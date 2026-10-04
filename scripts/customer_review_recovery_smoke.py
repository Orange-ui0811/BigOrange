"""Local authenticated regression with a NEW synthetic session; zero provider calls."""
import argparse,json,time,uuid
parser=argparse.ArgumentParser();parser.add_argument("--output",default="docs/customer/review-recovery-v416/http-smoke.json");args=parser.parse_args()
from customer_local_smoke import ROOT,authorized_token,sql
from run_customer_cases import Api,TERMINAL
api=Api('http://127.0.0.1:18080',authorized_token(1))
session=api.request('POST','/sessions',{'title':'验收 v416 核对失败状态恢复'})
sid=int(session['id']);rows=[]
try:
 state={'language':'Python','goal':'求职','topic':['AI应用开发'],'purposeReview':{'topic':'AI应用开发','proofs':{}}}
 # Seed only the just-created test session, never an existing user conversation.
 encoded=json.dumps(state,ensure_ascii=False).encode().hex()
 sql(f"UPDATE customer_session SET state_json=CONVERT(0x{encoded} USING utf8mb4) WHERE id={sid} AND account_id=1 AND title='验收 v416 核对失败状态恢复'")
 for message,expected in [('用途核对是什么','PROCESS_EXPLANATION'),('为什么没有项目推荐','RELEVANCE_UNAVAILABLE'),('当前有什么条件','CONFIRMED_CONDITIONS')]:
  request={'requestId':str(uuid.uuid4()),'message':message}
  start=time.monotonic();run=api.request('POST',f'/sessions/{sid}/runs',request)
  while run['status'] not in TERMINAL and time.monotonic()-start<15:
   time.sleep(.3);run=api.request('GET','/runs/'+run['id'])
  assert run['status']=='SUCCEEDED',run['status']
  result=json.loads(run['result_json']);metrics=json.loads(run.get('metrics_json') or '{}')
  assert result['status']==expected,result['status']
  assert metrics.get('modelCalls',[])==[],'Unexpected outbound model call'
  assert result['state']['language']=='Python' and result['state']['goal']=='求职'
  assert result['state']['topic']==['AI应用开发']
  assert '没有找到符合' not in result['answer']
  replay=api.request('POST',f'/sessions/{sid}/runs',request);assert replay['id']==run['id']
  rows.append({'message':message,'status':result['status'],'answer':result['answer'],'durationMs':round((time.monotonic()-start)*1000),'modelCalls':0,'idempotencyPassed':True,'promptVersion':run.get('prompt_version')})
 history=api.request('GET',f'/sessions/{sid}/messages');assert len(history['messages'])==6
 output={'scope':'new synthetic session only; legacy failure fixture; local API, no provider calls','turns':rows,'historyMessages':6,'passed':True}
 path=ROOT/args.output;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({'passed':True,'turns':len(rows),'modelCalls':0,'version':rows[0]['promptVersion']},ensure_ascii=False))
finally:api.request('DELETE',f'/sessions/{sid}')
