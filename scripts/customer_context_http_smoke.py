"""Real local HTTP/context wiring using deterministic routes; no model invocation."""
import hashlib
import json
import time
import uuid
from pathlib import Path
from customer_workflow_http_acceptance import api, sql

ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'docs/customer/context-v3'
LEDGER=ROOT/'docs/customer/workflow-v2/provider-calls.jsonl'

def main():
    before=hashlib.sha256(LEDGER.read_bytes()).hexdigest()
    fixture=json.loads((ROOT/'docs/customer/workflow-v2/http-acceptance.json').read_text(encoding='utf-8-sig'))
    client=api(fixture)
    session=client.request('POST','/sessions',{'title':'第三阶段无模型上下文冒烟'})
    report={'scope':'Real HTTP, MySQL, LangGraph4j; deterministic STATE routes; synthetic fixture authentication, not login testing',
        'sessionId':session['id'],'turns':[],'modelRequests':0,'passed':False}
    for message in ['当前有什么条件？','之前讨论过的要求，当前有什么条件？']:
        run=client.request('POST',f"/sessions/{session['id']}/runs",{'requestId':str(uuid.uuid4()),'message':message})
        deadline=time.monotonic()+30
        while run['status'] in {'QUEUED','RUNNING'} and time.monotonic()<deadline:
            time.sleep(.2)
            run=client.request('GET','/runs/'+run['id'])
        result=json.loads(run.get('result_json') or '{}')
        report['turns'].append({'runId':run['id'],'protocolStatus':run['status'],'errorCode':run.get('error_code'),'businessStatus':result.get('status')})
        if run['status']!='SUCCEEDED' or result.get('status')!='CONFIRMED_CONDITIONS':
            (FOLDER/'http-smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
            raise RuntimeError('Context smoke did not complete; report preserved')
    session_id=int(session['id'])
    report['taskBindings']=int(sql(f'SELECT COUNT(*) FROM customer_context_run WHERE session_id={session_id};'))
    report['contextManifests']=int(sql(f"SELECT COUNT(*) FROM customer_step s JOIN customer_run r ON r.id=s.run_id WHERE r.session_id={session_id} AND s.tool_name='contextManifest';"))
    report['historyReads']=int(sql(f"SELECT COUNT(*) FROM customer_step s JOIN customer_run r ON r.id=s.run_id WHERE r.session_id={session_id} AND s.tool_name='searchConversationHistory' AND s.status='SUCCEEDED';"))
    after=hashlib.sha256(LEDGER.read_bytes()).hexdigest()
    report['ledgerUnchanged']=before==after
    report['passed']=report['taskBindings']==2 and report['contextManifests']==2 and report['historyReads']==1 and report['ledgerUnchanged']
    (FOLDER/'http-smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
    assert report['passed']

if __name__=='__main__':main()
