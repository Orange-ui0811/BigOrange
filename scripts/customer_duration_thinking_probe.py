"""Two synthetic customer intent calls, matching the platform protocol and budgets."""
import json
import sys
import textwrap
import time
import urllib.request
import urllib.error
from customer_local_smoke import ROOT, configured_environment

env=configured_environment()
base=(env.get('AI_TEXT_ENDPOINT') or env.get('AI_TREND_ENDPOINT','')).rstrip('/')
if base not in ('https://api.deepseek.com','https://api.deepseek.com/v1','https://api.deepseek.com/chat/completions'):
    raise SystemExit('Only the configured official DeepSeek endpoint is allowed')
key=env.get('AI_TEXT_KEY') or env.get('AI_TREND_KEY')
if not key: raise SystemExit('No configured key')
endpoint=base if base.endswith('/chat/completions') else base+'/chat/completions'
source=(ROOT/'orange-server/src/main/java/com/orangejd/server/customer/CustomerState.java').read_text(encoding='utf-8-sig')
prompt=textwrap.dedent(source.split('PROMPT="""',1)[1].split('""";',1)[0]).strip()
properties={'intent':{'type':'string','enum':['CHAT','QUESTION','COLLECT','RECOMMEND','DETAIL','COMPARE']},'reply':{'type':'string'},'changes':{'type':'object'},'profileDefaults':{'type':'object'},'positions':{'type':'array','items':{'type':'integer'}}}
data={'message':'Java + 刚学 + 求职方向 + 半年内完成','state':{'language':'Java','experience':'刚学','goal':'学习','maxDurationDays':60},'profile':'','history':[],'summary':'','dialogueOnly':False}
output=ROOT/'docs/customer/acceptance/customer-duration-thinking.json'
supplement='--thinking-only' in sys.argv
rows=json.loads(output.read_text(encoding='utf-8')) if supplement and output.exists() else []
for thinking in ((True,) if supplement else (False,True)):
    body={'model':'deepseek-flash','temperature':0,'max_tokens':1500,'thinking':{'type':'enabled' if thinking else 'disabled'},'messages':[{'role':'system','content':prompt},{'role':'user','content':json.dumps(data,ensure_ascii=False)}],'tools':[{'type':'function','function':{'name':'customerAction','description':'提交经过校验的客服意图与条件变更','parameters':{'type':'object','properties':properties,'required':['intent','changes','positions']}}}],'tool_choice':{'type':'function','function':{'name':'customerAction'}}}
    start=time.monotonic()
    body['tools'][0]['function']['parameters']['required']=['intent','reply','changes','positions']
    if thinking: body.pop('tool_choice',None)
    row={'thinking':thinking,'maxTokens':1500,'scope':'synthetic intent only, no knowledge documents or account profile','protocolRevision':'compatible_auto_tools'}
    try:
        req=urllib.request.Request(endpoint,json.dumps(body,ensure_ascii=False).encode(),{'Content-Type':'application/json','Authorization':'Bearer '+key})
        with urllib.request.urlopen(req,timeout=45) as response: result=json.load(response)
        choice=result['choices'][0]; message=choice['message']; calls=message.get('tool_calls') or []
        row.update(finishReason=choice.get('finish_reason'),usage=result.get('usage'),reasoningChars=len(message.get('reasoning_content') or ''))
        content=calls[0]['function'].get('arguments','') if len(calls)==1 else message.get('content') or ''
        row['action']=json.loads(content)
        row['durationCorrect']=row['action'].get('changes',{}).get('maxDurationDays',{}).get('value')==180
    except urllib.error.HTTPError as error:
        row['errorType']='HTTPError'
        row['httpStatus']=error.code
        row['providerError']=error.read().decode('utf-8',errors='replace').replace(key,'[redacted]')[:600]
    except Exception as error:
        row['errorType']=type(error).__name__
    row['durationMs']=round((time.monotonic()-start)*1000)
    rows.append(row)
    print(json.dumps(row,ensure_ascii=False),flush=True)
output.write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
