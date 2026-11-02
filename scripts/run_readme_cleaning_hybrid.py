"""Isolated local HNSW/RRF replay; no Java business guard or generation claims."""
import re
import socket
import time
import numpy as np
from run_rag_m3_hybrid_translation_experiment import Redis
from run_readme_cleaning_experiment import STAGE, OUT, VARIANTS, read, write, sha, cache_path, norm, metrics


class ExperimentalRedis(Redis):
    def __init__(self):
        self.sock=socket.create_connection(('127.0.0.1',6381),timeout=30)
        self.file=self.sock.makefile('rb')


def info(client,index):
    items=client.command('FT.INFO',index)
    return {k.decode() if isinstance(k,bytes) else k:v for k,v in zip(items[::2],items[1::2])}


def create(client,chunks,vectors,variant):
    index='readme-exp-'+variant;prefix=index+':'
    if index.encode() in client.command('FT._LIST'):client.command('FT.DROPINDEX',index,'DD')
    client.command('FT.CREATE',index,'ON','HASH','PREFIX',1,prefix,'SCHEMA',
        'content','TEXT','title','TEXT','sourceRef','TEXT','sourceType','TAG',
        'vector','VECTOR','HNSW',10,'TYPE','FLOAT32','DIM',1024,'DISTANCE_METRIC','COSINE','M',16,'EF_CONSTRUCTION',200)
    for i,(c,v) in enumerate(zip(chunks,vectors)):
        client.command('HSET',prefix+str(i),'content',c['content'],'title',c['title'],'sourceRef',c['sourceRef'],
            'sourceType',c['sourceType'],'vector',np.asarray(v,dtype='<f4').tobytes())
    for _ in range(200):
        details=info(client,index)
        if int(details['num_docs'])==len(chunks) and float(details.get('indexing',0))==0:break
        time.sleep(.1)
    else:raise RuntimeError('Incomplete experimental index')
    return index,prefix,{k:float(details[k]) for k in ['num_docs','vector_index_sz_mb','inverted_sz_mb'] if k in details}


STOP=set('a an the is are was were to of and or in for with on by how what which can does do it its that this there'.split())


def lexical(client,index,prefix,q,types,fields):
    terms=list(dict.fromkeys(t for t in re.sub(r'[^\w-]+',' ',q,flags=re.UNICODE).split() if t.lower() not in STOP))[:12]
    if not terms:return []
    alternatives=' | '.join(t.replace('\\','\\\\').replace('-','\\-') for t in terms)
    expression='('+' | '.join('@'+f+':('+alternatives+')' for f in fields)+')'
    if types:expression='@sourceType:{'+'|'.join(types)+'} '+expression
    result=client.command('FT.SEARCH',index,expression,'NOCONTENT','LIMIT',0,128,'DIALECT',2)
    return [int(x.decode()[len(prefix):]) for x in result[1:]]


def name_match(q,c):
    for value in [c['title'],c['sourceRef']]:
        name=re.split(r'[/\\]',(value or '').split('|')[0])[-1]
        if sum(x.isalnum() for x in name)>=4 and re.search(r'(?<![a-z0-9])'+re.escape(name)+r'(?![a-z0-9])',q,re.I):return True
    return False


def select(ordered,chunks,query):
    seen=set();dedup=[];shingles={}
    for i in ordered:
        c=chunks[i];source=(c['sourceType'],c['sourceRef'].split('|')[0]);text=c['content'].replace('\r\n','\n').strip()
        if (source,text) in seen:continue
        seen.add((source,text));compact=re.sub(r'\s+','',text)
        sets={compact[j:j+5] for j in range(len(compact)-4)} if len(compact)>=120 else set()
        key=(c['sourceType'],c['sourceRef'])
        if any(sets and other and len(sets&other)/len(sets|other)>=.9 for other in shingles.get(key,[])):continue
        shingles.setdefault(key,[]).append(sets);dedup.append(i)
    q=query.lower();diverse=any(x in q for x in ['推荐','找几个','项目清单','recommend','suggest projects']) or bool(re.search(r'列出.{0,12}项目',q))
    if not diverse:return dedup[:8]
    chosen=[];sources=set()
    for i in dedup:
        c=chunks[i];key=(c['sourceType'],c['sourceRef'].split('|')[0])
        if key not in sources:chosen.append(i);sources.add(key)
        if len(chosen)==8:break
    return (chosen+[i for i in dedup if i not in chosen])[:8]


def retrieve(client,index,prefix,chunks,case,v):
    start=time.perf_counter();types=case['types'];scope='@sourceType:{'+'|'.join(types)+'}' if types else '*'
    result=client.command('FT.SEARCH',index,'('+scope+')=>[KNN 128 @vector $v EF_RUNTIME 10 AS distance]',
        'PARAMS',2,'v',np.asarray(v,dtype='<f4').tobytes(),'SORTBY','distance','LIMIT',0,128,'RETURN',1,'distance','DIALECT',2)
    vectors=[]
    for j in range(1,len(result),2):
        fields=dict(zip(result[j+1][::2],result[j+1][1::2]))
        if 1-float(fields[b'distance'])>=.55:vectors.append(int(result[j].decode()[len(prefix):]))
    vector_end=time.perf_counter()
    lex=lexical(client,index,prefix,case['question'],types,['content']);lex_end=time.perf_counter()
    names=lexical(client,index,prefix,case['question'],types,['title','sourceRef']);name_end=time.perf_counter()
    scores={}
    for items,w,channel in [(vectors,1,'v'),(lex,.2,'l'),(names,3,'n')]:
        for rank,i in enumerate(items,1):
            scores.setdefault(i,0)
            if channel!='n' or name_match(case['question'],chunks[i]):scores[i]+=w/(60+rank)
    for i in scores:
        if name_match(case['question'],chunks[i]):scores[i]+=.1
    ordered=sorted([i for i in scores if scores[i]>0],key=lambda i:-scores[i])[:80]
    selected=select(ordered,chunks,case['question'])
    end=time.perf_counter()
    return selected,{'vectorMs':(vector_end-start)*1000,'lexicalMs':(lex_end-vector_end)*1000,
        'nameMs':(name_end-lex_end)*1000,'fusionDedupMs':(end-name_end)*1000,
        'vectorCandidates':len(vectors),'lexicalCandidates':len(lex),'nameCandidates':len(names),'ms':(end-start)*1000}


def main():
    snap=read(STAGE/'snapshot.json');cases=read(OUT/'dataset.json')['cases'];digest=snap['modelDigest']
    queries=[np.load(cache_path(c['question'],digest)) for c in cases];client=ExperimentalRedis();summary={}
    for variant in ['stored']+VARIANTS:
        chunks=read(STAGE/(variant+'-chunks.json'));vectors=np.stack([np.load(cache_path(c['content'],digest)) for c in chunks])
        start=time.perf_counter();index,prefix,index_info=create(client,chunks,vectors,variant);index_info['buildMs']=(time.perf_counter()-start)*1000
        rows=[];hit_ids=[]
        # Warm the search path; model/query embedding latency is accounted separately.
        retrieve(client,index,prefix,chunks,cases[0],queries[0])
        for case,v in zip(cases,queries):
            selected,trace=retrieve(client,index,prefix,chunks,case,v);rank=0;source_rank=0
            for k,i in enumerate(selected,1):
                if chunks[i]['sourceRef'].split('|')[0] in case['refs']:
                    if not source_rank:source_rank=k
                    if not rank and (not case['anchors'] or all(norm(a) in norm(chunks[i]['content']) for a in case['anchors'])):rank=k
            rows.append({'key':case['key'],'category':case['category'],'group':case['group'],'sourceRank':source_rank,'rank':rank,
                'goldAvailable':case['goldAvailable'],**trace,'hits':[{'documentId':chunks[i]['documentId'],'sourceRef':chunks[i]['sourceRef'],
                    'section':chunks[i]['section'],'textSha256':sha(chunks[i]['content'])} for i in selected]})
            hit_ids.append({'key':case['key'],'indices':selected})
        summary[variant]={'allFrozen':metrics(rows),'historical140':metrics(rows[:140]),
            'historicalGithub30':metrics([r for r,c in zip(rows[:140],cases[:140]) if c['types']==['GITHUB'] and c['anchors']]),
            'format32':metrics(rows[140:]),'byCategory':{cat:metrics([r for r in rows if r['category']==cat]) for cat in sorted({r['category'] for r in rows})},
            'index':index_info,'chunks':len(chunks)}
        write(OUT/('hybrid-'+variant+'.json'),rows);write(STAGE/('hybrid-'+variant+'-hits.json'),hit_ids)
        print('HYBRID',variant,summary[variant]['historicalGithub30'],summary[variant]['format32'],flush=True)
        client.command('FT.DROPINDEX',index,'DD')
    write(OUT/'hybrid-summary.json',summary)


if __name__=='__main__':main()
