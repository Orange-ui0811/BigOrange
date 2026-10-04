"""Two authorized account-5 dialogue turns; no project retrieval or document export."""
import json
from customer_local_smoke import authorized_token, ROOT
from run_customer_cases import Api

api=Api('http://127.0.0.1:18080',authorized_token(5))
caps=api.request('GET','/capabilities')
if not caps.get('enabled') or caps.get('model')!='deepseek-flash':
    raise SystemExit('Updated customer runtime is not ready')
row=api.case({'id':'CHAT_V3_GREETING','category':'DIALOGUE','messages':['你好','今天有点累，不知道该聊什么。先不要推荐具体项目，随便聊聊吧。'], 'expected':'Model greeting and natural off-topic guidance, confirmed profile read, no knowledge search'},1)
for turn in row['turns']:
    run=api.request('GET','/runs/'+turn['runId'])
    turn['metrics']=json.loads(run.get('metrics_json') or '{}')
    print(json.dumps({'status':turn['status'],'error':turn['errorCode'],'answer':turn['result'].get('answer'),'profileLoaded':turn['result'].get('profileLoaded'),'intent':turn['result'].get('intent'),'tokenUsage':turn['metrics'].get('tokenUsage')},ensure_ascii=False),flush=True)
(ROOT/'docs/customer/acceptance/customer-v3-dialogue.json').write_text(json.dumps(row,ensure_ascii=False,indent=2),encoding='utf-8')
if any(t['status']!='SUCCEEDED' or t['result'].get('intent')!='CHAT' for t in row['turns']):
    raise SystemExit('Dialogue smoke failed')
