"""Local failure attribution for the completed pilot. No provider or index operations."""
from collections import Counter
import json
import math
from pathlib import Path
import statistics
from run_readme_v5_pilot import ROOT, R4 as PILOT, read, sha, verify_execution, write_new


def save(path,value):
    if path.exists():
        if read(path)!=value:raise ValueError('Do not overwrite a frozen summary: '+str(path))
    else:write_new(path,value)


def main():
    used=verify_execution();assert used==18
    baseline=read(PILOT/'baseline-evaluation.json');primary=read(PILOT/'primary-evaluation.json');current=read(PILOT/'diagnostic-evaluation.json')
    requests=read(PILOT/'requests.json')['batches'];by_request={r['requestId']:r for r in requests}
    labels=read(PILOT/'frozen-labels.json');gold={c['block']['blockId']:c for c in labels['cases']}
    source_rows=read(PILOT/'sources.json')['documents'];blocks={b['blockId']:b for s in source_rows for b in s['blocks']}
    plans={d['source']['documentId']:d for d in read(ROOT/'_experiment_staging/readme-v5-admission-repair-v2/all-plans.json')['documents']}
    metadata={ident:{a['blockId']:a for a in read(PILOT/f'results/diagnostic/{ident}.json')['result']['recommendation']['assessments']}
              for ident in plans if (PILOT/f'results/diagnostic/{ident}.json').exists()}
    required={r['blockId']:r for r in current['violations']['missingConditions']}
    targeted={bid:r['requestId'] for r in requests for bid in r['targetIds']}
    cases=[]
    for row in current['cases']:
        if not row['expected']['recoveryEligible'] or row['usableBody']:continue
        bid=row['blockId'];guards=[]
        for ref in plans[row['documentId']]['appliedGuardEdges'][bid]:
            a=metadata[row['documentId']][ref]
            if a['disposition'] not in {'KNOWLEDGE_CANDIDATE','CONTEXT_ONLY'} and a['applicability']!='OUT_OF_SCOPE':
                guards.append({'blockId':ref,'line':blocks[ref]['startLine'],'text':blocks[ref]['text'],'reason':a['reason']})
        if bid in required:reason='MISSING_REQUIRED_COMPANION'
        elif any(g['reason']=='MIXED_LICENSE_SCOPE_REQUIRES_SPLIT' for g in guards):reason='REAL_LICENSE_GUARD_UNRESOLVED'
        elif row['assessment']['kind']=='SOURCE_POINTER':reason='FACTUAL_BODY_AS_NAVIGATION'
        elif row['assessment']['reason']=='INSTRUCTION_LIKE_SOURCE':reason='HARD_SOURCE_INSTRUCTION_PATTERN'
        elif row['proposal'] and row['proposal']['decision']=='REVIEW':reason='MODEL_REVIEW'
        else:reason='OTHER_UNRESOLVED'
        cases.append({'caseId':row['caseId'],'documentId':row['documentId'],'blockId':bid,'sourceRef':row['sourceRef'],
            'lineRange':row['lineRange'],'text':blocks[bid]['text'],'reason':reason,'actualTarget':bid in targeted,
            'requestId':targeted.get(bid),'inBody':row['inBody'],'proposal':row['proposal'],'assessment':row['assessment'],
            'unresolvedGuards':guards,'missingRequiredBlocks':[{'blockId':i,'line':blocks[i]['startLine'],'text':blocks[i]['text']}
                for i in required.get(bid,{}).get('missing',[])]})
    failures=[];outcomes=[]
    for path in sorted((PILOT/'attempts').glob('*/*/outcome.json')):
        outcome=read(path);outcomes.append(outcome)
        if outcome['status']=='MODEL_VALIDATED':continue
        request=by_request[outcome['requestId']];targets={b['blockId']:b for b in json.loads(request['user'])['targets']}
        reply=read(path.parent/'reply.json');selections=json.loads(reply['text'])['selections'];conflicts=[]
        for choice in selections:
            target=targets.get(choice['blockId']);expected=gold.get(choice['blockId'],{}).get('expected',{})
            if target and target['ruleDecision']=='KEEP' and choice['decision']=='DROP':
                conflicts.append({'blockId':choice['blockId'],'text':target['raw'],'ruleReason':target['ruleReason'],
                    'goldDecision':expected.get('decision'),'modelDecision':'DROP',
                    'finding':'RULE_PROTECTION_FALSE_POSITIVE' if expected.get('decision')=='DROP' else 'DROP_CONFLICT'})
        failures.append({'phase':outcome['phase'],'requestId':outcome['requestId'],'status':outcome['status'],'fixedKeepDropConflicts':conflicts})
    before={c['caseId'] for c in baseline['cases'] if c['usableBody']};after={c['caseId'] for c in current['cases'] if c['usableBody']}
    audit={'scope':'Same 101 assistant-labelled cases; regression diagnostic, not independent acceptance',
        'providerCalls':used,'maximumAttempts':18,'indexWrites':0,'missingUsefulCases':cases,'missingUsefulCounts':dict(Counter(c['reason'] for c in cases)),
        'newlyUsable':sorted(after-before),'previouslyUsableNowUnusable':sorted(before-after),
        'protocolFailures':failures,'primaryScoreSha256':sha(PILOT/'primary-evaluation.json'),
        'diagnosticScoreSha256':sha(PILOT/'diagnostic-evaluation.json'),'baselineSha256':sha(PILOT/'baseline-evaluation.json')}
    elapsed=sorted(o['elapsedMs'] for o in outcomes)
    audit['classificationAttemptLatencyMs']={'count':len(elapsed),'min':min(elapsed),'p50':statistics.median(elapsed),
        'p95NearestRank':elapsed[math.ceil(.95*len(elapsed))-1],'max':max(elapsed),
        'meaning':'Per-attempt acquisition/classification including planning and validation; not retrieval/answer latency.'}
    save(PILOT/'failure-audit.json',audit)
    print(json.dumps({'providerCalls':used,'newlyUsable':len(after-before),'regressed':len(before-after),
        'unrecoveredByReason':audit['missingUsefulCounts'],'classificationAttemptLatencyMs':audit['classificationAttemptLatencyMs']},ensure_ascii=False))


if __name__=='__main__':main()
