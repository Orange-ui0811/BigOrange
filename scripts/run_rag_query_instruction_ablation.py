"""Reuse cached document vectors; compare raw vs model-card query instructions."""
import sys
from datetime import datetime,timezone
from run_rag_model_chunk_experiment import (STAGE,REPORT,Provider,load_json,write_json,build,
    dataset,evaluate,digest,instructed_query)

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    corpus=load_json(STAGE/'corpus.json');cases=dataset(corpus);plans=build(corpus);provider=Provider()
    for name,model,chunks,description in plans:
        if model=='bge-m3':continue
        output=REPORT/(name+'--query-instruct.json')
        if output.exists():print('CACHED_INSTRUCT '+name,flush=True);continue
        print('START_INSTRUCT '+name,flush=True)
        # Original chunk plan has identical batches and therefore hits the local vector cache.
        vectors=provider.embed(model,[c['content'] for c in chunks],'documents')
        queries=provider.embed(model,[instructed_query(model,c['question']) for c in cases],'queries-instruct')
        result=evaluate(chunks,vectors,cases,queries)
        result.update({'plan':name+'--query-instruct','basePlan':name,'model':model,'chunks':len(chunks),
            'dimensions':vectors.shape[1],'vectorBytes':vectors.nbytes,'description':description,
            'queryInstruction':'model-card example','instructionTemplate':instructed_query(model,'{query}'),
            'experimentHash':digest([cases,chunks,model,'model-card-query-instruct-v1']),
            'at':datetime.now(timezone.utc).isoformat()})
        write_json(output,result)
        print('INSTRUCT_RESULT '+name+' '+str(result['semantic']),flush=True)

if __name__=='__main__':main()
