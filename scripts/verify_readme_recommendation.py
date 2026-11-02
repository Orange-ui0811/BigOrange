"""Check K3-R3 offline evidence, source metadata and preservation of historical budgets/results."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/readme-recommendation-v3'
OUT = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def input_paths():
    base = ROOT / 'orange-server/src/main/java/com/orangejd/server/ragb/ingest'
    tests = ROOT / 'orange-server/src/test/java/com/orangejd/server/ragb/ingest'
    return sorted(list(base.glob('Readme*.java')) + list(tests.glob('Readme*.java'))
                  + [tests / 'RagBTokenChunkerTest.java', ROOT / 'orange-server/src/test/resources/readme-filter/dependency-v2/sources.json'])


def prepare():
    STAGE.mkdir(parents=True, exist_ok=True)
    baseline = STAGE / 'before.json'
    if not baseline.exists():
        files = [p for p in (OUT.parent / 'quality-v1').rglob('*') if p.is_file()]
        files += [ROOT / 'docs/customer/workflow-v2/provider-calls.jsonl', ROOT / 'docs/customer/context-v3/live-provider-calls.jsonl',
                  OUT / 'dependency-verification.json']
        baseline.write_text(json.dumps({'files': {p.relative_to(ROOT).as_posix(): sha(p) for p in files}}, indent=2), encoding='utf-8')
    (STAGE / 'test-inputs.json').write_text(json.dumps({p.relative_to(ROOT).as_posix(): sha(p) for p in input_paths()}, indent=2), encoding='utf-8')
    print('R3 offline inputs recorded; no provider calls')


def main():
    for name, expected in read(STAGE / 'before.json')['files'].items():
        assert sha(ROOT / name) == expected, f'Historical evidence changed: {name}'
    for name, expected in read(STAGE / 'test-inputs.json').items():
        assert sha(ROOT / name) == expected, f'Test input changed: {name}; rerun checks'
    classes = ['ReadmeRecommendationProjectionTest', 'ReadmeDependencyClosureTest', 'ReadmeKnowledgeFilterTest',
               'ReadmeModelReviewTest', 'ReadmeSpringAiReviewTransportTest', 'RagBTokenChunkerTest']
    suites, report_times = [], []
    for name in classes:
        path = ROOT / f'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.{name}.xml'
        suite = ET.parse(path).getroot()
        assert all(int(suite.attrib[k]) == 0 for k in ['failures', 'errors', 'skipped']), name
        report_times.append(path.stat().st_mtime_ns)
        suites.append({'class': name, 'tests': int(suite.attrib['tests']),
                       'cases': [case.attrib['name'] for case in suite.findall('testcase')]})
    assert suites[0]['tests'] >= 16 and suites[1]['tests'] == 8
    assert max(p.stat().st_mtime_ns for p in input_paths()) <= min(report_times), 'Reports precede current inputs'
    log = STAGE / 'acceptance-tests.log'
    assert 'BUILD SUCCESS' in log.read_text(encoding='utf-8')
    corpus = read(STAGE / 'corpus-check.json')
    dependency = read(STAGE / 'dependency/corpus-check.json')
    assert corpus['documents'] == dependency['documents'] == 303
    for key in ['sourceMappingErrors', 'dispositionLeaks', 'additionalProviderCalls', 'indexWrites']:
        assert corpus[key] == 0, key
    assert dependency['missingDependencies'] == dependency['idempotenceErrors'] == 0
    for example in read(STAGE / 'examples.json'):
        result = example['result']; preview = result['preview']; projection = result['projection']
        metadata = {a['blockId']: a for a in projection['assessments']}
        assert len(metadata) == len(preview['blocks'])
        for candidate in projection['knowledgeCandidates']:
            assert {a['blockId'] for a in candidate['evidence']} == set(candidate['packet']['primaryBlockIds'] + candidate['packet']['contextBlockIds'])
            for a in candidate['evidence']:
                assert a == metadata[a['blockId']]
                assert a['disposition'] in ['KNOWLEDGE_CANDIDATE', 'CONTEXT_ONLY']
                if a['disposition'] == 'KNOWLEDGE_CANDIDATE': assert a['applicability'] == 'IN_SCOPE' and a['scopes']
        assert all(not c['packet']['contextBlockIds'] for c in projection['pointerCandidates'])
    # Check the documented schema independently of Java's protocol tests.
    from jsonschema import Draft202012Validator
    schema_path = OUT / 'selection-v2.schema.json'
    schema = read(schema_path); Draft202012Validator.check_schema(schema); validator = Draft202012Validator(schema)
    base = {'blockId': 'source:block', 'decision': 'KEEP', 'kind': 'USAGE', 'scope': 'ENTERPRISE', 'reason': 'KNOWLEDGE',
            'applicability': 'IN_SCOPE', 'evidenceBlockIds': ['source:block']}
    valid = [base, {**base, 'applicability': 'OUT_OF_SCOPE', 'scope': 'UNKNOWN'},
             {**base, 'kind': 'SOURCE_POINTER', 'reason': 'POINTER', 'scope': 'UNKNOWN', 'applicability': 'UNKNOWN'}]
    invalid = [{**base, 'applicability': 'UNKNOWN'}, {**base, 'scope': 'UNKNOWN'},
               {**base, 'evidenceBlockIds': ['source:block', 'source:block']}, {**base, 'rewrittenText': 'invented'}]
    for row in valid: validator.validate({'schemaVersion': 'README_SELECTIONS_V2', 'selections': [row]})
    for row in invalid: assert not validator.is_valid({'schemaVersion': 'README_SELECTIONS_V2', 'selections': [row]})
    ledger = OUT.parent / 'quality-v1/provider-calls.jsonl'
    used = sum(bool(line.strip()) for line in ledger.read_text(encoding='utf-8').splitlines()); assert used == 18
    evidence_paths = input_paths() + [Path(__file__).resolve(), ROOT / 'scripts/run_readme_recommendation_checks.ps1', schema_path,
                                    log, STAGE / 'corpus-check.json', STAGE / 'examples.json', STAGE / 'test-inputs.json']
    receipt = {'status': 'K3_R3_IMPLEMENTED_OFFLINE_VERIFIED', 'tests': sum(s['tests'] for s in suites), 'suites': suites,
               'corpus': corpus, 'dependencyCorpus': dependency, 'schemaExamples': {'valid': len(valid), 'invalid': len(invalid)},
               'additionalProviderCalls': 0, 'previousBudget': {'authorized': 20, 'used': used, 'remaining': 20-used},
               'qualityGateV2': 'NOT_RUN', 'indexWrites': 0,
               'implementationAndEvidenceHashes': {p.relative_to(ROOT).as_posix(): sha(p) for p in evidence_paths},
               'limitations': ['Rule-only corpus preview and simulated model responses are implementation checks, not classification accuracy.',
                              'Semantic scope/applicability still requires frozen R4 labels and uniform-version R5 evaluation.',
                              'K4 database/index integration and retrieval quality remain pending.']}
    (OUT / 'recommendation-verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k: receipt[k] for k in ['status', 'tests', 'additionalProviderCalls', 'qualityGateV2', 'indexWrites']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--prepare', action='store_true')
    if parser.parse_args().prepare: prepare()
    else: main()
