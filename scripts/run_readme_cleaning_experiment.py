"""Local-only corpus projection experiment. SELECT/JSON.MGET production; never writes indexes.

All source payloads and vector caches stay in ignored staging. No cloud provider code is called.
"""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request

import numpy as np
from tokenizers import Tokenizer
from export_rag_model_corpus import query
from rag_readme_cleaning import clean, quality
import run_rag_model_chunk_experiment as chunk_prototype
from run_rag_model_chunk_experiment import split_document, norm

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/readme-cleaning-20261008'
OUT = ROOT / 'docs/rag-b/readme-cleaning-20261008'
MODEL = 'bge-m3:orangejd-v1'
VARIANTS = ['raw', 'light', 'structured', 'no_code_ablation', 'selective']


def java_sections(text):
    """Match Java's ASCII-space Setext rule; a tab-indented '-' is not a heading."""
    lines=text.splitlines(keepends=True);offsets=[];at=0
    for line in lines:offsets.append(at);at+=len(line)
    starts=[(0,None)];path=[None]*6;fence=None;i=0
    while i<len(lines):
        s=lines[i].rstrip();start=offsets[i];f=re.match(r'^\s{0,3}(`{3,}|~{3,})',s)
        if f:
            mark=f.group(1)
            if fence is None:fence=mark
            elif mark[0]==fence[0] and len(mark)>=len(fence):fence=None
            i+=1;continue
        if fence is not None:i+=1;continue
        h=re.fullmatch(r' {0,3}(#{1,6})\s+(.+)',s)
        if h:level=len(h.group(1));title=re.sub(r'\s+#+\s*$','',h.group(2)).strip()
        elif s.strip() and not re.match(r'^\s*[-*+]',s) and i+1<len(lines) and re.fullmatch(r' {0,3}(=+|-+)\s*',lines[i+1].rstrip()):
            level=1 if lines[i+1].lstrip().startswith('=') else 2;title=s.strip();i+=1
        else:i+=1;continue
        path[level-1]=title
        for j in range(level,6):path[j]=None
        label=' > '.join(v for v in path if v is not None)
        if start==starts[-1][0]:starts[-1]=(start,label)
        else:starts.append((start,label))
        i+=1
    return [(a,starts[i+1][0] if i+1<len(starts) else len(text),label) for i,(a,label) in enumerate(starts)]


chunk_prototype.sections=java_sections


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def local(path, body=None):
    req = urllib.request.Request('http://127.0.0.1:11434'+path,
        data=None if body is None else json.dumps(body).encode(), headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def freeze():
    if (STAGE/'snapshot.json').exists():
        print('Existing immutable snapshot; use it.', flush=True); return
    g = query("SELECT JSON_OBJECT('id',id,'generation',generation_key,'provider',embedding_provider,'revision',embedding_revision,'model',embedding_model,'dimensions',embedding_dimensions,'policy',chunk_policy) FROM rag_b_index_generation WHERE active=1")
    if len(g)!=1 or g[0]['provider']!='OLLAMA': raise ValueError('Require active local generation')
    tag = next(x for x in local('/api/tags')['models'] if x['name']==MODEL)
    if g[0]['revision'].removeprefix('sha256:') != tag['digest'].removeprefix('sha256:'): raise ValueError('Model mismatch')
    docs = query("""SELECT JSON_OBJECT('id',d.id,'sourceType',d.source_type,'sourceRef',d.source_ref,
        'title',d.title,'versionId',v.id,'contentHash',v.content_hash,'content',v.original_content)
        FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id
        WHERE d.status='READY' AND v.status='READY' AND d.deleted_at IS NULL ORDER BY d.id""")
    chunks = query(f"""SELECT JSON_OBJECT('documentId',c.document_id,'chunkId',c.id,'section',c.section_path,
        'startUtf16',c.start_offset,'endUtf16',c.end_offset,'content',c.content,'vectorKey',gc.vector_key)
        FROM rag_b_chunk c JOIN rag_b_document d ON d.active_version_id=c.version_id
        JOIN rag_b_generation_chunk gc ON gc.chunk_id=c.id AND gc.generation_id={int(g[0]['id'])}
        WHERE d.status='READY' AND d.deleted_at IS NULL ORDER BY c.document_id,c.chunk_index""")
    lookup = {d['id']:d for d in docs}
    for c in chunks:
        d=lookup[c['documentId']]
        c.update({k:d[k] for k in ['sourceType','sourceRef','title']})
        raw=d['content'].encode('utf-16-le')
        if raw[c['startUtf16']*2:c['endUtf16']*2].decode('utf-16-le')!=c['content']: raise ValueError('Original span mismatch')
    cases=read(ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json')['cases']
    # No edits to old questions/labels. Check availability separately before running retrieval.
    for c in cases:
        matched=[d for d in docs if d['sourceRef'].split('|')[0] in c['refs'] and d['sourceType'] in c['types']]
        c['goldAvailable']=bool(matched) and (not c['anchors'] or any(all(norm(a) in norm(d['content']) for a in c['anchors']) for d in matched))
    snapshot={'at':datetime.now(timezone.utc).isoformat(),'generation':g[0],'modelDigest':tag['digest'],'documents':docs,'chunks':chunks}
    write(STAGE/'snapshot.json',snapshot)
    write(OUT/'dataset.json',{'cases':cases,'note':'Historical development questions, not independent blind acceptance. Freeze before retrieval.'})
    write(OUT/'manifest.json',{'at':snapshot['at'],'generation':g[0],'documents':len(docs),'githubDocuments':sum(d['sourceType']=='GITHUB' for d in docs),
        'chunks':len(chunks),'corpusSha256':sha(json.dumps(docs,ensure_ascii=False,sort_keys=True)),
        'datasetSha256':sha(json.dumps(cases,ensure_ascii=False,sort_keys=True)), 'questions':len(cases),
        'unavailableGold':[c['key'] for c in cases if not c['goldAvailable']], 'externalCalls':0})
    print('FROZEN',len(docs),len(chunks),len(cases),flush=True)


def prepare(selected=None):
    snap=read(STAGE/'snapshot.json'); docs=snap['documents']; stored=defaultdict(list)
    for c in snap['chunks']: stored[c['documentId']].append(c)
    tokenizer=Tokenizer.from_file(str(ROOT/'orange-server/src/main/resources/rag-b/tokenizers/bge-m3.json'))
    tokenizer.no_truncation(); tokenizer.no_padding()
    variants=[selected] if selected else VARIANTS
    stats=[s for s in read(OUT/'document-statistics.json') if s['variant'] not in variants] if (OUT/'document-statistics.json').exists() else []
    parity=read(OUT/'chunk-parity.json') if selected and selected!='raw' else []
    for variant in variants:
        output=[]; packets=[]; start=time.perf_counter()
        for n,d in enumerate(docs):
            if d['sourceType']!='GITHUB': output.extend(stored[d['id']]); continue
            p=clean(d['content'],variant)
            # Same Python port for all four conditions, with stored chunk parity reported below.
            chunks=split_document({**d,'content':p.text},[tokenizer],512,768,64) if p.text else []
            if variant=='raw':
                parity.append({'documentId':d['id'],'sameTextBoundaries':[c['content'] for c in chunks]==[c['content'] for c in stored[d['id']]],'generated':len(chunks),'stored':len(stored[d['id']])})
            # Every retained original span must round-trip; introduced whitespace has no evidence identity.
            spans=p.spans()
            for s in spans:
                if s['originalStart']>=0 and p.text[s['start']:s['end']]!=d['content'][s['originalStart']:s['originalEnd']]: raise ValueError('Broken origin mapping')
            packets.append({'documentId':d['id'],'sourceRef':d['sourceRef'],'versionId':d['versionId'],
                'originalSha256':sha(d['content']),'cleanSha256':sha(p.text),'projection':p.text,
                'offsetUnit':'Unicode code point; convert to UTF-16 before Java use', 'originalSpans':spans,'qualityFlags':quality(d['content']),
                'factBoundary':'Source text only; job fit, personalized reasons and suggested work are not inferred by this cleaner.'})
            stats.append({'variant':variant,'documentId':d['id'],'sourceRef':d['sourceRef'],'changed':p.text!=d['content'],
                'beforeChars':len(d['content']),'afterChars':len(p.text),'chunks':len(chunks),
                'tokens':sum(len(tokenizer.encode(c['content'],add_special_tokens=False).ids) for c in chunks),
                'events':dict(p.events),'qualityFlags':quality(d['content'])})
            output.extend(chunks)
            if n%70==0: print('PREP',variant,n,len(docs),flush=True)
        write(STAGE/(variant+'-chunks.json'),output); write(STAGE/(variant+'-packets.json'),packets)
        print('PREPARED',variant,len(output),round(time.perf_counter()-start,1),flush=True)
    write(STAGE/'stored-chunks.json',snap['chunks'])
    write(OUT/'document-statistics.json',stats); write(OUT/'chunk-parity.json',parity)


def cache_path(text, digest):
    return STAGE/'vectors'/digest/(sha(text)+'.npy')


def seed():
    snap=read(STAGE/'snapshot.json'); digest=snap['modelDigest']; chunks=snap['chunks']
    for begin in range(0,len(chunks),64):
        batch=chunks[begin:begin+64]
        if all(cache_path(c['content'],digest).exists() for c in batch): continue
        keys=[c['vectorKey'] for c in batch]
        result=subprocess.run(['docker','exec','nxz-bigorange-redis-1','redis-cli','--raw','JSON.MGET',*keys,'.'],capture_output=True,check=True,encoding='utf-8')
        rows=result.stdout.splitlines()
        if len(rows)!=len(batch): raise ValueError('Unexpected Redis response rows')
        for c,line in zip(batch,rows):
            item=json.loads(line)
            if item['content']!=c['content']: raise ValueError('Redis content mismatch')
            arr=np.asarray(item['embedding'],dtype=np.float32)
            if arr.shape!=(1024,) or not np.isfinite(arr).all(): raise ValueError('Invalid stored vector')
            path=cache_path(c['content'],digest);path.parent.mkdir(parents=True,exist_ok=True);np.save(path,arr)
        if begin%512==0: print('SEED',begin,len(chunks),flush=True)


def embed():
    snap=read(STAGE/'snapshot.json'); digest=snap['modelDigest']
    tag=next(x for x in local('/api/tags')['models'] if x['name']==MODEL)
    if tag['digest']!=digest: raise ValueError('Model revision changed')
    texts={sha(c['question']):c['question'] for c in read(OUT/'dataset.json')['cases']}
    for variant in VARIANTS:
        for c in read(STAGE/(variant+'-chunks.json')): texts[sha(c['content'])]=c['content']
    missing=[s for s in texts.values() if not cache_path(s,digest).exists()]
    print('NEED_EMBED',len(missing),'unique',len(texts),flush=True)
    for begin in range(0,len(missing),4):
        batch=missing[begin:begin+4]
        for attempt in (1,2):
            start=time.perf_counter()
            try:
                data=local('/api/embed',{'model':MODEL,'input':batch,'truncate':False,'keep_alive':-1})
                arr=np.asarray(data['embeddings'],dtype=np.float32)
                if arr.shape!=(len(batch),1024) or not np.isfinite(arr).all() or np.any(np.linalg.norm(arr,axis=1)==0): raise ValueError('Invalid local vectors')
                event={'items':len(batch),'chars':sum(map(len,batch)),'ms':round((time.perf_counter()-start)*1000,2),
                    'promptEvalCount':data.get('prompt_eval_count'),'totalDurationNs':data.get('total_duration'),'attempt':attempt,'status':'OK'}
                for s,v in zip(batch,arr):
                    path=cache_path(s,digest);path.parent.mkdir(parents=True,exist_ok=True);np.save(path,v)
                break
            except Exception as ex:
                event={'items':len(batch),'ms':round((time.perf_counter()-start)*1000,2),'attempt':attempt,'status':type(ex).__name__}
                if attempt==2: raise
            finally:
                with (OUT/'embedding-calls.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(event)+'\n')
        if begin%80==0:print('EMBEDDED',min(begin+4,len(missing)),len(missing),flush=True)


def metrics(rows):
    if not rows:return {'n':0}
    return {'n':len(rows),'sourceRecall8':sum(r['sourceRank']>0 for r in rows)/len(rows),
        'evidenceRecall8':sum(r['rank']>0 for r in rows)/len(rows),
        'MRR8':sum(1/r['rank'] if r['rank'] else 0 for r in rows)/len(rows),
        'top1':sum(r['rank']==1 for r in rows)/len(rows), 'retrievalP95Ms':float(np.percentile([r['ms'] for r in rows],95))}


def evaluate():
    snap=read(STAGE/'snapshot.json');digest=snap['modelDigest'];cases=read(OUT/'dataset.json')['cases']
    queries=np.stack([np.load(cache_path(c['question'],digest)) for c in cases]);queries/=np.linalg.norm(queries,axis=1,keepdims=True)
    summary={}; allrows={}
    for variant in ['stored']+VARIANTS:
        chunks=read(STAGE/(variant+'-chunks.json'))
        matrix=np.stack([np.load(cache_path(c['content'],digest)) for c in chunks]);matrix/=np.linalg.norm(matrix,axis=1,keepdims=True)
        rows=[]
        for ci,c in enumerate(cases):
            start=time.perf_counter();valid=np.array([i for i,ch in enumerate(chunks) if not c['types'] or ch['sourceType'] in c['types']])
            scores=matrix[valid]@queries[ci]; ordered=valid[np.argsort(-scores,kind='stable')[:8]].tolist()
            rank=0;source_rank=0
            for k,i in enumerate(ordered,1):
                if chunks[i]['sourceRef'].split('|')[0] in c['refs']:
                    if not source_rank:source_rank=k
                    if not rank and (not c['anchors'] or all(norm(a) in norm(chunks[i]['content']) for a in c['anchors'])):rank=k
            rows.append({'key':c['key'],'category':c['category'],'group':c['group'],'goldAvailable':c['goldAvailable'],
                'sourceRank':source_rank,'rank':rank,'ms':round((time.perf_counter()-start)*1000,3),
                'hits':[{'documentId':chunks[i]['documentId'],'sourceRef':chunks[i]['sourceRef'],'section':chunks[i]['section'],
                    'textSha256':sha(chunks[i]['content'])} for i in ordered]})
        summary[variant]={'allFrozen':metrics(rows),'availableGold':metrics([r for r in rows if r['goldAvailable']]),
            'byCategory':{cat:metrics([r for r in rows if r['category']==cat]) for cat in sorted({r['category'] for r in rows})},
            'githubSemantic':metrics([r for r,c in zip(rows,cases) if c['types']==['GITHUB'] and c['anchors']]),
            'chunks':len(chunks)}
        allrows[variant]=rows;write(OUT/('exact-'+variant+'.json'),rows)
        print('SCORED',variant,json.dumps(summary[variant]['githubSemantic']),flush=True)
    write(OUT/'exact-summary.json',summary)
    changes={}
    for variant in VARIANTS[1:]:
        changes[variant]=[{'key':a['key'],'beforeRank':a['rank'],'afterRank':b['rank']} for a,b in zip(allrows['raw'],allrows[variant]) if a['rank']!=b['rank']]
    write(OUT/'exact-paired-changes.json',changes)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['freeze','prepare','seed','embed','evaluate']);parser.add_argument('--variant',choices=VARIANTS);args=parser.parse_args()
    STAGE.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
    if args.phase=='prepare':prepare(args.variant)
    else:globals()[args.phase]()
