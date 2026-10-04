"""Audit actual experiment consumption; monetary estimate from public Gitee policy."""
import json
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'_experiment_staging/rag-model-chunk-20261002'
REPORT=ROOT/'docs/rag-b/model-chunk-experiment'

def main():
    batches=[json.loads(l) for l in (STAGE/'calls.jsonl').read_text(encoding='utf-8').splitlines()]
    singles=[json.loads(l) for l in (STAGE/'single-query-calls.jsonl').read_text(encoding='utf-8').splitlines()]
    rows=[]
    for model in ['bge-small-zh-v1.5','bge-large-zh-v1.5','bge-m3','Qwen3-Embedding-0.6B','Qwen3-Embedding-4B']:
        b=[r for r in batches if r['model']==model];s=[r for r in singles if r['model']==model]
        events=b+s;tokens=0;unknown=0
        for r in events:
            usage=r.get('usage') or {};count=usage.get('total_tokens',usage.get('totalTokens'))
            if isinstance(count,(int,float)) and count>0:tokens+=count
            else:unknown+=1
        rows.append({'model':model,'batchRequests':len(b),'singleRequests':len(s),
            'successfulRequests':len(events),'inputItems':sum(r.get('items',1) for r in events),
            'inputCharacters':sum(r['inputCharacters'] for r in events),
            'positiveReportedTokens':tokens,'unknownOrZeroUsageRequests':unknown,
            'estimatedEmbeddingFeeCnyUnderPublishedPolicy':0,'accountDebitsVerified':False})
    total={key:sum(row[key] for row in rows) for key in ['batchRequests','singleRequests','successfulRequests',
        'inputItems','inputCharacters','positiveReportedTokens','unknownOrZeroUsageRequests']}
    result={'checkedAt':datetime.now(timezone.utc).isoformat(),'models':rows,'totals':total,
        'scope':'Only the 18-configuration experiment and fresh query-latency test; excludes earlier model discovery and older A/B evaluations.',
        'pricingSource':'https://ai.gitee.com/docs/products/apis/embeddings/',
        'pricingStatement':'The official page states all deployed embedding and rerank models are free.',
        'estimatedEmbeddingFeeCnyUnderPublishedPolicy':0,'accountDebitsVerified':False,
        'generationRequests':0,'notes':['Zero usage is unknown token consumption, not proof of zero billing.',
        'Different models ran different configuration counts; experiment totals are not unit cost comparisons.',
        'No model price from a different provider was substituted.',
        'Excludes compute, database, storage and any future LLM answer generation.']}
    (REPORT/'cost-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(total,ensure_ascii=False))

if __name__=='__main__':main()
