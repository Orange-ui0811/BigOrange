"""Summarize the isolated experiment, with paired-fact uncertainty and audit totals."""
import json
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'_experiment_staging/rag-model-chunk-20261002'
REPORT=ROOT/'docs/rag-b/model-chunk-experiment'

def read(path):return json.loads(path.read_text(encoding='utf-8'))

def paired_comparison(candidate,baseline):
    base={r['key']:r for r in baseline['rows']}
    groups={}
    for row in candidate['rows']:
        if row['category']=='named_regression':continue
        old=base[row['key']]
        rank=row['evidenceRank'];oldrank=old['evidenceRank']
        delta=(1/rank if 0<rank<=8 else 0)-(1/oldrank if 0<oldrank<=8 else 0)
        groups.setdefault(row['group'],[]).append(delta)
    values=np.array([np.mean(v) for v in groups.values()])
    rng=np.random.default_rng(20261002)
    resampled=values[rng.integers(0,len(values),size=(10000,len(values)))].mean(axis=1)
    return {'factGroups':len(values),'mrrDelta':float(values.mean()),
        'pairedBootstrap95':[float(v) for v in np.quantile(resampled,[.025,.975])],
        'note':'Exploratory paired-fact bootstrap; does not correct selection across configurations.'}

def main():
    results=[]
    for p in REPORT.glob('*.json'):
        x=read(p)
        if 'semantic' in x and 'rows' in x:results.append(x)
    baseline=next((x for x in results if x['plan']=='current-small-char500'),None)
    ranked=sorted(results,key=lambda x:(x['semantic']['evidenceMRR8'],x['semantic']['evidenceRecall8']),reverse=True)
    rows=[]
    for r in ranked:
        row={k:r[k] for k in ['plan','model','chunks','dimensions','semantic','categories','vectorBytes']}
        row['queryInstruction']=r.get('queryInstruction')
        row['instructionTemplate']=r.get('instructionTemplate')
        if baseline:row['versusCurrent']=paired_comparison(r,baseline)
        rows.append(row)
    ledger=[json.loads(l) for l in (STAGE/'calls.jsonl').read_text().splitlines()] if (STAGE/'calls.jsonl').exists() else []
    audit={}
    for model in sorted({x['model'] for x in ledger}):
        events=[x for x in ledger if x['model']==model]
        known_tokens=[];missing=0
        for event in events:
            usage=event.get('usage') or {}
            count=usage.get('total_tokens',usage.get('totalTokens'))
            if isinstance(count,(int,float)) and count>0:known_tokens.append(count)
            else:missing+=1
        audit[model]={'successfulBatches':len(events),'items':sum(x['items'] for x in events),
            'inputCharacters':sum(x['inputCharacters'] for x in events),
            'positiveReportedTokens':sum(known_tokens),'missingOrZeroUsageBatches':missing,
            'batchP50Ms':float(np.percentile([x['durationMs'] for x in events],50)),
            'batchP95Ms':float(np.percentile([x['durationMs'] for x in events],95)),
            'note':'Batch timings mix document/query sizes; not online query latency or bill.'}
    errors=(STAGE/'errors.jsonl').read_text().splitlines() if (STAGE/'errors.jsonl').exists() else []
    summary={'rankingMetric':'60 semantic questions evidence MRR@8','completedPlans':len(rows),
        'plans':rows,'providerAudit':audit,'errorAttempts':len(errors),'textGenerationCalls':0,
        'limitations':['assistant-authored source-scoped gold, not independent human blind test',
        'base plans use raw queries; supplementary plans use model-card query instructions',
        'exact normalized anchor containment is an evidence proxy, not semantic entailment',
        'no RRF, business hard filters, ANN, rerank, refusal or final generation measured',
        'vectorBytes excludes Redis/HNSW metadata and graph memory']}
    (REPORT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'completedPlans':len(rows),'topPlans':[{'plan':r['plan'],'mrr':r['semantic']['evidenceMRR8'],
        'recall8':r['semantic']['evidenceRecall8']} for r in rows],'errors':len(errors)},ensure_ascii=False))

if __name__=='__main__':main()
