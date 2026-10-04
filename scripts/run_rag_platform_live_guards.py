"""Read-only live HTTP guards and public data readiness; never starts AI generation."""
import json,sys,time
from pathlib import Path
import requests
from run_rag_b_first_round import call,local_admin_token
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/rag-b/platform-acceptance';BASE='http://127.0.0.1:18080'
def main():
    sys.stdout.reconfigure(encoding='utf-8');OUT.mkdir(parents=True,exist_ok=True);rows=[]
    cases=[('public_graph','GET','/app/trend/graph',None,{},200),('public_pricing','GET','/app/config/ai-pricing',None,{},200),
           ('anonymous_analysis_blocked','POST','/app/trend/analyze-v2',{'topics':['Spring']},{},401),
           ('anonymous_history_blocked','GET','/app/trend/history',None,{},401),
           ('invalid_app_token_blocked','POST','/app/trend/analyze-v2',{'topics':['Spring']},{'App-Token':'invalid-acceptance-token'},401),
           ('admin_token_is_not_app_token','POST','/app/trend/analyze-v2',{'topics':['Spring']},{'App-Token':local_admin_token()},401)]
    for key,method,path,body,headers,status in cases:
        started=time.perf_counter();response=requests.request(method,BASE+path,json=body,headers=headers,timeout=30)
        row={'id':key,'httpStatus':response.status_code,'expectedHttpStatus':status,'passed':response.status_code==status,'wallMs':(time.perf_counter()-started)*1000}
        try:
            obj=response.json();row['code']=obj.get('code');row['message']=obj.get('msg')
            if key.startswith('public_'):row['passed']=row['passed'] and obj.get('code')==1
            if key=='public_graph':row['graphNodes']={k:len(obj.get('data',{}).get(k,{}).get('nodes',[])) for k in ['thisWeek','lastWeek']}
        except ValueError:pass
        rows.append(row)
    current=call('GET','/admin/rag-lab/status')
    report={'scope':'Live public API data/readiness and unauthorized boundary only. No authenticated generation, no user quota/history writes, no outbound AI.',
            'cases':rows,'bActiveIndex':current.get('activeIndex'),'aiGenerationRequests':0}
    (OUT/'live-guards.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
