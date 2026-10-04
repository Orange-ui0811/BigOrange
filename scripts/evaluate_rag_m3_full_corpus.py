"""Full-corpus deterministic A/B retrieval, business and short-burst performance."""
import json,time,sys,concurrent.futures
from pathlib import Path
import numpy as np
from run_rag_b_first_round import call
from run_rag_model_chunk_experiment import norm
import run_rag_b_full_original_validation as prior

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/m3-full-corpus'
def write(name,data):(OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def ranks(case,hits,side):
    source=0;evidence=0
    refs={r.split('|',1)[0] for r in case['refs']}
    for i,h in enumerate(hits,1):
        ref=(h.get('sourceRef') if side=='b' else h.get('metadata',{}).get('sourceRef')) or ''
        if ref.split('|',1)[0] in refs:
            if not source:source=i
            if not evidence and (not case['anchors'] or any(norm(a) in norm(h['content']) for a in case['anchors'])):evidence=i
    return source,evidence
def aggregate(rows,side):
    values=[r[side] for r in rows];n=len(values)
    return {'n':n,'errors':sum(bool(v.get('error')) for v in values),
        'sourceRecall8':sum(v['sourceRank']>0 for v in values)/n,
        'sourceMRR8':sum(1/v['sourceRank'] if v['sourceRank'] else 0 for v in values)/n,
        'literalAnchorRecall8':sum(v['evidenceRank']>0 for v in values)/n,
        'literalAnchorMRR8':sum(1/v['evidenceRank'] if v['evidenceRank'] else 0 for v in values)/n,
        'p50Ms':float(np.percentile([float(v['durationMs']) for v in values],50)),
        'p95Ms':float(np.percentile([float(v['durationMs']) for v in values],95))}
def main():
    sys.stdout.reconfigure(encoding='utf-8');OUT.mkdir(parents=True,exist_ok=True)
    audit=json.loads((OUT/'corpus-audit.json').read_text(encoding='utf-8'))
    if audit['failures']:raise RuntimeError('Incomplete corpus')
    cases=json.loads((ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json').read_text(encoding='utf-8'))['cases']
    rows=[]
    for i,c in enumerate(cases):
        pair=call('POST','/admin/rag-lab/compare/retrieval',{'query':c['question'],'topK':8,'threshold':.55},timeout=180)
        row={'key':c['key'],'category':c['category'],'result':pair}
        for side in ['a','b']:
            s,e=ranks(c,pair[side]['hits'],side)
            row[side]={'sourceRank':s,'evidenceRank':e,'durationMs':pair[side]['durationMs'],'error':pair[side].get('error')}
        rows.append(row);write('controlled-retrieval.json',{'rows':rows,'complete':False})
        if i%20==0:print('AB '+str(i+1)+'/'+str(len(cases)),flush=True)
    semantic=[r for r in rows if r['category']!='named_regression']
    result={'rows':rows,'complete':True,'semantic90':{s:aggregate(semantic,s) for s in ['a','b']},
        'named50':{s:aggregate([r for r in rows if r['category']=='named_regression'],s) for s in ['a','b']},
        'limits':['A direct VectorStore vs B native hybrid pipeline, no explicit source filters, top8 threshold .55.',
                  'Literal anchors may penalize A translations. Use RAGAS semantic reference coverage alongside this metric.',
                  'Source Recall@8 is a question-level hit rate, not full relevant-chunk recall.',
                  '140 reused diagnostic cases; not an independent held-out set.']}
    write('controlled-retrieval.json',result);print('CONTROLLED '+json.dumps(result['semantic90']),flush=True)
    # Retain previous source-scoped B retrieval / business / load protocol for longitudinal comparison.
    prior.OUT=OUT
    prior.evaluate('m3-full')
    # Separate native A platform retrieval and B retrieval at low burst loads (no generation).
    pool_cases=[c for c in cases if c['key'].startswith('fresh-')][:24];loads=[]
    def native(c):
        value=call('POST','/admin/rag-lab/compare/platform-retrieval',{'query':c['question']},timeout=180)
        return {'key':c['key'],'aMs':float(value['a']['durationMs']),'bMs':float(value['b']['durationMs']),
                'aStatus':value['a']['trace']['status']}
    for concurrency in [1,4,8]:
        started=time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool: values=list(pool.map(native,pool_cases))
        loads.append({'concurrency':concurrency,'elapsedSeconds':time.perf_counter()-started,'requests':len(values),
            'aP95Ms':float(np.percentile([v['aMs'] for v in values],95)),
            'bP95Ms':float(np.percentile([v['bMs'] for v in values],95)),'rows':values})
        print('NATIVE_LOAD '+json.dumps({k:v for k,v in loads[-1].items() if k!='rows'}),flush=True)
    write('platform-load.json',{'loads':loads,'limits':'Sequential A then B within each request; shared provider/resource contention. Short bursts, not sustained capacity.'})

if __name__=='__main__':main()
