"""One explicitly authorized diagnostic request; no profile/history, no automatic retry."""
import json,re,textwrap,time,urllib.request,urllib.error
from customer_local_smoke import ROOT,sql,configured_environment
out=ROOT/'docs/customer/review-recovery-v416/provider-probe.json'
if out.exists(): raise SystemExit('Already attempted; refusing to consume another call')
run='17ce6eab-2b96-4903-a0fa-ed19f8167ced'
raw=sql("SELECT detail_json FROM customer_step WHERE run_id='"+run+"' AND tool_name='searchProjects' AND JSON_UNQUOTE(JSON_EXTRACT(detail_json,'$.parameters.purpose'))='PURPOSE_REVIEW' ORDER BY id LIMIT 1")
candidates=json.loads(raw)['result']
source=(ROOT/'orange-server/src/main/java/com/orangejd/server/customer/CustomerPurpose.java').read_text(encoding='utf-8')
prompt=textwrap.dedent(source.split('REVIEW_PROMPT="""',1)[1].split('""";',1)[0]).strip()+'\n'
quote=json.loads(sql("SELECT JSON_EXTRACT(result_json,'$.state.provenance.topic.quote') FROM customer_run WHERE id='"+run+"'"))
assert quote and quote in '我想再找一下有关python的可以用于AI应用开发的求职的项目'
views=[]
def compact(v):return json.dumps(v,ensure_ascii=False,separators=(',',':'))
for c in candidates:
 text=' '.join(str(c.get(k,'')) for k in ('whatIsIt','description','catalogText'))
 text=re.sub(r'<(?:script|style)\b[^>]*>.*?</(?:script|style)>',' ',text,flags=re.I|re.S)
 text=re.sub(r'^.*(?:\[!\[|<img\b|shields\.io|badge\.svg).*$', ' ',text,flags=re.M)
 text=re.sub(r'!\[[^\]]*\]\([^\n]*?\)',' ',text,flags=re.S)
 text=re.sub(r'\[([^\]]*)\]\([^\s)]*\)',r'\1',text)
 text=re.sub(r'<[^>]+>',' ',text);text=re.sub(r'https?://\S+',' ',text)
 text=re.sub(r'[\t ]+',' ',text);text=re.sub(r'\n\s*\n+','\n',text).strip()
 views.append({'key':str(c['type'])+':'+str(c['id']),'purpose':text[:440]})
 payload={'topic':'AI应用开发','originalRequest':quote,'candidates':views}
 if len(prompt+compact(payload))>6000:views.pop();break
payload={'topic':'AI应用开发','originalRequest':quote,'candidates':views}
env=configured_environment();base=(env.get('AI_TEXT_ENDPOINT') or '').rstrip('/')
assert base in ('https://api.deepseek.com','https://api.deepseek.com/v1','https://api.deepseek.com/chat/completions')
endpoint=base if base.endswith('/chat/completions') else base+'/chat/completions'
key=env.get('AI_TEXT_KEY');assert key
body={'model':env.get('CUSTOMER_MODEL','deepseek-flash'),'temperature':0.0,'max_tokens':4096,'thinking':{'type':'enabled'},'response_format':{'type':'json_object'},'messages':[{'role':'system','content':prompt},{'role':'user','content':compact(payload)}]}
row={'scope':'one authorized reconstructed purpose review; original candidate snippets, no account profile/history','candidatesSupplied':len(views),'requestCount':1,'model':body['model'],'status':'ATTEMPT_STARTED','costUsd':'UNKNOWN'}
out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(row,ensure_ascii=False,indent=2),encoding='utf-8')
start=time.monotonic()
try:
 request=urllib.request.Request(endpoint,compact(body).encode(),{'Content-Type':'application/json','Authorization':'Bearer '+key})
 with urllib.request.urlopen(request,timeout=45) as response:result=json.load(response)
 choice=result['choices'][0];row.update(status='RECEIVED',httpStatus=200,usage=result.get('usage',{}),finishReason=choice.get('finish_reason'))
 try:row['matchesReturned']=len(json.loads(choice['message'].get('content') or '{}').get('matches',[]))
 except (ValueError,TypeError):row['invalidJson']=True
except urllib.error.HTTPError as error:
 data=error.read().decode('utf-8',errors='replace').replace(key,'[redacted]')
 # Save only a known error classification, not arbitrary reflected request content.
 row.update(status='REJECTED',httpStatus=error.code,providerClassification='CONTENT_EXISTS_RISK' if 'Content Exists Risk' in data else 'UNCLASSIFIED_PROVIDER_REJECTION')
except Exception as error:row.update(status='TRANSPORT_ERROR',errorType=type(error).__name__)
row['durationMs']=round((time.monotonic()-start)*1000)
out.write_text(json.dumps(row,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(row,ensure_ascii=False))
