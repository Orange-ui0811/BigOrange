"""Verify the R4 source/label/request freeze, without provider, database or index access."""
from collections import Counter
import argparse
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/r4'
STAGE = ROOT / '_experiment_staging/readme-quality-v2'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def digest(value):
    return hashlib.sha256(value).hexdigest()


def main(seal=False):
    sealpath = OUT / 'r4-seal.json'
    if sealpath.exists():
        for name, expected in read(sealpath)['files'].items():
            assert digest((ROOT / name).read_bytes()) == expected, f'R4 sealed evidence changed: {name}'
    freeze = read(OUT / 'freeze-manifest.json')
    for name, expected in {**freeze['files'], **freeze['historicalFiles']}.items():
        assert digest((ROOT / name).read_bytes()) == expected, f'Frozen/historical file changed: {name}'
    data = read(OUT / 'frozen-dataset.json')
    sources = read(OUT / 'sources.json')['documents']
    requests = read(OUT / 'requests.json')['batches']
    runtime = read(OUT / 'provider-config.json')
    proposal = read(OUT / 'execution-proposal.json')
    allplans = read(STAGE / 'all-plans.json')
    assert len(sources) == len(data['documents']) == 12
    old = {str(x['documentId']) for x in read(ROOT / 'docs/rag-b/readme-filter-v1/sample-manifest.json')['samples']}
    prior = read(ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v1/frozen-dataset.json')
    old |= {str(x['documentId']) for x in prior['documents'] + prior['controls']}
    assert not old & {s['source']['documentId'] for s in sources}
    snapshot = read(ROOT / '_experiment_staging/readme-filter-v1-20261029/snapshot.json')
    original = {str(x['documentId']): x for x in snapshot['documents']}
    blocks, bydoc = {}, {}
    for s in sources:
        doc = s['source']; ident = doc['documentId']; raw = doc['text']; prior = original[ident]
        assert doc['sourceRef'] == prior['sourceRef'] and doc['versionId'] == str(prior['versionId'])
        assert raw == prior['content'] and digest(raw.encode()) == s['sha256'] == prior['sha256']
        assert ''.join(b['text'] for b in s['blocks']) == raw
        utf16 = raw.encode('utf-16-le'); cursor = 0
        for b in s['blocks']:
            assert b['blockId'] not in blocks
            assert b['startUtf16'] == cursor
            assert utf16[b['startUtf16'] * 2:b['endUtf16'] * 2].decode('utf-16-le') == b['text']
            assert digest(b['text'].encode()) == b['sha256']
            cursor = b['endUtf16']; blocks[b['blockId']] = b
        assert cursor * 2 == len(utf16)
        bydoc[ident] = s
    cases = {c['block']['blockId']: c for c in data['cases']}; assert len(cases) == len(data['cases']) == 147
    planned = {r['cacheKey']: r for d in allplans['documents'] for r in d['batches']}
    assert len(requests) == len(planned) == len({r['cacheKey'] for r in requests}) == 34
    target_pairs = set()
    versions = data['versions']
    for r in requests:
        p = planned[r['cacheKey']]
        assert all(r[k] == p[k] for k in p), 'Frozen request differs from Java plan'
        assert r['sourceSha256'] == bydoc[r['documentId']]['sha256']
        assert r['versionId'] == bydoc[r['documentId']]['source']['versionId']
        assert len(r['targetIds']) <= 4 and r['outputTokens'] == 2048
        assert len(set(r['targetIds'] + r['contextIds'])) == len(r['targetIds'] + r['contextIds'])
        assert r['inputUpperBound'] == len(r['system'].encode()) + len(r['user'].encode()) + 256 <= 12000
        assert digest(r['system'].encode()) == r['systemSha256'] and digest(r['user'].encode()) == r['userSha256']
        user = json.loads(r['user'])
        assert [b['blockId'] for b in user['targets']] == r['targetIds']
        assert [b['blockId'] for b in user['context']] == r['contextIds']
        for b in user['targets'] + user['context']:
            assert b['raw'] == blocks[b['blockId']]['text']
        for bid in r['targetIds']:
            pair = (r['documentId'], bid); assert pair not in target_pairs; target_pairs.add(pair)
            assert cases[bid]['role'] == 'MODEL_TARGET'
        key = '\n'.join([versions['selectionPolicy'], versions['prompt'], versions['schema'], versions['projectionPolicy'],
                         versions['rulePolicy'], versions['parser'], runtime['modelIdentity'], '2048', r['system'], r['user']])
        assert digest(key.encode()) == r['cacheKey'], 'Model identity or cache key changed'
    assert {bid for _, bid in target_pairs} == {bid for bid, c in cases.items() if c['role'] == 'MODEL_TARGET'}
    for bid, c in cases.items():
        assert c['block'] == blocks[bid]
        e = c['expected']
        for dep in e['requiredBlockIds']:
            assert dep in cases and cases[dep]['documentId'] == c['documentId']
            if e['recoveryEligible']:
                assert cases[dep]['expected']['recoveryEligible'], 'Expected usable body has an unresolved gold dependency'
        assert e['headingBlockIds'] == blocks[bid]['headingIds']
    assert not proposal['authorized'] and proposal['primaryRequests'] == 34
    assert proposal['primaryRequests'] + proposal['reserveRequests'] == proposal['maximumProposedAttempts'] == 40
    assert proposal['primaryReservedTokenUpperBound'] == sum(r['inputUpperBound'] + r['outputTokens'] for r in requests)
    assert not runtime['credentialsStored'] and not runtime['thinkingEnabled'] and not runtime['toolsEnabled']
    assert not any(k.lower() in {'key', 'api_key', 'api-key', 'secret', 'password', 'token'} for k in runtime)
    assert not (ROOT / proposal['ledgerPath']).exists(), 'R5 has already started; use execution-aware verification'
    assert not (STAGE / 'unused-ledger.jsonl').exists()
    suitepath = ROOT / 'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.K3R4FrozenPlanTest.xml'
    suite = ET.parse(suitepath).getroot()
    assert int(suite.attrib['tests']) == 1
    assert all(int(suite.attrib[n]) == 0 for n in ['errors', 'failures', 'skipped'])
    assert 'BUILD SUCCESS' in (STAGE / 'plan-tests.log').read_text(encoding='utf-8')
    producer = ROOT / 'orange-server/src/test/java/com/orangejd/server/ragb/ingest/K3R4FrozenPlanTest.java'
    assert producer.stat().st_mtime_ns < suitepath.stat().st_mtime_ns
    assert (STAGE / 'all-plans.json').stat().st_mtime_ns <= (OUT / 'freeze-manifest.json').stat().st_mtime_ns
    categories = dict(Counter(c['expected']['category'] for c in cases.values()))
    report = {'status': 'K3_R4_FROZEN_OFFLINE_VERIFIED', 'repositories': len(sources), 'oldRepositoryOverlap': 0,
              'modelTargets': len(target_pairs), 'controls': len(cases) - len(target_pairs), 'cases': len(cases),
              'criticalCases': sum(c['expected']['critical'] for c in cases.values()), 'categories': categories,
              'manualDependencyCases': sum(bool(c['expected']['requiredBlockIds']) for c in cases.values()),
              'primaryRequests': len(requests), 'reserveRequestsProposed': 6, 'maximumAttemptsProposed': 40,
              'primaryReservedTokenUpperBound': proposal['primaryReservedTokenUpperBound'],
              'checks': ['source/version/hash/UTF16 original mapping', 'no overlap with 46 prior repositories',
                         'complete annotations for every target', 'all executable batches included, no outcome-based selection',
                         'manual gold dependency consistency', 'byte-identical Java requests and recomputed cache keys',
                         'protected historical files and ledgers unchanged', 'no credentials in frozen configuration',
                         'one Docker network-disabled Java preparation test passed'],
              'additionalProviderCalls': 0, 'indexWrites': 0, 'qualityGate': 'NOT_RUN', 'r5Authorized': False,
              'datasetSha256': digest((OUT / 'frozen-dataset.json').read_bytes()),
              'manifestSha256': digest((OUT / 'freeze-manifest.json').read_bytes()),
              'verifierSha256': digest(Path(__file__).read_bytes()),
              'evidenceHashes': {p.relative_to(ROOT).as_posix(): digest(p.read_bytes()) for p in
                                 [suitepath, STAGE / 'plan-tests.log', STAGE / 'all-plans.json']},
              'limitations': ['Assistant-labeled, purposively selected, source-reviewed; not independent human gold or a blind/random test.',
                             'No provider quality scores, embedding/retrieval metrics or production integration claims.',
                             'Initial annotation draft had one dependency contradiction; archived and corrected before any provider call.']}
    (OUT / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if seal and not sealpath.exists():
        paths = [p for p in OUT.iterdir() if p.is_file() and p.name not in {'verification.json', 'r4-seal.json'}]
        paths += [Path(__file__).resolve(), suitepath, STAGE / 'plan-tests.log', STAGE / 'all-plans.json']
        sealpath.write_text(json.dumps({'status': 'R4_FINAL_SOURCE_LABELS_CRITERIA_AND_REQUESTS_SEALED',
                                       'files': {p.relative_to(ROOT).as_posix(): digest(p.read_bytes()) for p in paths},
                                       'providerCalls': 0, 'r5Authorized': False}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: report[k] for k in ['status', 'repositories', 'cases', 'modelTargets', 'controls', 'primaryRequests', 'additionalProviderCalls']}))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seal', action='store_true', help='Also seal final scoring criteria and preparation evidence after verification')
    main(parser.parse_args().seal)
