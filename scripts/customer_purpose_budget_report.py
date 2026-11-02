"""Summarize frozen provider reports without exposing prompts, profiles or credentials."""
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def summarize(rows):
    result = {}
    for engine in ('LEGACY', 'LANGGRAPH4J'):
        selected = [r for r in rows if r['engine'] == engine]
        if not selected:
            continue
        calls = [c for r in selected for c in r['usage'].get('modelCalls', [])]
        purpose = [c for c in calls if c.get('phase') == 'PURPOSE_REVIEW']
        durations = sorted(r['durationMs'] for r in selected)
        result[engine] = {
            'runs': len(selected), 'passes': sum(r['passed'] for r in selected),
            'p95Ms': durations[math.ceil(len(durations) * .95) - 1],
            'maxMs': max(durations), 'modelAttempts': len(calls),
            'failedAttempts': sum(c['status'] != 'SUCCEEDED' for c in calls),
            'retryAttempts': sum(c.get('attempt', 1) > 1 for c in calls),
            'reportedTotalTokens': sum(c.get('usage', {}).get('total_tokens', 0) for c in calls),
            'attemptsWithoutUsage': sum(not bool(c.get('usage')) for c in calls),
            'runsWithRetrievalFailures': sum(r.get('retrievalFailures', 0) > 0 for r in selected),
            'costUsd': 'UNKNOWN',
            'purpose': {'attempts': len(purpose),
                'failedAttempts': sum(c['status'] != 'SUCCEEDED' for c in purpose),
                'retryAttempts': sum(c.get('attempt', 1) > 1 for c in purpose),
                'durationMs': sum(c['durationMs'] for c in purpose),
                'reportedTotalTokens': sum(c.get('usage', {}).get('total_tokens', 0) for c in purpose),
                'attemptsWithoutUsage': sum(not bool(c.get('usage')) for c in purpose)},
            'cases': [{k: r.get(k) for k in ('caseId', 'round', 'passed', 'runStatus', 'status',
                'durationMs', 'projectCount', 'referenceCount', 'retrievalFailures')} for r in selected],
            'purposeAttempts': [{k: c.get(k) for k in ('status', 'attempt', 'durationMs', 'usage',
                'thinking', 'outputBudget', 'timeoutSeconds', 'finishReason', 'errorCode',
                'retryDecision', 'remainingSeconds', 'purposeOutputErrors')} for c in purpose],
        }
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report-root', required=True)
    options = parser.parse_args()
    base = (ROOT / options.report_root).resolve()
    if not base.is_relative_to(ROOT / 'docs/customer/langgraph4j'):
        raise ValueError('Invalid report directory')
    output = {'releaseApproved': False,
        'baseline': summarize(json.loads((ROOT / 'docs/customer/langgraph4j/local-m3-20261008/smoke/provider-acceptance.json').read_text(encoding='utf-8'))['rows']),
        'limitations': ['Automatic behavior checks are not manual claim-support acceptance.',
            'One smoke run per scenario is not a stability or causal estimate.',
            'Provider generation is stochastic; no framework quality conclusion from different answers.',
            'Unknown language/duration must remain unverified and non-importable.',
            'No current-user profile/history was sent; public engine switching is a separate release gate.',
            'Unavailable usage is not zero cost; prices and local hardware costs are not measured.']}
    for phase in ('smoke', 'full'):
        file = base / phase / 'provider-acceptance.json'
        if file.exists():
            output[phase] = summarize(json.loads(file.read_text(encoding='utf-8'))['rows'])
            execution = base / phase / 'execution.json'
            output[phase + 'ExitCode'] = json.loads(execution.read_text(encoding='utf-8'))['exitCode'] if execution.exists() else None
    audit = base / 'full/semantic-audit.json'
    if audit.exists():
        output['semanticAudit'] = json.loads(audit.read_text(encoding='utf-8'))
        output['automaticPassIsNotFullSemanticPass'] = True
    safety = base / 'full/project-safety.json'
    if safety.exists():
        checked = json.loads(safety.read_text(encoding='utf-8'))
        output['projectSafety'] = {'runsReviewed': len(checked),
            'unsafeImports': sum(r['unsafeImports'] for r in checked),
            'partialRuns': sum(r['purposeReviewIncomplete'] for r in checked),
            'partialNoticesVisible': sum(r['partialNoticeVisible'] for r in checked)}
    (base / 'summary.json').write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: {e: {key: v[key] for key in ('runs', 'passes', 'p95Ms', 'failedAttempts', 'purpose')} for e, v in output[k].items()} for k in ('smoke', 'full') if k in output}, ensure_ascii=False))

if __name__ == '__main__':
    main()
