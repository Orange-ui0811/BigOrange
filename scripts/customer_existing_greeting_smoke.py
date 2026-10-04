"""Verify two greetings in the authorized admin's failing session; no project retrieval."""
import json
import time
import uuid
from customer_local_smoke import authorized_token, ROOT
from run_customer_cases import Api, TERMINAL

api=Api('http://127.0.0.1:18080',authorized_token(1))
session=25
before=api.request('GET',f'/sessions/{session}/messages')
if before.get('activeRun'):
    raise SystemExit('Existing session is busy; do not interrupt the user')
state=before['state']
rows=[]
for message in ('你好','你好！'):
    start=time.monotonic()
    run=api.request('POST',f'/sessions/{session}/runs',{'requestId':str(uuid.uuid4()),'message':message})
    limit=time.monotonic()+95
    while run['status'] not in TERMINAL and time.monotonic()<limit:
        time.sleep(.5)
        run=api.request('GET','/runs/'+run['id'])
    if run['status'] not in TERMINAL:
        api.request('POST','/runs/'+run['id']+'/cancel',{})
        raise SystemExit('Greeting did not reach terminal state')
    time.sleep(.2)
    run=api.request('GET','/runs/'+run['id'])
    result=json.loads(run.get('result_json') or '{}')
    row=dict(runId=run['id'],status=run['status'],error=run.get('error_code'),version=run.get('prompt_version'),result=result,metrics=json.loads(run.get('metrics_json') or '{}'),durationMs=round((time.monotonic()-start)*1000))
    rows.append(row)
    print(json.dumps({'status':row['status'],'error':row['error'],'version':row['version'],'answer':result.get('answer'),'durationMs':row['durationMs']},ensure_ascii=False),flush=True)
    if run['status']!='SUCCEEDED': break
after=api.request('GET',f'/sessions/{session}/messages')
report={'sessionId':session,'rows':rows,'conditionsPreserved':state==after['state']}
(ROOT/'docs/customer/acceptance/customer-existing-greeting.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
assert len(rows)==2 and report['conditionsPreserved']
assert all(r['status']=='SUCCEEDED' and r['version']=='customer-v3.1-chat' and r['result'].get('intent')=='CHAT' and not r['result'].get('projects') for r in rows)
