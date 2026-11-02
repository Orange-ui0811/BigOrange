"""Score the V1.6 pilot once per phase; preserve first-pass results and budget identity."""
import argparse
from collections import Counter
import json
import sys
from score_readme_quality_v3 import evaluate
from run_readme_v5_pilot import ROOT, R4 as PILOT, read, rows, sha, verify_execution, write_new


def score(phase):
    calls=verify_execution()
    data=read(PILOT/'frozen-labels.json');sources=read(PILOT/'sources.json')['documents']
    auth=read(PILOT/'authorization.json');results={}
    for source in sources:
        ident=source['source']['documentId'];entry=read(PILOT/f'results/{phase}/{ident}.json')
        if entry['datasetSha256']!=auth['datasetSha256'] or entry['sealSha256']!=auth['sealSha256']:
            raise ValueError('Result does not belong to the authorized source generation')
        results[ident]=entry['result']
    report=evaluate(data,sources,results)
    attempts=[read(p) for p in sorted((PILOT/'attempts').glob('*/*/outcome.json'))]
    primary=list((PILOT/'attempts/primary').glob('*/reservation.json'))
    if len(primary)!=16:
        raise ValueError('Cannot score an incomplete primary acquisition')
    usage=Counter()
    for path in (PILOT/'attempts').glob('*/*/provider-response.json'):
        usage.update({k:v for k,v in (read(path).get('usage') or {}).items() if isinstance(v,int) and k in {'prompt_tokens','completion_tokens','total_tokens'}})
    report.update({'phase':phase,'scope':'FIVE_REPOSITORY_REGRESSION_DIAGNOSTIC_NOT_FINAL_INDEPENDENT_ACCEPTANCE',
        'newPromptExecuted':True,'annotationAuthority':data['annotationAuthority'],'independentValidation':False,
        'budgetUsedAtScoring':calls,'budgetLimit':18,'providerReportedUsage':dict(usage),
        'attemptStatuses':{p:dict(Counter(a['status'] for a in attempts if a['phase']==p)) for p in ['primary','reserve']},
        'requestsSha256':sha(PILOT/'requests.json'),'datasetSha256':auth['datasetSha256'],'sealSha256':auth['sealSha256'],
        'indexWrites':0,'historicalResultsUnchanged':True})
    destination=PILOT/f'{phase}-evaluation.json'
    if destination.exists():
        if read(destination)!=report:raise ValueError('First-pass/phase results are immutable; use a new diagnostic report')
    else:write_new(destination,report)
    summary={k:report[k] for k in ['phase','budgetUsedAtScoring','budgetLimit','attemptStatuses']}
    summary['metrics']=report['metrics']
    print(json.dumps(summary,ensure_ascii=False))
    return report


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase',choices=['primary','diagnostic'],default='primary')
    score(parser.parse_args().phase)
