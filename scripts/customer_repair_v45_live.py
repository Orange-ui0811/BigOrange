"""Post-fix replay of the same eight synthetic profiles; no analysis calls or real history."""
import argparse,json,time,sys
from pathlib import Path
import run_analysis_customer_b_compare as compare

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser();parser.add_argument('--version',choices=['v45','v46','v47','v48','v49','v410','v411'],default='v45');args=parser.parse_args()
    compare.OUT=Path(__file__).resolve().parents[1]/f'docs/customer/repair-{args.version}-live'
    compare.OUT.mkdir(parents=True,exist_ok=True)
    if not (compare.OUT/'before.json').exists():compare.save('before.json',compare.preflight())
    compare.save('cases.json',{'cases':compare.definitions(),'rounds':1,'scope':'Same synthetic profiles, repaired customer only; at most 8 runs, 48 generation attempts; no real account profile/history; no platform credits.'})
    for case in compare.definitions():
        name=case['id']+'-1-customerB.json'
        if (compare.OUT/name).exists():continue
        row=compare.customer(case,1);compare.save(name,row)
        result=row.get('result',{});calls=row.get('metrics',{}).get('modelCalls',[])
        print(json.dumps({'case':case['id'],'status':row.get('run',{}).get('status'),'result':result.get('status'),'seconds':round(row['clientWallMs']/1000,2),'projects':len(result.get('projects',[])),'references':len(result.get('references',[])),'calls':len(calls),'errors':[c.get('errorCode') for c in calls if c.get('status')=='FAILED']},ensure_ascii=False),flush=True)
    compare.save('after.json',compare.preflight())
if __name__=='__main__':main()
