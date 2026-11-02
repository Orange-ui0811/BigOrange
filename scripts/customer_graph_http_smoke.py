"""Local authenticated protocol smoke, deterministic STATE branch; no model/evidence egress."""
import argparse, json, time, urllib.request, urllib.error
from pathlib import Path
from customer_local_smoke import authorized_token, configured_environment, sql, ROOT
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',default='docs/customer/langgraph4j/http-smoke.json')
    options=parser.parse_args()
    output=(ROOT/options.output).resolve()
    if not output.is_relative_to(ROOT/'docs/customer'):raise ValueError('Report must stay in customer documentation')
    token=authorized_token(5);endpoint='http://127.0.0.1:18080';report={'realModelCalls':0,'releaseApproved':False}
    def call(path,data=None,method=None,auth=token,admin=False):
        req=urllib.request.Request(endpoint+path,data=None if data is None else json.dumps(data).encode(),method=method,headers={'Content-Type':'application/json',('Admin-Token' if admin else 'App-Token'):auth})
        with urllib.request.urlopen(req,timeout=20) as response:return json.load(response)
    session=call('/app/customer/sessions',{'title':'LangGraph 接入协议验收'})['data']['id']
    request='graph-smoke-'+str(int(time.time()))
    run=call(f'/app/customer/sessions/{session}/runs',{'requestId':request,'message':'当前有什么条件'})['data']['id']
    for _ in range(60):
        row=call('/app/customer/runs/'+run)['data']
        if row['status'] not in ('QUEUED','RUNNING'):break
        time.sleep(.5)
    replay=call(f'/app/customer/sessions/{session}/runs',{'requestId':request,'message':'当前有什么条件'})
    report.update(runId=run,status=row['status'],replaySame=replay['data']['id']==run)
    try:call('/admin/customer/runs',auth=token,admin=True);report['appTokenRejectedByAdmin']=False
    except urllib.error.HTTPError as ex:report['appTokenRejectedByAdmin']=ex.code in (401,403)
    env=configured_environment();password=env.get('ORANGE_ADMIN_BOOTSTRAP_PASSWORD')
    report['adminLogin']='NO_CONFIGURED_CREDENTIAL'
    if password:
        try:
            logged=call('/admin/auth/login',{'username':'admin','password':password},auth='',admin=True)
            admin_token=(logged.get('data') or {}).get('token')
            if admin_token:
                diag=call('/admin/customer/runs/'+run+'/diagnostics',auth=admin_token,admin=True)['data']
                report.update(adminLogin='PASSED',engine=diag.get('execution_engine'),nodeCount=len(diag.get('nodes',[])))
        except Exception:report['adminLogin']='FAILED'
    report['generation']=sql('SELECT generation_key,embedding_model,embedding_dimensions,chunk_policy,adapter_type,document_count,chunk_count FROM rag_b_index_generation WHERE active=1')
    report['schema']=sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1')
    report['modelConfig']={k:env.get(k) for k in ('CUSTOMER_MODEL','CUSTOMER_THINKING','CUSTOMER_NATIVE_ACTIONS')}
    call(f'/app/customer/sessions/{session}',method='DELETE')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
    assert report['status']=='SUCCEEDED' and report['replaySame'] and report['appTokenRejectedByAdmin'], 'HTTP smoke failed'
if __name__=='__main__':main()
