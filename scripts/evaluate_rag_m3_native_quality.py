"""Same 140 diagnostic cases against actual A knowledge pipeline and native B."""
import json,sys
from pathlib import Path
from run_rag_b_first_round import call
from evaluate_rag_m3_full_corpus import ranks,aggregate
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/rag-b/m3-full-corpus'
def main():
    sys.stdout.reconfigure(encoding='utf-8')
    path=OUT/'native-quality.json'
    cases=json.loads((ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json').read_text(encoding='utf-8'))['cases']
    rows=json.loads(path.read_text(encoding='utf-8'))['rows'] if path.exists() else []
    done={r['key'] for r in rows}
    for c in cases:
        if c['key'] in done:continue
        pair=call('POST','/admin/rag-lab/compare/platform-retrieval',{'query':c['question']},timeout=180)
        row={'key':c['key'],'category':c['category'],'result':pair}
        for side in ['a','b']:
            if side=='a':
                hits=[{'sourceRef':e['sourceRef'],'content':str(e.get('card') or e['text'])} for e in pair['a']['evidence']]
                error=pair['a']['trace']['status'] if pair['a']['trace']['status'] in ['FAILED','UNAVAILABLE','PARTIAL_FAILURE'] else None
            else:hits=pair['b']['hits'];error=None
            s,e=ranks(c,hits,'b')
            row[side]={'sourceRank':s,'evidenceRank':e,'durationMs':pair[side]['durationMs'],'error':error,'returnedEvidenceCount':len(hits)}
        rows.append(row)
        path.write_text(json.dumps({'complete':False,'rows':rows},ensure_ascii=False),encoding='utf-8')
        if len(rows)%20==0:print('NATIVE_QUALITY '+str(len(rows))+'/140',flush=True)
    result={'complete':True,'rows':rows,'semantic90':{s:aggregate([r for r in rows if r['category']!='named_regression'],s) for s in ['a','b']},
            'named50':{s:aggregate([r for r in rows if r['category']=='named_regression'],s) for s in ['a','b']},
            'limits':['Native production knowledge retrieval: A up to 3 compressed project cards; B up to 8 chunks.',
                      'Metric keys ending in 8 follow the shared summarizer; A actually emits at most 3 cards, so compare as native output hit rates.',
                      'Literal anchors are language-sensitive. Graph/candidate recommendations and final product outputs excluded.',
                      'Timings from this quality run may overlap RAGAS traffic; use separate platform-load.json for latency conclusions.']}
    path.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('NATIVE_RESULT '+json.dumps(result['semantic90']))
if __name__=='__main__':main()
