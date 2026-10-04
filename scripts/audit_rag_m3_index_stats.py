"""Read-only FT.INFO for platform A and active B; no credentials or text output."""
import json,subprocess,sys,re
from pathlib import Path
from export_rag_model_corpus import query
ROOT=Path(__file__).resolve().parents[1]
def main():
    active=query("SELECT JSON_OBJECT('key',generation_key,'model',embedding_model,'dimensions',embedding_dimensions) FROM rag_b_index_generation WHERE active=1")[0]
    stats={}
    for side,index in [('A','default-index'),('B','orange-rag-b-'+active['key'])]:
        raw=subprocess.run(['docker','exec','nxz-bigorange-redis-1','redis-cli','--json','FT.INFO',index],capture_output=True,text=True,check=True).stdout
        data=json.loads(re.sub(r'(?<=:)\s*(?:nan|-?inf)(?=\s*[,}])','null',raw))
        fields=data if isinstance(data,dict) else dict(zip(data[::2],data[1::2]))
        stats[side]={'index':index,**{k:fields.get(k) for k in ['num_docs','vector_index_sz_mb','total_index_memory_sz_mb','hash_indexing_failures','indexing']}}
    stats['activeB']=active;stats['aVectorCountUnchanged']=int(stats['A']['num_docs'])==5647
    (ROOT/'docs/rag-b/m3-full-corpus/index-stats.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
    print(json.dumps(stats))
    if not stats['aVectorCountUnchanged']:raise RuntimeError('A count differs from recorded baseline; investigate')
if __name__=='__main__':main()
