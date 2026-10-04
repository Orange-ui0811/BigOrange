"""Fresh single-query requests, randomized interleaving; exploratory provider latency."""
import argparse
import json
import random
import sys
import time
from datetime import datetime,timezone
from pathlib import Path
import numpy as np
from run_rag_model_chunk_experiment import Provider, REPORT, STAGE, instructed_query

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    p=argparse.ArgumentParser();p.add_argument('--count',type=int,default=30);args=p.parse_args()
    summary=json.loads((REPORT/'summary.json').read_text(encoding='utf-8'))
    models=['bge-small-zh-v1.5']
    instructions={'bge-small-zh-v1.5':False}
    for row in summary['plans']:
        if row['model'] not in models:
            models.append(row['model']);instructions[row['model']]=bool(row.get('queryInstruction'))
        if len(models)==5:break
    cases=[c for c in json.loads((REPORT/'dataset.json').read_text(encoding='utf-8'))['cases'] if c['anchors']]
    rng=random.Random(20261002);rng.shuffle(cases);cases=cases[:args.count]
    tasks=[(model,c['key'],c['question'],False) for c in cases for model in models]
    rng.shuffle(tasks)
    tasks=[(model,'warmup',cases[0]['question'],True) for _ in range(2) for model in models]+tasks
    provider=Provider();events=[]
    for index,(model,key,question,warm) in enumerate(tasks):
        if instructions[model]:question=instructed_query(model,question)
        start=time.perf_counter();status=None
        try:
            r=provider.session.post(provider.url,headers={'Authorization':'Bearer '+provider.key},
                json={'model':model,'input':[question]},timeout=60)
            status=r.status_code
            if status!=200:raise ValueError('Provider rejected request')
            data=r.json();vector=data['data'][0]['embedding']
            if not vector or not all(np.isfinite(vector)):raise ValueError('Invalid vector')
            event={'model':model,'key':key,'warmup':warm,'status':status,
                   'durationMs':round((time.perf_counter()-start)*1000,2),'usage':data.get('usage'),
                   'dimensions':len(vector),'inputCharacters':len(question)}
        except Exception as e:
            event={'model':model,'key':key,'warmup':warm,'status':status,
                   'durationMs':round((time.perf_counter()-start)*1000,2),'errorType':type(e).__name__}
        events.append(event)
        with (STAGE/'single-query-calls.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(event)+'\n')
        if index%15==0:print('SINGLE_QUERY_PROGRESS',index+1,len(tasks),flush=True)
    stats={}
    for model in models:
        rows=[e for e in events if e['model']==model and not e['warmup']]
        durations=[e['durationMs'] for e in rows if 'errorType' not in e]
        stats[model]={'requests':len(rows),'successes':len(durations),'errors':len(rows)-len(durations),
            'p50Ms':float(np.percentile(durations,50)) if durations else None,
            'p95Ms':float(np.percentile(durations,95)) if durations else None}
    result={'at':datetime.now(timezone.utc).isoformat(),'summary':stats,'calls':events,'queryInstructions':instructions,
            'note':'Fresh sequential single-query requests, randomized interleaving; provider + network only, no concurrency or retrieval/generation.'}
    (REPORT/'single-query-latency.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(stats,ensure_ascii=False),flush=True)

if __name__=='__main__':main()
