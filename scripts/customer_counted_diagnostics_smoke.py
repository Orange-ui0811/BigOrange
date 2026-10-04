"""Local HTTP customer checks on a newly created synthetic session. No model/embedding call.

Only STATE/DIAGNOSE routes are submitted. Test conditions are seeded in that new
session only; existing user sessions and all project documents remain unchanged.
"""
import json
import time
import uuid
from customer_local_smoke import ROOT, sql, authorized_token
from run_customer_cases import Api, TERMINAL

api=Api('http://127.0.0.1:18080',authorized_token(5))
session=api.request('POST','/sessions',{'title':'验收 v42条件与数量（无模型调用）'})
session_id=int(session['id'])
assert sql(f"SELECT title FROM customer_session WHERE id={session_id} AND account_id=5")=='验收 v42条件与数量（无模型调用）'
state=json.dumps({'language':'Java','goal':'学习','experience':'刚学','maxDurationDays':7},ensure_ascii=False)
sql(f"UPDATE customer_session SET state_json=CONVERT(0x{state.encode().hex()} USING utf8mb4) WHERE id={session_id} AND account_id=5")
rows=[]
for message in ['当前有什么条件','为什么没有推荐项目','确认']:
    run=api.request('POST',f'/sessions/{session_id}/runs',{'requestId':str(uuid.uuid4()),'message':message})
    deadline=time.monotonic()+90
    while run['status'] not in TERMINAL and time.monotonic()<deadline:
        time.sleep(.3);run=api.request('GET','/runs/'+run['id'])
    assert run['status']=='SUCCEEDED',run.get('error_code')
    assert run['prompt_version']=='customer-v4.2-counted-alternatives'
    metrics=json.loads(run.get('metrics_json') or '{}');assert not metrics.get('modelCalls')
    result=json.loads(run['result_json']);assert result['state']['maxDurationDays']==7
    rows.append({'message':message,'status':run['status'],'result':result,'metrics':metrics})
history=api.request('GET',f'/sessions/{session_id}/messages')
timings=[m.get('run_duration_ms') for m in history['messages'] if m['role']=='assistant']
assert len(timings)==3 and all(float(ms)>=0 for ms in timings)
output={'sessionId':session_id,'scope':'New synthetic local session; real DB diagnosis; no external calls','rows':rows,'durationMs':timings}
(ROOT/'docs/customer/acceptance/customer-v42-http-diagnostics.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'sessionId':session_id,'passed':len(rows),'modelCalls':0,'durationMs':timings,'diagnostic':rows[1]['result'].get('diagnostics')},ensure_ascii=False))
