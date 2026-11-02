"""Local workflow-v2 acceptance. Synthetic accounts only; no email, tokens or keys in reports.

Authentication is a short-lived fixture JWT; public registration/login is NOT tested.
Every provider request is metered by the backend's shared acceptance ledger.
"""
import argparse, base64, hashlib, hmac, json, os, re, subprocess, sys, time, uuid
from urllib.error import HTTPError
from pathlib import Path
from run_customer_cases import Api, TERMINAL

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'docs/customer/workflow-v2/http-acceptance.json'
LEDGER = REPORT.with_name('provider-calls.jsonl')

def environment(container):
    data = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
    return dict(v.split('=', 1) for v in data['Config']['Env'] if '=' in v)

def sql(query):
    backend = environment('nxz-bigorange-backend-1')
    database = re.search(r':3306/([^?]+)', backend['MYSQL_URL']).group(1)
    env = dict(os.environ, MYSQL_PWD=environment('nxz-bigorange-mysql-1')['MYSQL_ROOT_PASSWORD'])
    result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'nxz-bigorange-mysql-1',
        'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '-B', '-r', database],
        input=query, capture_output=True, text=True, encoding='utf-8', env=env, timeout=30)
    if result.returncode:
        raise RuntimeError('Fixture database operation failed; credentials withheld')
    return result.stdout.strip()

def token(account):
    username, version = sql(f'SELECT username,password_version FROM account WHERE id={int(account)}').split('\t')
    if not username.startswith('workflow_v2_acceptance_'):
        raise RuntimeError('Only dedicated synthetic fixture accounts may be used')
    secret = environment('nxz-bigorange-backend-1').get('ORANGE_JWT_APP_SECRET_KEY', 'change-me-app').encode().ljust(32, b'\0')
    enc = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=')
    now = int(time.time())
    claims = dict(id=int(account), tokenType='APP', iss='orangejd', aud='APP', passwordVersion=int(version), iat=now, exp=now+3600)
    value = enc(b'{"alg":"HS256"}')+b'.'+enc(json.dumps(claims).encode())
    return 'sk-'+(value+b'.'+enc(hmac.new(secret,value,hashlib.sha256).digest())).decode()

def calls():
    return len(LEDGER.read_text(encoding='utf-8').splitlines()) if LEDGER.exists() else 0

def save(report):
    report['providerRequests'] = calls()
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')

def api(report, index=0):
    return Api('http://127.0.0.1:18080', token(report['accounts'][index]))

def initialize():
    if REPORT.exists():
        raise RuntimeError('Existing report preserved; continue its sessions instead')
    env = environment('nxz-bigorange-backend-1')
    if env.get('CUSTOMER_ACCEPTANCE_LIMIT') != '50' or not env.get('CUSTOMER_ACCEPTANCE_LEDGER','').endswith('/docs/customer/workflow-v2/provider-calls.jsonl'):
        raise RuntimeError('Backend shared 50-call budget must be configured first')
    report = dict(scope='Real HTTP + database + LangGraph4j + B index + configured providers; synthetic JWT authentication fixture, not registration/login', accounts=[], sessions={}, turns=[], probes=[], releaseApproved=False)
    for _ in range(2):
        username = 'workflow_v2_acceptance_'+uuid.uuid4().hex[:12]
        # An unusable random password prevents normal sign-in to this fixture account.
        account = int(sql(f"INSERT INTO account(username,password,status,role) VALUES('{username}','!disabled-{uuid.uuid4().hex}','ACTIVE','USER'); SELECT LAST_INSERT_ID();"))
        report['accounts'].append(account)
        save(report)
    client = api(report)
    report['capabilities'] = client.request('GET','/capabilities')
    for name in ('job','shop','legacy'):
        report['sessions'][name] = client.request('POST','/sessions',dict(title='Workflow v2 acceptance '+name))['id']
    save(report)
    print(json.dumps(dict(accounts=report['accounts'], sessions=report['sessions'], requests=calls())))

def turn(report, name, message, context=None):
    if calls() >= 45:
        raise RuntimeError('Reserve remaining budget for diagnosis; do not start another full run')
    client = api(report)
    if name not in report['sessions']:
        report['sessions'][name]=client.request('POST','/sessions',dict(title='Workflow v2 acceptance '+name))['id']
    session = report['sessions'][name]
    body = dict(requestId=str(uuid.uuid4()), message=message)
    if context: body['context']=context
    start=time.monotonic(); before=calls()
    run=client.request('POST', f'/sessions/{session}/runs', body)
    row=dict(session=name, sessionId=session, message=message, request=body, runId=run['id'], startedAt=time.strftime('%Y-%m-%dT%H:%M:%S%z'))
    report['turns'].append(row); save(report)
    deadline=time.monotonic()+100
    while run['status'] not in TERMINAL and time.monotonic()<deadline:
        time.sleep(.5); run=client.request('GET','/runs/'+run['id'])
    if run['status'] not in TERMINAL:
        client.request('POST','/runs/'+run['id']+'/cancel',{})
    row.update(status=run['status'], errorCode=run.get('error_code'), durationMs=round((time.monotonic()-start)*1000),
        result=json.loads(run.get('result_json') or '{}'), metrics=json.loads(run.get('metrics_json') or '{}'),
        state=client.request('GET',f'/sessions/{session}/messages')['state'], providerRequests=calls()-before)
    save(report)
    print(json.dumps(dict(session=name,status=row['status'],durationMs=row['durationMs'],providerRequests=row['providerRequests'],businessStatus=row['result'].get('status'),answer=row['result'].get('answer'),question=row['result'].get('pendingQuestion'),projects=[p.get('name') for p in row['result'].get('projects',[])]),ensure_ascii=False),flush=True)

def browser_check(report):
    fixture=dict(token=token(report['accounts'][0]),account=report['accounts'][0],output=str(REPORT.parent),sessionTitle='Workflow v2 acceptance '+report.get('legacyFixture',{}).get('sessionName','legacy'))
    outcome=subprocess.run(['D:/nodejs/node.exe',str(ROOT/'scripts/customer_workflow_browser_acceptance.cjs')],input=json.dumps(fixture),capture_output=True,text=True,encoding='utf-8',timeout=70)
    # Browser errors cannot print credentials: only accept the expected JSON result or a class summary.
    if outcome.returncode: result=dict(passed=False,reason=outcome.stderr.replace(fixture['token'],'[REDACTED]')[-1800:])
    else: result=json.loads(outcome.stdout)
    if 'browser' in report: report.setdefault('browserHistory',[]).append(report['browser'])
    report['browser']=result;save(report);print(json.dumps(result,ensure_ascii=False))

def probes(report):
    client=api(report); before=calls(); first=report['turns'][0]
    replay=client.request('POST',f"/sessions/{first['sessionId']}/runs",first['request'])
    report['probes'].append(dict(name='same request replays same run',passed=replay['id']==first['runId']))
    def rejected(name, expected, request):
        try:
            request(); actual=200
        except HTTPError as error: actual=error.code
        report['probes'].append(dict(name=name,expected=expected,actual=actual,passed=actual==expected))
    rejected('same request ID with altered text',409,lambda:client.request('POST',f"/sessions/{first['sessionId']}/runs",dict(first['request'],message='different input')))
    rejected('other account cannot read history',404,lambda:api(report,1).request('GET',f"/sessions/{first['sessionId']}/messages"))
    rejected('other account cannot read run',404,lambda:api(report,1).request('GET','/runs/'+first['runId']))
    rejected('stale question binding',409,lambda:client.request('POST',f"/sessions/{first['sessionId']}/runs",dict(requestId=str(uuid.uuid4()),message='是的',context=dict(questionId='q-stale-fixture'))))
    rejected('foreign task binding',409,lambda:client.request('POST',f"/sessions/{first['sessionId']}/runs",dict(requestId=str(uuid.uuid4()),message='先看看',context=dict(taskId='task-foreign-fixture'))))
    for row in report['turns']:
        for card in row.get('result',{}).get('projects',[]):
            if not card.get('importable'):
                rejected('unverified project import refused',409,lambda:client.request('POST','/runs/'+row['runId']+'/import-draft',dict(type=card['type'],projectId=card['id'])))
                break
    report['probes'].append(dict(name='boundary probes use zero provider calls',passed=calls()==before))
    save(report); print(json.dumps(report['probes'],ensure_ascii=False))

def seed_legacy(report, name='legacy'):
    if name not in report['sessions']:
        report['sessions'][name]=api(report).request('POST','/sessions',dict(title='Workflow v2 acceptance '+name))['id'];save(report)
    session=int(report['sessions'][name]);owner=int(report['accounts'][0])
    count=int(sql(f'SELECT COUNT(*) FROM customer_message WHERE session_id={session}'))
    if count: raise RuntimeError('Never overwrite a nonempty legacy fixture')
    state=dict(language='Java',goal='求职',lastCandidates=[dict(type='BEGINNER_PROJECT',id=80)],lastQuestion='旧问题不应沿用',
        provenance=dict(language=dict(source='MESSAGE',quote='只要Java项目'),goal=dict(source='PROFILE',quote='求职')))
    # Hex literals avoid SQL escaping hazards, and only the dedicated synthetic session is touched.
    payload=json.dumps(state,ensure_ascii=False).encode().hex();message='只要Java项目'.encode().hex()
    sql(f"START TRANSACTION; UPDATE customer_session SET state_json=CONVERT(0x{payload} USING utf8mb4),state_version=state_version+1 WHERE id={session} AND account_id={owner}; INSERT INTO customer_message(session_id,run_id,role,content,status) VALUES({session},'{uuid.uuid4()}','user',CONVERT(0x{message} USING utf8mb4),'COMPLETED'); COMMIT;")
    report['legacyFixture']=dict(sessionId=session,sessionName=name,sourceBackedFields=['language'],unsupportedFields=['goal'],oldCandidateId=80);save(report)

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['init','turn','probes','seed-legacy','browser']);parser.add_argument('--input',type=Path);parser.add_argument('--name',default='legacy')
    args=parser.parse_args()
    if args.action=='init': initialize()
    else:
        report=json.loads(REPORT.read_text(encoding='utf-8'))
        if args.action=='probes': probes(report)
        elif args.action=='seed-legacy': seed_legacy(report,args.name)
        elif args.action=='browser': browser_check(report)
        else: turn(report,**json.loads(args.input.read_text(encoding='utf-8-sig')))
