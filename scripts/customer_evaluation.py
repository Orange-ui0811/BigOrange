"""Offline customer evaluation. Semantic correctness requires an explicit human verdict."""
import argparse
import json
import math
from collections import defaultdict
from pathlib import Path

def assess_behavior(row):
    """Deterministic behavior checks do not score semantic truth or replace manual review."""
    expected = row.get('expectations')
    if not isinstance(expected, dict) or not expected:
        return {'status': 'UNASSESSED', 'reason': 'NO_STRUCTURED_EXPECTATIONS'}
    result = row.get('result') or (row.get('turns') or [{}])[-1].get('result', {})
    failures = []
    supported = {'status', 'businessOutcome', 'state', 'excludedProjectIds', 'minimumProjects'}
    if set(expected) - supported:
        return {'status': 'UNASSESSED', 'reason': 'UNSUPPORTED_EXPECTATIONS'}
    if not any(key in expected for key in ('status', 'businessOutcome', 'minimumProjects')) and not expected.get('state') and not expected.get('excludedProjectIds'):
        return {'status': 'UNASSESSED', 'reason': 'EMPTY_EXPECTATIONS'}
    for key in ('status', 'businessOutcome'):
        if key in expected and result.get(key) != expected[key]:
            failures.append(key)
    for key, value in expected.get('state', {}).items():
        if result.get('state', {}).get(key) != value:
            failures.append('state.' + key)
    projects = result.get('projects', [])
    if any(str(p.get('id')) in {str(i) for i in expected.get('excludedProjectIds', [])} for p in projects):
        failures.append('excludedProjectIds')
    if len(projects) < expected.get('minimumProjects', 0):
        failures.append('minimumProjects')
    return {'status': 'FAILED' if failures else 'PASSED', 'failures': failures}

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
        behavior = [assess_behavior(r) for r in batch]
        outcomes = defaultdict(int)
        phases = defaultdict(lambda: {'attempts': 0, 'failedAttempts': 0, 'durationMs': 0, 'reportedTokens': 0, 'unknownUsage': 0})
        for row in batch:
            for turn in row.get('turns', [row]):
                outcomes[turn.get('result', {}).get('businessOutcome', 'UNASSESSED')] += 1
            for call in row.get('metrics', {}).get('modelCalls', []):
                phase = phases[call.get('phase', 'UNKNOWN')]
                phase['attempts'] += 1
                phase['failedAttempts'] += call.get('status') == 'FAILED'
                phase['durationMs'] += call.get('durationMs', 0) or 0
                total = call.get('usage', {}).get('total_tokens')
                if isinstance(total, (int, float)):
                    phase['reportedTokens'] += total
                else:
                    phase['unknownUsage'] += 1
        case_ids = {r['caseId'] for r in batch if r.get('caseId')}
        report[variant] = {'runs': len(batch), 'independentCases': len(case_ids), 'reviewed': len(reviewed),
            'unreviewed': len(batch) - len(reviewed), 'passed': sum(r['manualPassed'] for r in reviewed),
            'criticalFailures': [r.get('caseId') for r in batch if r.get('criticalFailure')],
            'p95Ms': latencies[math.ceil(.95 * len(latencies)) - 1] if latencies else None,
            'reportedTotalTokens': tokens, 'callsWithUnknownUsage': unknown,
            'businessOutcomes': dict(outcomes), 'phases': dict(phases),
            'behaviorChecks': {status: sum(r['status'] == status for r in behavior) for status in ('PASSED', 'FAILED', 'UNASSESSED')},
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
