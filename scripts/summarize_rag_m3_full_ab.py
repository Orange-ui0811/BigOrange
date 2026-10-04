"""Combine full-corpus audits, retrieval, load and RAGAS without mixing protocols."""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/rag-b/m3-full-corpus'
def read(name):return json.loads((OUT/name).read_text(encoding='utf-8'))
def main():
    corpus=read('corpus-audit.json');chunks=read('chunk-audit.json');controlled=read('controlled-retrieval.json')
    scoped=read('m3-full-retrieval.json');business=read('m3-full-business-ab.json')
    ragas=read('ragas/summary.json');scores=read('ragas/results.json');samples=read('ragas/samples.json')
    byid={s['id']:s for s in samples['samples']};citations={};costs={}
    for side in ['A','B']:
        rows=[r for r in scores['rows'].values() if r['system']==side];total=valid=with_cites=generated=0
        for row in rows:
            if not row['answerGenerated']:continue
            generated+=1;refs=[int(x) for x in re.findall(r'\[C(\d+)\]',row['response'])]
            with_cites+=bool(refs);total+=len(refs)
            valid+=sum(1<=r<=len(byid[row['id']]['retrieved_contexts']) for r in refs)
        citations[side]={'generatedAnswers':generated,'answersWithCitation':with_cites,'citationMentions':total,
                         'validPointerMentions':valid,'pointerValidity':valid/total if total else None,
                         'limitation':'Pointer validity does not establish that the cited chunk supports its attached claim.'}
    events=[json.loads(l) for l in (OUT/'ragas/calls.jsonl').read_text(encoding='utf-8').splitlines()]
    from run_ragas_m3_full_ab import peak_cost
    for purpose in ['answer','judge']:
        done=[e for e in events if e['kind']=='DONE' and e['purpose']==purpose]
        peak=sum(peak_cost(e['usage']) for e in done)
        costs[purpose]={'attempts':sum(e['kind']=='START' and e['purpose']==purpose for e in events),
                        'successfulRequests':len(done),'totalTokens':sum(e['usage'].get('total_tokens',0) for e in done),
                        'estimatedPeakUsd':peak,'estimatedOffPeakUsd':peak/2}
    paired={}
    for metric in ['context_precision','context_recall','faithfulness','factual_correctness']:
        pairs=[]
        for sample in samples['samples']:
            if sample['system']!='A':continue
            left=scores['rows'][sample['id']]['metrics'].get(metric)
            right=scores['rows'][sample['key']+'-b']['metrics'].get(metric)
            if left is not None and right is not None:pairs.append((left,right))
        paired[metric]={'n':len(pairs),'aMean':sum(p[0] for p in pairs)/len(pairs) if pairs else None,
                        'bMean':sum(p[1] for p in pairs)/len(pairs) if pairs else None}
    native=read('native-quality.json')
    result={'complete':scores['complete'] and controlled['complete'] and native['complete'],'corpus':corpus,'chunkAudit':chunks,'indexStats':read('index-stats.json'),
            'nativeQuality':{k:native[k] for k in ['semantic90','named50','limits']},
            'controlledRetrieval':{'semantic90':controlled['semantic90'],'named50':controlled['named50'],'limits':controlled['limits']},
            'sourceScopedB':{'semantic90':scoped['semantic'],'categories':scoped['categories']},
            'business60':business['summary'],'bBurstLoad':read('m3-full-load.json'),
            'nativePlatformBurstLoad':read('platform-load.json'),'ragas':ragas,'pairedRagas':paired,'targetedReview':read('targeted-review.json'),'citationPointerAudit':citations,'costByPurpose':costs,
            'limits':['Full existing database coverage is tested; not a live replacement or full product end-to-end acceptance.',
                      'A source text/translation and B original-first ingestion differ by design; this is a deployed-system comparison, not embedding-only causality.',
                      'All diagnostic questions reused; report improvement without claiming independent generalization.',
                      'Short concurrent bursts do not prove a sustained production SLA.',
                      'Gitee embedding billing usage is not captured by the existing Java adapter; token audit is not an actual bill.',
                      'RAGAS faithfulness is whole-context support, separate from individual cited-chunk entailment.']}
    (OUT/'comparison-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False),encoding='utf-8')
    print(json.dumps({'retrieval':result['controlledRetrieval'],'business60':result['business60'],'ragas':ragas['summary'],'cost':costs},ensure_ascii=False))
if __name__=='__main__':main()
