"""Review stored live traces plus read-only DB invariants; no model requests."""
import collections,hashlib,json,statistics
from pathlib import Path
from urllib.error import HTTPError
from customer_workflow_http_acceptance import api,sql
from customer_context_live_acceptance import FOLDER,REPORT,LEDGER,calls,save

def main():
    report=json.loads(REPORT.read_text(encoding='utf-8'));turns=report['turns'];checks=[]
    def check(name,value,evidence=None):checks.append(dict(name=name,passed=bool(value),evidence=evidence))
    by_case=lambda case:[t for t in turns if t['case']==case]
    short=by_case('LIVE-01')[0]
    check('short conversation does not create requirements',not short['state']['workflowV2']['task']['requirements'],short['runId'])
    memory=by_case('LIVE-02');expected=report['memoryFixture']['expected']
    check('window-external nickname recovered',memory[0]['result']['answer'].strip()=='蓝鲸书签。',memory[0]['runId'])
    check('initial long-history confirmed conditions retained',memory[0]['state'].get('language')=='Java' and memory[0]['state'].get('maxDurationDays')==28)
    check('correction becomes21days and retains offline',memory[1]['state'].get('maxDurationDays')==21 and any(q['status']=='ACTIVE' and '离线' in q['text'] for q in memory[1]['state']['workflowV2']['task']['requirements']))
    technical=by_case('LIVE-03')[0]
    check('technical interruption preserves conditions',technical['state'].get('language')=='Java' and technical['state'].get('maxDurationDays')==21)
    current=by_case('LIVE-02-REQUIREMENTS')[0]
    check('latest requirements outrank old summary',all(x in current['result']['answer'] for x in ['Java','21天','必须离线运行']))
    semantic=by_case('LIVE-02-SEMANTIC')[0]
    check('nickname remains available without explicit historical keyword',semantic['result']['answer'].strip()=='蓝鲸书签。')
    retest=by_case('LIVE-04-RETEST');states=[t['state'] for t in retest];tasks=[s['workflowV2']['task'] for s in states]
    check('task switch/resume real retest',len(tasks)==3 and tasks[0]['id']!=tasks[1]['id'] and tasks[0]['id']==tasks[2]['id'] and [s.get('language') for s in states]==['Java','Python','Java'])
    check('old offline requirement does not leak into newtask',not any(q['status']=='ACTIVE' and '离线' in q['text'] for q in tasks[1]['requirements']))
    ambiguous=by_case('LIVE-05')[0]
    check('ambiguous historical selection asks instead of selecting',ambiguous['result'].get('status')=='FOLLOW_UP' and not ambiguous['result'].get('projects') and not ambiguous['state']['workflowV2']['task'].get('selectedProject'))
    strict=by_case('LIVE-07')[-1];phases=strict['metrics'].get('modelCalls',[])
    check('strict real full run excludes unknown',strict['status']=='SUCCEEDED' and strict['result'].get('status')=='NO_VERIFIED_MATCH' and not strict['result'].get('projects') and any(m.get('phase')=='REQUIREMENT_MATCH' and m.get('status')=='SUCCEEDED' for m in phases) and all(m.get('status')=='SUCCEEDED' for m in phases),strict['runId'])
    inventory=by_case('LIVE-08')[0];coverage=next(s['details'] for s in inventory['steps'] if s['tool']=='purposeCoverage')
    check('purpose pool coverage tracked without provider failure',coverage.get('candidateCount')==12 and coverage.get('pendingCount')==0 and inventory['status']=='SUCCEEDED',coverage)
    # Validate every stored segment against the exact raw archive; hashes do not certify semantic truth.
    session=int(report['sessions']['memory']);segments=[]
    for line in sql(f'SELECT task_id,source_ids_json,source_hash,HEX(summary_text) FROM customer_context_summary WHERE session_id={session} ORDER BY from_message_id').splitlines():
        task,ids_json,expected_hash,summary_hex=line.split('\t',3);ids=json.loads(ids_json)['ids'];assert all(isinstance(mid,int) for mid in ids)
        rows=sql(f"SELECT m.id,m.run_id,m.role,c.task_id,HEX(m.content) FROM customer_message m JOIN customer_context_run c ON c.run_id=m.run_id WHERE m.session_id={session} AND m.id IN ({','.join(map(str,ids))}) ORDER BY m.id")
        digest='context-source-v1';actual=[]
        for row in rows.splitlines():
            mid,run,role,source_task,encoded=row.split('\t',4);actual.append(int(mid));raw=bytes.fromhex(encoded)
            digest+='|'+mid+'|'+run+'|'+role+'|'+source_task+'|'+hashlib.sha256(raw).hexdigest()
        valid=actual==ids and hashlib.sha256(digest.encode()).hexdigest()==expected_hash
        segments.append(dict(sourceMessageIds=ids,sourceHashValid=valid,summary=bytes.fromhex(summary_hex).decode()))
    check('actual generated summaries have valid exact raw sources',bool(segments) and all(s['sourceHashValid'] for s in segments),len(segments))
    # HTTP permission/idempotency probes must not consume quota.
    before=calls();client=api(report);other=api(report,1);session=int(short['sessionId'])
    replay=client.request('POST',f'/sessions/{session}/runs',short['request']);check('same request replays same run',replay['id']==short['runId'])
    def reject(name,expected,fn):
        try:fn();actual=200
        except HTTPError as error:actual=error.code
        check(name,actual==expected,dict(expected=expected,actual=actual))
    reject('altered replay rejected',409,lambda:client.request('POST',f'/sessions/{session}/runs',dict(short['request'],message='changed fixture request')))
    reject('foreign account cannot read history',404,lambda:other.request('GET',f'/sessions/{session}/messages'))
    reject('foreign account cannot read run',404,lambda:other.request('GET','/runs/'+short['runId']))
    reject('foreign task binding rejected',409,lambda:client.request('POST',f'/sessions/{session}/runs',dict(requestId='boundary-'+__import__('uuid').uuid4().hex,message='先看看',context=dict(taskId='task-not-owned-by-this-session'))))
    check('HTTP boundary probes consume no provider attempts',calls()==before)
    old=Path('docs/customer/workflow-v2/provider-calls.jsonl').read_bytes()
    check('old50-entry ledger unchanged',len(old.splitlines())==50 and hashlib.sha256(old).hexdigest()=='7d9f44cb5a1fb7e210dec11add09aaf2e360f02c0f64b1f765f3a09d48301f97')
    if not any(f['id']=='LIVE-QUALITY-02' for f in report['findings']):
        report['findings'].append(dict(id='LIVE-QUALITY-02',status='OPEN',problem='Inventory request accepted ToolJet generic builder as SUBTOPIC; stated business capabilities remained SOFT/UNKNOWN but output was VERIFIED_PROJECTS',evidenceRunId=inventory['runId'],releaseImpact='Inventory recommendation quality and verified-label correctness failed'))
    entries=[json.loads(line) for line in LEDGER.read_text(encoding='utf-8').splitlines() if line.strip()]
    assessment=dict(status='PARTIAL_PASS',contextScenarios='PASSED_FOR_TESTED_CASES',recommendationQuality='FAILED',releaseApproved=False,checks=checks,checkFailures=[c for c in checks if not c['passed']],providerAttempts=len(entries),limit=40,remaining=40-len(entries),attemptKinds=dict(collections.Counter(e['kind'] for e in entries)),turns=len(turns),httpProtocolSuccesses=sum(t['status']=='SUCCEEDED' for t in turns),findings=report['findings'],summaries=segments,latencies=[dict(case=t['case'],session=t['session'],durationMs=t['durationMs'],providerAttempts=t['providerAttempts']) for t in turns],performanceNote='Client submit-to-terminal polling wall time. Synthetic seeded prefix, small sample, no load/P95 claims; failed semantic samples retained.')
    assert assessment['providerAttempts']<=40
    (FOLDER/'live-review.json').write_text(json.dumps(assessment,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report['assessment']='live-review.json';report['status']='PARTIAL_PASS';report['checks']=checks;save(report)
    print(json.dumps({'checks':len(checks),'failedChecks':assessment['checkFailures'],'attempts':len(entries),'kinds':assessment['attemptKinds'],'turns':len(turns),'status':'PARTIAL_PASS'},ensure_ascii=False))

if __name__=='__main__':main()
