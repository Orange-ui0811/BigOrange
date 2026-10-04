"""Full original-content import and real-platform before/after validation.

Never changes A documents/indexes or platform user routing. Captures each case,
errors, wall-clock latency and evidence in reviewable local experiment artifacts.
"""
import argparse
import concurrent.futures
import json
import sys
import time
from datetime import datetime,timezone
from pathlib import Path

import numpy as np
from export_rag_model_corpus import query
from run_rag_b_first_round import call
from run_rag_model_chunk_experiment import ROOT, digest, load_json, norm, write_json

OUT=ROOT/'docs/rag-b/full-original-validation'


def ready():
    for _ in range(90):
        try:
            status=call('GET','/admin/rag-lab/status',timeout=5)
            if not status['enabled']:raise RuntimeError('B disabled')
            return status
        except Exception:
            time.sleep(3)
    raise RuntimeError('Local backend did not become ready')


def imports():
    ready(); manifest_path=OUT/'import-manifest.json'
    if manifest_path.exists():manifest=load_json(manifest_path)
    else:
        docs=query("""SELECT JSON_OBJECT('id',id,'source',source,'sourceRef',source_ref,
            'bytes',OCTET_LENGTH(original_content),'characters',CHAR_LENGTH(original_content),
            'hash',SHA2(REPLACE(REPLACE(original_content,CONCAT(CHAR(13),CHAR(10)),CHAR(10)),CHAR(13),CHAR(10)),256))
            FROM knowledge_document WHERE original_content IS NOT NULL AND TRIM(original_content)<>'' ORDER BY id""")
        manifest={'at':datetime.now(timezone.utc).isoformat(),'documents':docs,'results':{},
                  'selection':'Non-null, nonblank original_content only; no fallback content selected.'}
        write_json(manifest_path,manifest)
    for i,doc in enumerate(manifest['documents']):
        key=str(doc['id'])
        if key in manifest['results'] and 'error' not in manifest['results'][key]:continue
        try:result=call('POST',f"/admin/rag-lab/imports/legacy/{doc['id']}",timeout=120)
        except Exception as e:result={'error':str(e)[:300]}
        manifest['results'][key]=result;write_json(manifest_path,manifest)
        if i%10==0:print(f'IMPORT {i+1}/{len(manifest["documents"])}',flush=True)
    ids=sorted({r['documentId'] for r in manifest['results'].values() if 'documentId' in r})
    if not ids:raise RuntimeError('No documents imported')
    expression=','.join(map(str,ids))
    deadline=time.time()+7200
    while time.time()<deadline:
        states=query("SELECT JSON_OBJECT('status',status,'n',COUNT(*)) FROM rag_b_document WHERE id IN ("+expression+") GROUP BY status")
        tasks=query("SELECT JSON_OBJECT('status',status,'n',COUNT(*)) FROM system_task WHERE task_type LIKE 'RAG_B%' GROUP BY status")
        print('INDEX_PROGRESS '+json.dumps({'documents':states,'tasks':tasks}),flush=True)
        if all(s['status'] in ['READY','FAILED'] for s in states):break
        time.sleep(15)
    audited=query("""SELECT JSON_OBJECT('id',d.id,'source',d.source_type,'sourceRef',d.source_ref,
        'status',d.status,'versionId',v.id,'chunks',v.chunk_count,'hash',v.content_hash,
        'error',d.last_error) FROM rag_b_document d LEFT JOIN rag_b_document_version v ON v.id=d.active_version_id
        WHERE d.id IN ("""+expression+") ORDER BY d.id")
    write_json(OUT/'import-result.json',{'manifest':manifest,'audited':audited})
    if any(r['status']!='READY' for r in audited):raise RuntimeError('Import incomplete; inspect import-result')
    expected={(d['source'],d['sourceRef']):d['hash'] for d in manifest['documents']}
    mismatches=[r['id'] for r in audited if expected[(r['source'],r['sourceRef'])]!=r['hash']]
    if mismatches:raise RuntimeError('Imported source hash mismatch: '+str(mismatches))
    print('IMPORT_COMPLETE '+str(len(audited))+' documents '+str(sum(r['chunks'] for r in audited))+' chunks',flush=True)


def search_one(case):
    started=time.perf_counter()
    body={'query':case['question'],'topK':8,'threshold':.55,'sourceTypes':case['types']}
    try:
        result=call('POST','/admin/rag-lab/search',body,timeout=120)
        hits=result.get('hits',[]);rank_=0
        for i,hit in enumerate(hits,1):
            if hit['sourceRef'] in case['refs'] and (not case['anchors'] or any(
                norm(a) in norm(hit['content']) for a in case['anchors'])):
                rank_=i;break
        return {'key':case['key'],'category':case['category'],'rank':rank_,
            'wallMs':(time.perf_counter()-started)*1000,'result':result}
    except Exception as e:
        return {'key':case['key'],'category':case['category'],'rank':0,
                'wallMs':(time.perf_counter()-started)*1000,'error':str(e)[:300]}


def summary(rows):
    return {'n':len(rows),'errors':sum('error' in r for r in rows),
        'evidenceRecall8':sum(r['rank']>0 for r in rows)/len(rows),
        'evidenceMRR8':sum(1/r['rank'] if r['rank'] else 0 for r in rows)/len(rows),
        'p50Ms':float(np.percentile([r['wallMs'] for r in rows],50)),
        'p95Ms':float(np.percentile([r['wallMs'] for r in rows],95))}


def evaluate(phase):
    ready();path=OUT/(phase+'-retrieval.json')
    cases=load_json(ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json')['cases']
    rows=[]
    for i,case in enumerate(cases):
        row=search_one(case);rows.append(row)
        write_json(path,{'phase':phase,'rows':rows,'complete':False})
        if i%20==0:print('RETRIEVAL '+phase+' '+str(i+1)+'/'+str(len(cases)),flush=True)
    result={'phase':phase,'complete':True,'datasetHash':digest(cases),'rows':rows,
        'semantic':summary([r for r in rows if r['category']!='named_regression']),
        'categories':{cat:summary([r for r in rows if r['category']==cat]) for cat in sorted({r['category'] for r in rows})}}
    write_json(path,result);print('RETRIEVAL_RESULT '+json.dumps(result['semantic']),flush=True)
    benchmark=call('POST','/admin/rag-lab/evaluation/benchmark/run?topK=8&threshold=0.55',timeout=1800)
    write_json(OUT/(phase+'-business-ab.json'),benchmark)
    print('BUSINESS_AB '+json.dumps(benchmark.get('summary',{}),ensure_ascii=False),flush=True)
    loads=[]
    # Modest burst loads; timings include actual provider query embedding.
    pool_cases=[c for c in cases if c['key'].startswith('fresh-')][:24]
    for concurrency in [1,4,8]:
        started=time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
            loaded=list(pool.map(search_one,pool_cases))
        elapsed=time.perf_counter()-started
        loads.append({'concurrency':concurrency,'requests':len(loaded),'elapsedSeconds':elapsed,
            'throughputRps':len(loaded)/elapsed,'metrics':summary(loaded),'rows':loaded})
        print('LOAD '+phase+' '+json.dumps({k:v for k,v in loads[-1].items() if k!='rows'}),flush=True)
    write_json(OUT/(phase+'-load.json'),{'loads':loads,'limits':'24-request bursts; not long-duration capacity/SLA evidence.'})


def answers(phase):
    ready();all_cases=load_json(ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json')['cases']
    selected=[f'fact-{i:02}-zh' for i in [0,2,3,4,7,12,16,18,20,21,24,25,26,28,29]]+['fresh-02','fresh-03','fresh-21']
    cases=[c for c in all_cases if c['key'] in selected]
    path=OUT/(phase+'-answers.json');rows=load_json(path)['rows'] if path.exists() else []
    done={r['key'] for r in rows}
    for case in cases:
        if case['key'] in done:continue
        started=time.perf_counter()
        try:
            result=call('POST','/admin/rag-lab/answer-preview',
                {'query':case['question'],'topK':8,'threshold':.55,'sourceTypes':case['types']},timeout=180)
            row={'key':case['key'],'question':case['question'],'expectedRefs':case['refs'],
                 'goldAnchors':case['anchors'],'wallMs':(time.perf_counter()-started)*1000,'result':result}
        except Exception as e:row={'key':case['key'],'error':str(e)[:300],'wallMs':(time.perf_counter()-started)*1000}
        rows.append(row);write_json(path,{'phase':phase,'rows':rows,'complete':False})
        print('ANSWER '+phase+' '+case['key']+' '+row.get('result',{}).get('status','ERROR'),flush=True)
    write_json(path,{'phase':phase,'rows':rows,'complete':True,'limitations':[
        'Validator is lexical support heuristic, not semantic entailment or human accuracy.',
        'Chinese answers over English evidence can be falsely rejected; inspect claim/evidence separately.']})


def main():
    sys.stdout.reconfigure(encoding='utf-8');OUT.mkdir(parents=True,exist_ok=True)
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['import','evaluate','answers']);
    parser.add_argument('--phase',default='baseline');args=parser.parse_args()
    if args.action=='import':imports()
    elif args.action=='evaluate':evaluate(args.phase)
    else:answers(args.phase)


if __name__=='__main__':main()
