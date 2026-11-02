"""Audit actual R5 wire evidence, immutable primary outputs, budget and offline merged selections."""
from collections import Counter
import argparse
import json
from pathlib import Path
import sys
from run_readme_quality_v2 import verify_execution, write_new, rows, R4, R5, ROOT
from score_readme_quality_v2 import read, sha


def seal_primary():
    used = verify_execution()
    if used != 34 or len(list((R5 / 'results/primary').glob('*.json'))) != 12:
        raise ValueError('Complete primary results required before reserve calls')
    paths = list((R5 / 'attempts/primary').rglob('*.json')) + list((R5 / 'results/primary').glob('*.json'))
    paths += [R5 / 'primary-evaluation.json', R5 / 'primary-evaluation.md']
    ledger = R5 / 'provider-calls.jsonl'
    seal = {'purpose': 'Immutable first-pass results before any reserve retry', 'outboundAtSeal': used,
            'ledgerPrefixBytes': ledger.stat().st_size, 'ledgerPrefixSha256': sha(ledger),
            'files': {p.relative_to(ROOT).as_posix(): sha(p) for p in paths}}
    write_new(R5 / 'primary-seal.json', seal)
    print(json.dumps({'status': 'PRIMARY_SEALED', 'files': len(paths), 'outbound': used}))


def verify():
    used = verify_execution()
    primary = read(R5 / 'primary-seal.json')
    for name, digest in primary['files'].items():
        assert sha(ROOT / name) == digest, f'Primary result changed: {name}'
    import hashlib
    prefix = (R5 / 'provider-calls.jsonl').read_bytes()[:primary['ledgerPrefixBytes']]
    assert hashlib.sha256(prefix).hexdigest() == primary['ledgerPrefixSha256']
    calls = rows(R5 / 'provider-calls.jsonl')
    assert [c['attempt'] for c in calls] == list(range(1, used + 1))
    assert all(c['kind'] == 'CUSTOMER_CHAT' for c in calls)
    requests = read(R4 / 'requests.json')['batches']
    by_id = {r['requestId']: r for r in requests}
    configs = read(R4 / 'provider-config.json')
    attempts, request_ids = [], {}
    for phase in ['primary', 'reserve']:
        request_ids[phase] = []
        for path in sorted((R5 / 'attempts' / phase).glob('*/outcome.json')):
            outcome = read(path)
            r = by_id[outcome['requestId']]
            request_ids[phase].append(r['requestId'])
            assert outcome['cacheKey'] == r['cacheKey'] and outcome['phase'] == phase
            wire = read(path.parent / 'wire-request.json')
            assert wire == {'model': configs['model'], 'temperature': 0.0, 'max_tokens': 2048,
                            'messages': [{'role': 'system', 'content': r['system']}, {'role': 'user', 'content': r['user']}],
                            'thinking': {'type': 'disabled'}, 'response_format': {'type': 'json_object'}}
            if outcome['status'] == 'MODEL_VALIDATED':
                reply = read(path.parent / 'reply.json')
                assert reply['finishReason'] == 'stop'
                assert outcome['selections'] == json.loads(reply['text'])['selections']
            else:
                assert not outcome['selections']
            attempts.append(outcome)
    assert set(request_ids['primary']) == set(by_id) and len(request_ids['primary']) == 34
    eligible = [r['requestId'] for r in requests
                if any(a['phase'] == 'primary' and a['requestId'] == r['requestId']
                       and (a['status'] in {'TIMEOUT', 'PROVIDER_OR_STORAGE_FAILURE'} or a['status'].startswith('OUTPUT_REJECTED_')) for a in attempts)][:6]
    assert set(request_ids['reserve']) <= set(eligible) and len(request_ids['reserve']) <= 6
    wire_count = len(list((R5 / 'attempts').glob('*/*/wire-request.json')))
    assert used == wire_count == len(attempts), 'Every physical attempt must have one wire record and one outcome'
    journal = rows(R5 / 'selection-attempts.jsonl')
    starts = [r for r in journal if r['event'] == 'START']
    assert len(starts) == used and all(r['simulated'] is False for r in journal)
    assert Counter(r['cacheKey'] for r in starts) == Counter(a['cacheKey'] for a in attempts)
    assert all(r['runId'] == 'K3_R5_FROZEN_V2_20261030' and r['model'] == configs['modelIdentity'] and r['inputUpperBound'] <= 12000 and r['outputLimit'] == 2048 for r in starts)
    for phase in ['primary', 'diagnostic']:
        files = list((R5 / f'results/{phase}').glob('*.json'))
        if not files:
            continue
        assert len(files) == 12
        for path in files:
            entry = read(path)
            result = entry['result']
            assert entry['replayNetworkCalls'] == 0
            assert result['recommendation']['versions']['modelIdentity'] == configs['modelIdentity']
            reqs = [r for r in requests if r['documentId'] == path.stem]
            assert [r['cacheKey'] for r in reqs] == [b['cacheKey'] for b in result['batches']]
            expected = []
            for r in reqs:
                primary_outcome = next(a for a in attempts if a['phase'] == 'primary' and a['requestId'] == r['requestId'])
                chosen = primary_outcome
                if phase == 'diagnostic':
                    retry = next((a for a in attempts if a['phase'] == 'reserve' and a['requestId'] == r['requestId']), None)
                    if retry and retry['status'] == 'MODEL_VALIDATED':
                        chosen = retry
                expected.extend(chosen['selections'])
            assert result['selections'] == expected, f'Lost/changed batch selections for {phase}/{path.stem}'
    report = {'status': 'R5_EXECUTION_VERIFIED', 'maximumAttempts': 40, 'outbound': used, 'primaryRequests': 34,
              'reserveRequests': len(request_ids['reserve']), 'eligibleReserveIds': eligible,
              'physicalAttemptStatuses': {p: dict(Counter(a['status'] for a in attempts if a['phase'] == p)) for p in ['primary', 'reserve']},
              'wireRequestsExactlyMatchR4': True, 'oneLedgerEntryPerPhysicalAttempt': True,
              'primaryResultsUnchanged': True, 'allRepositoryBatchSelectionsMerged': True,
              'r4AndHistoricalHashesUnchanged': True, 'indexWrites': 0,
              'sourceCodeAndLabelsChanged': False, 'annotationAuthority': 'ASSISTANT_SOURCE_REVIEWED_NOT_INDEPENDENT_HUMAN_GOLD',
              'primaryQualityGatePassed': read(R5 / 'primary-evaluation.json')['metrics']['qualityGatePassed']}
    path = R5 / 'verification.json'
    if path.exists():
        assert read(path) == report, 'Do not overwrite a different execution verification'
    else:
        write_new(path, report)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seal-primary', action='store_true')
    args = parser.parse_args()
    seal_primary() if args.seal_primary else verify()
