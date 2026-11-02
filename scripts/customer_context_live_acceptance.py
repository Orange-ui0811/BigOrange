"""P3 live continuation with explicit 40-attempt guard. All seeded history is synthetic."""
import argparse,datetime,hashlib,json,time,uuid
from pathlib import Path
from customer_workflow_http_acceptance import api,sql,environment
from run_customer_cases import TERMINAL

ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'docs/customer/context-v3'
REPORT=FOLDER/'live-http-acceptance.json'
LEDGER=FOLDER/'live-provider-calls.jsonl'

def calls():return len(LEDGER.read_text(encoding='utf-8').splitlines()) if LEDGER.exists() else 0
def save(report):
    report['providerAttempts']=calls();report['remaining']=40-calls()
    REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def literal(value):return 'CONVERT(0x'+str(value).encode().hex()+' USING utf8mb4)'

def initialize():
    if REPORT.exists():raise RuntimeError('Existing round preserved; continue instead')
    config=environment('nxz-bigorange-backend-1')
    assert config.get('CUSTOMER_ACCEPTANCE_LIMIT')=='40' and config.get('CUSTOMER_ACCEPTANCE_LEDGER','').endswith('/context-v3/live-provider-calls.jsonl')
    fixture=json.loads((ROOT/'docs/customer/workflow-v2/http-acceptance.json').read_text(encoding='utf-8-sig'))
    report=dict(accounts=fixture['accounts'],sessions={},turns=[],probes=[],authorization='User explicitly authorized up to 40 NEW actual outbound attempts; all failed/retried/model/embedding/summary calls count',limit=40,releaseApproved=False,startedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    assert int(sql('SELECT COUNT(*) FROM customer_run WHERE status IN (\'QUEUED\',\'RUNNING\');'))==0
    for name in ['short','memory','tasks','recommend','inventory']:
        report['sessions'][name]=api(report).request('POST','/sessions',{'title':'P3 live acceptance '+name})['id'];save(report)
    print(json.dumps({'sessions':report['sessions'],'attempts':calls()},ensure_ascii=False))

def seed_memory(report):
    session=int(report['sessions']['memory']);owner=int(report['accounts'][0]);task_id='task-'+str(uuid.uuid4())
    assert int(sql(f'SELECT COUNT(*) FROM customer_message WHERE session_id={session};'))==0
    sources=[]
    for i in range(60):
        run=str(uuid.uuid4())
        if i==0:user='只找Java项目，必须离线运行，最多两周；只记录，暂不推荐。'
        elif i==1:user='周期改成四周，Java和必须离线这两项要求不变。'
        elif i==2:user='我给这次讨论起的代号是蓝鲸书签。这只是讨论代号，不是软件功能要求。'
        else:user=f'第{i+1}轮技术插问：解释一下Java集合的常见概念。本轮只复习概念，不修改项目要求，也不请求查找或导入项目。'
        answer={'status':'CONVERSATION','answer':'这是合成历史中的概念解释。已确认要求保持不变，项目尚未推荐，也没有执行导入。普通技术讨论不能变成新的项目条件。'}
        query=f"""START TRANSACTION;
INSERT INTO customer_run(id,session_id,account_id,request_id,input_hash,message,status,stage,prompt_version,deadline_at,finished_at,result_json) VALUES('{run}',{session},{owner},'{uuid.uuid4()}','{'0'*64}',{literal(user)},'SUCCEEDED','SYNTHETIC_HISTORY','SYNTHETIC_P3_FIXTURE',NOW(),NOW(),{literal(json.dumps(answer,ensure_ascii=False))});
INSERT INTO customer_message(session_id,run_id,role,content,status) VALUES({session},'{run}','user',{literal(user)},'COMPLETED'); SET @source_message=LAST_INSERT_ID();
INSERT INTO customer_message(session_id,run_id,role,content,status) VALUES({session},'{run}','assistant',{literal(json.dumps(answer,ensure_ascii=False))},'COMPLETED');
INSERT INTO customer_context_run(run_id,session_id,task_id,requirement_version) VALUES('{run}',{session},'{task_id}',3);
COMMIT; SELECT @source_message;"""
        mid=int(sql(query).splitlines()[-1]);sources.append({'messageId':mid,'runId':run,'message':user})
    reqs=[]
    for field,text,value,index in [('language','只找Java项目','Java',0),('open','必须离线运行','必须离线运行',0),('maxDurationDays','周期改成四周',28,1)]:
        reqs.append(dict(id='req-'+str(uuid.uuid4()),taskId=task_id,revision=1,field=field,kind='FEATURE',text=text,normalizedValue=value,strength='HARD',assertion='EXPLICIT',status='ACTIVE',verification='SEMANTIC' if field=='open' else 'STRUCTURED',source=dict(messageId=str(sources[index]['messageId']),quote=text,type='MESSAGE')))
    task=dict(id=task_id,createdRunId=sources[0]['runId'],status='DISCOVERING',requirementVersion=3,requirements=reqs,questions=[],lists=[],events=[])
    state=dict(language='Java',maxDurationDays=28,workflowV2=dict(schemaVersion=2,sessionRevision=0,task=task,activeTaskId=task_id,archivedTasks=[]),provenance=dict(language=dict(source='MESSAGE',quote='只找Java项目'),maxDurationDays=dict(source='MESSAGE',quote='周期改成四周')))
    sql(f"UPDATE customer_session SET state_json={literal(json.dumps(state,ensure_ascii=False))},state_version=state_version+1 WHERE id={session} AND account_id={owner};")
    report['memoryFixture']=dict(kind='SYNTHETIC_HISTORY_REAL_CONTINUATION',turns=60,taskId=task_id,sources=sources[:3],expected={'language':'Java','maxDurationDays':28,'hardOpen':'必须离线运行','historicalNickname':'蓝鲸书签'})
    save(report);print(json.dumps({'syntheticTurns':60,'session':session,'taskId':task_id},ensure_ascii=False))

def turn(report,name,message,binding=None,case=None):
    if calls()>=38:raise RuntimeError('Remaining budget reserved; no new turn started')
    session=report['sessions'][name];client=api(report);body=dict(requestId=str(uuid.uuid4()),message=message)
    if binding:body['context']=binding
    started=time.monotonic();before=calls();run=client.request('POST',f'/sessions/{session}/runs',body)
    row=dict(case=case,session=name,sessionId=session,runId=run['id'],request=body);report['turns'].append(row);save(report)
    deadline=time.monotonic()+100
    while run['status'] not in TERMINAL and time.monotonic()<deadline:
        time.sleep(.3);run=client.request('GET','/runs/'+run['id'])
    if run['status'] not in TERMINAL:client.request('POST','/runs/'+run['id']+'/cancel',{})
    result=json.loads(run.get('result_json') or '{}');history=client.request('GET',f'/sessions/{session}/messages')
    row.update(status=run['status'],errorCode=run.get('error_code'),result=result,state=history['state'],metrics=json.loads(run.get('metrics_json') or '{}'),durationMs=round((time.monotonic()-started)*1000),providerAttempts=calls()-before)
    details=sql(f"SELECT tool_name,status,HEX(detail_json) FROM customer_step WHERE run_id='{run['id']}' ORDER BY id;")
    steps=[]
    for line in details.splitlines():
        tool,status,encoded=line.split('\t',2)
        steps.append(dict(tool=tool,status=status,details=json.loads(bytes.fromhex(encoded).decode())))
    row['steps']=steps;save(report)
    print(json.dumps(dict(case=case,session=name,status=row['status'],errorCode=row['errorCode'],businessStatus=result.get('status'),answer=result.get('answer'),question=result.get('pendingQuestion'),projects=[p.get('name') for p in result.get('projects',[])],calls=row['providerAttempts'],totalCalls=calls(),durationMs=row['durationMs']),ensure_ascii=False),flush=True)

if __name__=='__main__':
    import sys;sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['init','seed-memory','turn']);parser.add_argument('--input',type=Path);args=parser.parse_args()
    if args.action=='init':initialize()
    else:
        report=json.loads(REPORT.read_text(encoding='utf-8'))
        if args.action=='seed-memory':seed_memory(report)
        else:turn(report,**json.loads(args.input.read_text(encoding='utf-8-sig')))
