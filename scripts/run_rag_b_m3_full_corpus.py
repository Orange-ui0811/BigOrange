"""Upgrade B generation, import every nonempty legacy source, audit readiness. A stays intact."""
import json,time,sys
from pathlib import Path
from export_rag_model_corpus import query
from run_rag_b_first_round import call
from run_rag_b_full_original_validation import ready

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/m3-full-corpus'
def write(name,data): (OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def read(name): return json.loads((OUT/name).read_text(encoding='utf-8'))

def main():
    sys.stdout.reconfigure(encoding='utf-8'); OUT.mkdir(parents=True,exist_ok=True); ready()
    active=query("SELECT JSON_OBJECT('id',id,'model',embedding_model,'dimension',embedding_dimensions,'policy',chunk_policy) FROM rag_b_index_generation WHERE active=1")
    if active[0]['model']!='bge-m3':
        if (OUT/'rebuild.json').exists(): build=read('rebuild.json')
        else:
            build=call('POST','/admin/rag-lab/generations/rebuild?adapter=HNSW_HYBRID'); write('rebuild.json',build)
        deadline=time.time()+7200
        while time.time()<deadline:
            states=query("SELECT JSON_OBJECT('status',status,'documents',document_count,'chunks',chunk_count,'error',last_error) FROM rag_b_index_generation WHERE id="+str(build['generationId']))
            print('REBUILD '+json.dumps(states,ensure_ascii=False),flush=True)
            if states[0]['status']=='ACTIVE': break
            if states[0]['status']=='FAILED': raise RuntimeError(states[0]['error'])
            time.sleep(20)
        else: raise RuntimeError('Rebuild timeout')
    path=OUT/'import-manifest.json'
    if path.exists(): manifest=read(path.name)
    else:
        docs=query("""SELECT JSON_OBJECT('id',id,'source',source,'sourceRef',source_ref,
            'inputField',IF(original_content IS NOT NULL AND TRIM(original_content)<>'','original_content','content'),
            'hash',SHA2(REPLACE(REPLACE(IF(original_content IS NOT NULL AND TRIM(original_content)<>'',original_content,content),CONCAT(CHAR(13),CHAR(10)),CHAR(10)),CHAR(13),CHAR(10)),256))
            FROM knowledge_document WHERE (original_content IS NOT NULL AND TRIM(original_content)<>'') OR (content IS NOT NULL AND TRIM(content)<>'') ORDER BY id""")
        manifest={'documents':docs,'results':{}}; write(path.name,manifest)
    for i,doc in enumerate(manifest['documents']):
        key=str(doc['id'])
        if key in manifest['results'] and 'error' not in manifest['results'][key]:continue
        try: result=call('POST',f"/admin/rag-lab/imports/legacy/{doc['id']}",timeout=180)
        except Exception as e:result={'error':str(e)[:400]}
        manifest['results'][key]=result; write(path.name,manifest)
        if i%25==0:print(f'IMPORT {i+1}/{len(manifest["documents"])}',flush=True)
    errors=[k for k,v in manifest['results'].items() if 'error' in v]
    if errors: raise RuntimeError('Import submission errors: '+str(errors))
    ids=','.join(str(i) for i in sorted({r['documentId'] for r in manifest['results'].values()}))
    deadline=time.time()+7200
    while time.time()<deadline:
        states=query("SELECT JSON_OBJECT('status',status,'n',COUNT(*)) FROM rag_b_document WHERE id IN ("+ids+") GROUP BY status")
        print('INDEX '+json.dumps(states),flush=True)
        if all(s['status'] in ['READY','FAILED'] for s in states):break
        time.sleep(20)
    else:raise RuntimeError('Index timeout')
    audit=query("SELECT JSON_OBJECT('id',d.id,'source',d.source_type,'sourceRef',d.source_ref,'status',d.status,'hash',v.content_hash,'chunks',v.chunk_count,'error',d.last_error) FROM rag_b_document d LEFT JOIN rag_b_document_version v ON v.id=d.active_version_id WHERE d.id IN ("+ids+") ORDER BY d.id")
    byid={str(r['id']):r for r in audit}; failures=[]
    for doc in manifest['documents']:
        imported=byid[str(manifest['results'][str(doc['id'])]['documentId'])]
        if imported['status']!='READY' or imported['hash']!=doc['hash']:failures.append({'legacyId':doc['id'],'imported':imported})
    generations=query("SELECT JSON_OBJECT('id',id,'key',generation_key,'model',embedding_model,'dimensions',embedding_dimensions,'policy',chunk_policy,'status',status,'active',active) FROM rag_b_index_generation ORDER BY id")
    write('corpus-audit.json',{'legacyInputCount':len(manifest['documents']),
        'originalContentCount':sum(d['inputField']=='original_content' for d in manifest['documents']),
        'fallbackContentCount':sum(d['inputField']=='content' for d in manifest['documents']),
        'importedUniqueDocuments':len(audit),'activeChunks':sum(r['chunks'] or 0 for r in audit),
        'failures':failures,'documents':audit,'generations':generations})
    if failures:raise RuntimeError('Corpus audit failed')
    print('FULL_CORPUS_READY '+str(len(audit)),flush=True)

if __name__=='__main__':main()
