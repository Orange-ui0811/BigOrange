"""Summarize frozen synthetic scenarios and actual turns separately; no credential access."""
import argparse, collections, json, math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def summarize(folder):
    scenarios = json.loads((folder / 'provider-acceptance.json').read_text(encoding='utf-8'))['rows']
    results = {}
    for engine in ('LEGACY', 'LANGGRAPH4J'):
        own = [r for r in scenarios if r['engine'] == engine]
        turns = [t for r in own for t in r.get('turns', [r])]
        durations = sorted(t['durationMs'] for t in turns)
        calls = [c for t in turns for c in t['usage'].get('modelCalls', [])]
        results[engine] = {
            'scenarios': len(own), 'passedScenarios': sum(r['passed'] for r in own),
            'actualRequests': len(turns), 'terminalStatuses': dict(collections.Counter(t['runStatus'] for t in turns)),
            'p95RequestMs': durations[math.ceil(.95 * len(durations)) - 1], 'maxRequestMs': max(durations),
            'modelAttempts': len(calls), 'failedAttempts': sum(c['status'] == 'FAILED' for c in calls),
            'retryAttempts': sum(c.get('attempt', 1) > 1 for c in calls),
            'reportedTotalTokens': sum(t['usage'].get('tokenUsage', {}).get('totalTokens', 0) for t in turns),
            'callsWithoutUsage': sum(t['usage'].get('tokenUsage', {}).get('callsWithoutUsage', 0) for t in turns),
            'retrievalFailures': sum(t.get('retrievalFailures', 0) for t in turns),
            'criticalErrors': sum(t.get('criticalErrors', 0) for t in turns),
            'costUsd': 'UNKNOWN',
            'failures': [{k: r.get(k) for k in ('caseId', 'round', 'intent', 'status', 'state', 'error')} for r in own if not r['passed']],
            'multiTurn': [{k: r.get(k) for k in ('caseId', 'round', 'passed', 'projectCount', 'referenceCount', 'criticalErrors')} for r in own if 'turns' in r],
        }
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report-root', required=True)
    args = parser.parse_args()
    folder = (ROOT / args.report_root).resolve()
    if not folder.is_relative_to(ROOT / 'docs/customer/langgraph4j'):
        raise ValueError('Report must remain in LangGraph4j documentation')
    report = {'releaseApproved': False, 'smoke': summarize(folder / 'smoke')}
    if (folder / 'full/provider-acceptance.json').exists():
        report['full'] = summarize(folder / 'full')
    if (folder / 'repair/provider-acceptance.json').exists():
        report['repair'] = summarize(folder / 'repair')
    if (folder / 'goal-provenance-validation/repair/provider-acceptance.json').exists():
        report['goalProvenance'] = summarize(folder / 'goal-provenance-validation/repair')
    if (folder / 'admin-live-ui.json').exists():
        report['adminLiveUI'] = json.loads((folder / 'admin-live-ui.json').read_text(encoding='utf-8'))
    report['remainingGates'] = [
        'Final-code complete independent model acceptance',
        'Deployment/rollback at confirmed target',
        '24-hour post-release observation',
    ]
    report['limitations'] = [
        'Automatic criteria and identity/source checks are not complete manual semantic acceptance.',
        '20 independent scenarios repeated twice are not 40 independent scenarios.',
        'Request timings include up to 150 ms polling resolution; multi-turn scenarios are flattened.',
        'Provider calls are stochastic; quality/cost differences are not framework causal estimates.',
        'Unknown usage/pricing and local hardware cost are not zero.',
        'Authenticated live UI, deployment/rollback and 24-hour server observation are separate gates.',
    ]
    (folder / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=True))


if __name__ == '__main__':
    main()
