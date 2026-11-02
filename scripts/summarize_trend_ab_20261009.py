"""Aggregate observed A/B measurements; unavailable usage is never counted as zero."""
import json,statistics,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-ab-20261009'

def main():
    rows=[]; review=[]
    for f in sorted(OUT.glob('*-[12]-[AB].json')):
        raw=json.loads(f.read_text(encoding='utf-8'));res=raw.get('response',{});result=res.get('result',{})
        metrics=result.get('modelMetrics',{});usage=metrics.get('usage',{});ret=res.get('retrieval',{});trace=ret.get('trace',{})
        cards=result.get('recommendations',[])
        stages={}
        for s in res.get('labStages',[]):stages[s['stage']]=stages.get(s['stage'],0)+s['durationMs']
        urls=[r.get('url') for c in cards for r in c.get('references',[]) if r.get('url')]
        ids=[c.get('candidateId') for c in cards]
        review.append({'file':f.name,'profile':raw['profile'],'retrievalStatus':trace.get('status'),
                       'evidence':ret.get('text',''),
                       'cards':cards,'analysis':result.get('analysis'),'learning':result.get('learning'),
                       'businessCandidates':{'beginner':result.get('beginnerCases',[]),'career':result.get('careerCases',[])}})
        row={'case':raw['case'],'round':raw['round'],'side':raw['variant'],'file':f.name,
             'success':bool(metrics) and not raw.get('error') and not result.get('error'),
             'error':raw.get('error') or result.get('error'),'wallMs':raw['clientWallMs'],
             'retrievalMs':trace.get('durationMs'),'modelMs':metrics.get('durationMs'),
             'requestedModel':metrics.get('requestedModel'),'actualModel':metrics.get('model'),
             'providerHost':metrics.get('providerHost'),'usage':usage or None,
             'recommendations':len(cards),'candidateIds':[c.get('candidateId') for c in cards],
             'duplicateCandidateIds':sorted({x for x in ids if ids.count(x)>1}),
             'ragSelectedCards':sum(str(x).startswith('E') for x in ids),
             'businessSelectedCards':sum(str(x).startswith(('B:','C:')) for x in ids),
             'boundCards':sum(bool(c.get('references')) for c in cards),
             'reportFieldsComplete':all(result.get('analysis',{}).get(k) for k in ['trend','prospect','demand','summary']) and all(result.get('learning',{}).get(k) for k in ['steps','timeline','tips']),
             'repositories':sorted(set(urls)),'evidenceCount':len(trace.get('evidence',[])),
             'retrievedRepositories':sorted({e.get('repositoryUrl') for e in trace.get('evidence',[]) if e.get('repositoryUrl')}),
             'evidenceCharacters':len(ret.get('text','')),'retrievalStatus':trace.get('status'),
             'stagesMs':stages,'costCny':None,'costStatus':'UNKNOWN_NO_CONFIRMED_CURRENT_GITEE_RATE_OR_BILL',
             'failedStages':[s['stage'] for s in res.get('labStages',[]) if s.get('status')=='FAILED'],
             'modelHttpCalls':sum(s['stage']=='model.http' for s in res.get('labStages',[])),
             'businessCandidates':len(result.get('beginnerCases',[]))+len(result.get('careerCases',[])),
             'emptyFields':[{'candidateId':c.get('candidateId'),'fields':[k for k in ['description','difficulty','difficultyReason','vibeCodingReason','recommendReason','prerequisites'] if not c.get(k)]} for c in cards]}
        rows.append(row)
    aggregates={}
    for side in ['A','B']:
        group=[r for r in rows if r['side']==side];good=[r for r in group if r['success']];measured=[r for r in group if r['usage']]
        if not group:continue
        def mean(key):
            v=[r[key] for r in good if isinstance(r.get(key),(int,float))]
            return statistics.mean(v) if v else None
        aggregates[side]={'attempts':len(group),'successes':len(good),'failures':len(group)-len(good),
          'knownUsageCalls':len(measured),'unknownUsageCalls':len(group)-len(measured),
          'modelHttpCalls':sum(r['modelHttpCalls'] for r in group),
          'meanAllWallMs':statistics.mean(r['wallMs'] for r in group),
          'meanSuccessWallMs':mean('wallMs'),'meanSuccessRetrievalMs':mean('retrievalMs'),'meanSuccessModelMs':mean('modelMs'),
          'recommendations':sum(r['recommendations'] for r in good),'emptyRecommendationRuns':sum(r['recommendations']==0 for r in good),
          'knownInputTokens':sum(r['usage'].get('prompt_tokens',0) for r in measured),
          'knownOutputTokens':sum(r['usage'].get('completion_tokens',0) for r in measured),
          'knownReasoningTokens':sum(r['usage'].get('completion_tokens_details',{}).get('reasoning_tokens',0) for r in measured),
          'knownCachedInputTokens':sum(r['usage'].get('prompt_tokens_details',{}).get('cached_tokens',0) for r in measured),
          'duplicateCandidateRuns':sum(bool(r['duplicateCandidateIds']) for r in good),
          'richCardFieldsComplete':sum(not x['fields'] for r in good for x in r['emptyFields']),
          'medianSuccessWallMs':statistics.median([r['wallMs'] for r in good]) if good else None,
          'p95SuccessWallMs':sorted(r['wallMs'] for r in good)[math.ceil(len(good)*.95)-1] if good else None,
          'ragSelectedCards':sum(r['ragSelectedCards'] for r in good),
          'businessSelectedCards':sum(r['businessSelectedCards'] for r in good),
          'boundCards':sum(r['boundCards'] for r in good),
          'reportFieldsComplete':sum(r['reportFieldsComplete'] for r in good),
          'actualModels':sorted({r['actualModel'] for r in measured if r['actualModel']}),
          'costStatus':'UNKNOWN','meanSuccessEvidenceCharacters':mean('evidenceCharacters')}
    paired=[]
    for a in rows:
        if a['side']!='A':continue
        b=next((r for r in rows if r['case']==a['case'] and r['round']==a['round'] and r['side']=='B'),None)
        if b:paired.append({'case':a['case'],'round':a['round'],'bothSuccess':a['success'] and b['success'],
                            'sameActualModel':a['actualModel']==b['actualModel'],
                            'A':{k:a[k] for k in ['wallMs','recommendations','repositories','usage']},
                            'B':{k:b[k] for k in ['wallMs','recommendations','repositories','usage']}})
    summary={'scope':'8 historical synthetic scenarios, 2 repeats each. Actual service path, not browser/full-controller performance. No independent blind-set claim.',
             'aggregates':aggregates,'pairs':paired,'rows':rows,
             'limitations':['Source URL binding is not factual correctness.','Failed requests may consume unreported tokens.',
                            'Embedding API usage and local power cost are not included in generation usage.',
                            'No verified current Gitee billing price; no fabricated currency total.']}
    by_model={}
    for model in sorted({r['actualModel'] for r in rows if r['actualModel']}):
        by_model[model]={}
        for side in ['A','B']:
            group=[r for r in rows if r['success'] and r['side']==side and r['actualModel']==model]
            by_model[model][side]={'count':len(group),'meanWallMs':statistics.mean(r['wallMs'] for r in group) if group else None,
              'meanOutputTokens':statistics.mean(r['usage']['completion_tokens'] for r in group) if group else None}
    summary['providerResponseModelStrata']=by_model
    summary['matchedModelPairs']=sum(p['bothSuccess'] and p['sameActualModel'] for p in paired)
    pair_groups={}
    for label,eligible in [('bothSuccess',[p for p in paired if p['bothSuccess']]),
                           ('bothSuccessSameResponseModel',[p for p in paired if p['bothSuccess'] and p['sameActualModel']])]:
        pair_groups[label]={'pairs':len(eligible)}
        for side in ['A','B']:
            group=[r for r in rows if r['side']==side and any(r['case']==p['case'] and r['round']==p['round'] for p in eligible)]
            pair_groups[label][side]={k:statistics.mean(r[k] for r in group) if group else None for k in ['wallMs','modelMs','retrievalMs']}
            pair_groups[label][side].update({k:statistics.mean(r['usage'].get(k,0) for r in group) if group else None for k in ['prompt_tokens','completion_tokens','total_tokens']})
    summary['pairedSuccessAggregates']=pair_groups
    stability=[]
    for a in rows:
        if a['round']!=1:continue
        b=next((r for r in rows if r['round']==2 and r['case']==a['case'] and r['side']==a['side']),None)
        if not b:continue
        def overlap(key):
            left,right=set(a[key]),set(b[key]); union=left|right
            return len(left&right)/len(union) if union else None
        stability.append({'case':a['case'],'side':a['side'],'retrievalSetJaccard':overlap('retrievedRepositories'),
                          'recommendationSetJaccard':overlap('repositories'),
                          'bothEmpty':not a['repositories'] and not b['repositories']})
    summary['twoRoundStability']=stability
    staged=ROOT/'_experiment_staging/trend-ab-20261009'
    summary['identicalPromptChecks']=[]
    for case in sorted({r['case'] for r in rows}):
        for first,second in [('1-A','1-B'),('2-A','2-B'),('1-A','2-A'),('1-B','2-B')]:
            left,right=[staged/f'{case}-{suffix}-request.json' for suffix in [first,second]]
            if left.exists() and right.exists():
                summary['identicalPromptChecks'].append({'case':case,'first':first,'second':second,
                    'identical':json.loads(left.read_text(encoding='utf-8'))==json.loads(right.read_text(encoding='utf-8'))})
    summary['byRound']=[{'round':rnd,'side':side,'count':len(g),
      'meanWallMs':statistics.mean(r['wallMs'] for r in g),
      'meanRetrievalMs':statistics.mean(r['retrievalMs'] for r in g),
      'meanModelMs':statistics.mean(r['modelMs'] for r in g)}
      for rnd in [1,2] for side in ['A','B']
      if (g:=[r for r in rows if r['success'] and r['round']==rnd and r['side']==side])]
    (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'review-packet.json').write_text(json.dumps(review,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(aggregates,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
