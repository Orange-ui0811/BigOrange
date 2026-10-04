"""Independent Python tokenizer/UTF-16 offset audit of active B; no external calls."""
import json,sys,collections
from pathlib import Path
import numpy as np
from tokenizers import Tokenizer
from export_rag_model_corpus import query
ROOT=Path(__file__).resolve().parents[1]

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    generation=query("SELECT JSON_OBJECT('id',id,'model',embedding_model,'dimensions',embedding_dimensions,'policy',chunk_policy) FROM rag_b_index_generation WHERE active=1")[0]
    if generation['model']!='bge-m3':raise RuntimeError('M3 generation required')
    docs=query("SELECT JSON_OBJECT('id',d.id,'content',v.original_content,'chunks',v.chunk_count) FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id WHERE d.status='READY' ORDER BY d.id")
    chunks=query("SELECT JSON_OBJECT('id',c.id,'doc',c.document_id,'start',c.start_offset,'end',c.end_offset,'content',c.content,'section',c.section_path) FROM rag_b_chunk c JOIN rag_b_document d ON d.active_version_id=c.version_id WHERE d.status='READY' ORDER BY c.document_id,c.chunk_index")
    tokenizer=Tokenizer.from_file(str(ROOT/'orange-server/src/main/resources/rag-b/tokenizers/bge-m3.json'))
    tokenizer.no_truncation();tokenizer.no_padding()
    groups=collections.defaultdict(list)
    for c in chunks:groups[c['doc']].append(c)
    failures=[];lengths=[];overlaps=[];sectioned=0
    for d in docs:
        raw=d['content'].encode('utf-16-le');covered=0;previous=None
        for c in groups[d['id']]:
            n=len(tokenizer.encode(c['content'],add_special_tokens=False).ids);lengths.append(n)
            sectioned+=bool(c['section'])
            if n>768:failures.append({'chunk':c['id'],'reason':'TOKEN_CAP','tokens':n})
            try:substring=raw[c['start']*2:c['end']*2].decode('utf-16-le')
            except UnicodeDecodeError:substring=None
            if substring!=c['content']:failures.append({'chunk':c['id'],'reason':'OFFSET_CONTENT'})
            if c['start']>covered:failures.append({'chunk':c['id'],'reason':'COVERAGE_GAP'})
            if previous and c['start']<previous['end']:
                overlap=raw[c['start']*2:previous['end']*2].decode('utf-16-le')
                nt=len(tokenizer.encode(overlap,add_special_tokens=False).ids);overlaps.append(nt)
                if nt>64 or c['section']!=previous['section']:failures.append({'chunk':c['id'],'reason':'OVERLAP_BOUNDARY','tokens':nt})
            covered=max(covered,c['end']);previous=c
        if covered*2!=len(raw) or len(groups[d['id']])!=d['chunks']:failures.append({'document':d['id'],'reason':'INCOMPLETE_COVERAGE_OR_COUNT'})
    result={'generation':generation,'documents':len(docs),'chunks':len(chunks),'sectionedChunks':sectioned,
            'tokenLength':{'min':min(lengths),'median':float(np.median(lengths)),'p95':float(np.percentile(lengths,95)),'max':max(lengths),'sum':sum(lengths)},
            'overlapCount':len(overlaps),'maxOverlapTokens':max(overlaps,default=0),'failures':failures,
            'scope':'Independent tokenizer, all active READY chunks; UTF-16 offsets; no embedding billing usage inference.'}
    path=ROOT/'docs/rag-b/m3-full-corpus/chunk-audit.json';path.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='failures'},ensure_ascii=False))
    if failures:raise RuntimeError('Chunk audit failed: '+str(len(failures)))

if __name__=='__main__':main()
