"""Print compact local review material; never calls models or reads account data."""
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-ab-20261009'
for name in sys.argv[1:]:
    for f in sorted(OUT.glob(name)):
        d=json.loads(f.read_text(encoding='utf-8')); r=d.get('response',{}).get('result',{})
        print('\n'+f.name)
        print('EVIDENCE:',d.get('response',{}).get('retrieval',{}).get('text',''))
        print('CARDS:',json.dumps([{k:c.get(k) for k in ['candidateId','name','description','prerequisites','recommendReason']} for c in r.get('recommendations',[])],ensure_ascii=False))
        print('LEARNING:',json.dumps(r.get('learning'),ensure_ascii=False))
        print('METRICS:',json.dumps(r.get('modelMetrics'),ensure_ascii=False))
        if r.get('error') or d.get('error'):print('ERROR:',r.get('error') or d.get('error'))
