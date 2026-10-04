"""Offline customer evaluation. Semantic correctness requires an explicit human verdict."""
import argparse
import json
import math
from collections import defaultdict
from pathlib import Path

def evaluate(rows):
    rubric_path = Path(__file__).resolve().parents[1] / 'docs/customer/cases-v1.json'
    rubric = json.loads(rubric_path.read_text(encoding='utf-8')) if rubric_path.exists() else {'cases': []}
    acceptance_ids = {case['id'] for case in rubric['cases'] if case['split'] == 'acceptance'}
    groups = defaultdict(list)
    for row in rows:
        groups[row.get('variant', 'B')].append(row)
    report = {}
    for variant, batch in groups.items():
        latencies = sorted(ms for r in batch for ms in
            ([t.get('durationMs') for t in r['turns']] if r.get('turns') else [r.get('durationMs')])
            if isinstance(ms, (int, float)))
        tokens, unknown = 0, 0
        for row in batch:
            for call in row.get('metrics', {}).get('modelCalls', []):
                usage = call.get('usage', {})
                total = usage.get('total_tokens')
                if isinstance(total, (int, float)):
                    tokens += total
                else:
                    unknown += 1
        reviewed = [r for r in batch if isinstance(r.get('manualPassed'), bool)]
        case_ids = {r['caseId'] for r in batch if r.get('caseId')}
        report[variant] = {'runs': len(batch), 'independentCases': len(case_ids), 'reviewed': len(reviewed),
            'unreviewed': len(batch) - len(reviewed), 'passed': sum(r['manualPassed'] for r in reviewed),
            'criticalFailures': [r.get('caseId') for r in batch if r.get('criticalFailure')],
            'p95Ms': latencies[math.ceil(.95 * len(latencies)) - 1] if latencies else None,
            'reportedTotalTokens': tokens, 'callsWithUnknownUsage': unknown,
            'costUsd': None, 'costStatus': 'UNKNOWN_UNTIL_PROVIDER_PRICE_AND_USAGE_ARE_VERIFIED',
            'qualityGatePassed': len(batch) == 40 and len(acceptance_ids) == 20 and case_ids == acceptance_ids and len(reviewed) == len(batch)
                and all(r.get('split') == 'acceptance' for r in batch)
                and all({r.get('round') for r in batch if r.get('caseId') == case_id} == {1, 2} for case_id in case_ids)
                and sum(r['manualPassed'] for r in reviewed) / len(batch) >= .9
                and not any(r.get('criticalFailure') for r in batch),
            'releaseApproved': False,
            'releaseReason': 'Quality alone cannot approve release; real browser, engineering, server and rollback gates require separate evidence.'}
    return report

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('results', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.results.read_text(encoding='utf-8-sig'))
    report = evaluate(data)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()
