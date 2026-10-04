"""Real customer HTTP regression, authorized accounts only; never exports credentials.

Synthetic conversations pass through the running Java service, model, state validation,
project query and B retrieval. Semantic assertions are separate from successful responses.
"""
import concurrent.futures
import json
import time
import uuid
from pathlib import Path
from customer_local_smoke import authorized_token, ROOT
from run_customer_cases import Api, TERMINAL

OUTPUT = ROOT / 'docs/customer/acceptance/customer-v4-live.json'
CASES = [
    ('S01', '把这次项目的完成周期设为半年内，先记下就好', {'maxDurationDays': 180}),
    ('S02', '我最多能花一个半月做完，先记录这个条件', {'maxDurationDays': 45}),
    ('S03', '希望一个季度之内完成项目，先记下要求', {'maxDurationDays': 90}),
    ('S04', 'I need to finish the project within a fortnight. Just note this condition.', {'maxDurationDays': 14}),
    ('S05', '项目最多十二周完成，先记录', {'maxDurationDays': 84}),
    ('S06', '希望三到四个月完成，短一些也可以，先记录', {'maxDurationDays': 120}),
    ('S07', '每天可以学两小时，暂时没有项目截止时间', {'absent': 'maxDurationDays'}),
    ('S08', '使用JS开发，Docker我并不排斥，先记录', {'language': 'JavaScript', 'allowDocker': True}),
    ('S09', '我想用爪哇语言做作品来找份工作，先记下', {'language': 'Java', 'goal': '求职'}),
    ('S10', '需要二十个工作日做完，周末不做，先记录', {'pendingFields': ['maxDurationDays']}),
    ('S11', '至少半年才能体现项目规模，不能少于半年，先记录', {'minDurationDays': 180}),
    ('S12', '只用Java，数据库和Docker都不想用，先记下', {'language': 'Java', 'allowDatabase': False, 'allowDocker': False}),
]


def turn(api, session, message):
    start = time.monotonic()
    request_id = str(uuid.uuid4())
    run = api.request('POST', f'/sessions/{session}/runs', {'requestId': request_id, 'message': message})
    limit = time.monotonic() + 95
    while run['status'] not in TERMINAL and time.monotonic() < limit:
        time.sleep(.4)
        run = api.request('GET', '/runs/' + run['id'])
    if run['status'] not in TERMINAL:
        api.request('POST', '/runs/' + run['id'] + '/cancel', {})
        raise RuntimeError('No terminal state within deadline')
    time.sleep(.15)
    run = api.request('GET', '/runs/' + run['id'])
    result = json.loads(run.get('result_json') or '{}')
    history = api.request('GET', f'/sessions/{session}/messages')
    row = dict(message=message, runId=run['id'], status=run['status'], version=run.get('prompt_version'),
               error=run.get('error_code'), durationMs=round((time.monotonic()-start)*1000),
               result=result, state=history['state'], metrics=json.loads(run.get('metrics_json') or '{}'))
    row['continuationPassed'] = bool(result.get('answer')) and '本轮失败，可以重新提问' not in result.get('answer', '')
    replay = api.request('POST', f'/sessions/{session}/runs', {'requestId': request_id, 'message': message})
    row['dedupePassed'] = replay['id'] == run['id']
    return row


def semantic_case(api, spec):
    case_id, message, expected = spec
    session = api.request('POST', '/sessions', {'title': 'v4语义回归 ' + case_id})['id']
    row = turn(api, session, message)
    state = row['state']
    row['caseId'], row['expected'] = case_id, expected
    checks = []
    for key, value in expected.items():
        checks.append(value not in state if key == 'absent' else [p['field'] for p in state.get('pendingConditions', [])] == value if key == 'pendingFields' else bool(state.get('pendingConditions')) if key == 'pending' else state.get(key) == value)
    row['semanticPassed'] = all(checks) and row['status'] == 'SUCCEEDED'
    return row


def worker(owner, cases):
    api = Api('http://127.0.0.1:18080', authorized_token(owner))
    caps = api.request('GET', '/capabilities')
    if not caps.get('enabled'): raise RuntimeError('Test account is not enabled')
    rows = []
    for spec in cases:
        row = semantic_case(api, spec)
        rows.append(row)
        print(json.dumps({'caseId': row['caseId'], 'status': row['status'], 'semanticPassed': row['semanticPassed'], 'state': row['state'], 'answer': row['result'].get('answer')}, ensure_ascii=False), flush=True)
    return rows


def main():
    rows = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        jobs = [executor.submit(worker, 5, CASES[::2]), executor.submit(worker, 1, CASES[1::2])]
        for job in concurrent.futures.as_completed(jobs):
            rows.extend(job.result())
            OUTPUT.write_text(json.dumps({'rows': rows}, ensure_ascii=False, indent=2), encoding='utf-8')
    api = Api('http://127.0.0.1:18080', authorized_token(5))
    session = api.request('POST', '/sessions', {'title': 'v4连续对话与推荐'})['id']
    messages = ['你好', '今天学编程学得有点烦，不想聊项目', '能用简单例子解释一下Java的依赖注入吗？',
                '先给我推荐几个Java项目，不超过半年完成，我刚学，目标学习。没有资料的地方请标出来',
                '第二个项目怎么部署？如果资料不足就说哪些地方还需要核对',
                '改成两个月内，其他条件保持不变', '你记得我现在的条件吗？', '谢谢，明天再聊']
    for i, message in enumerate(messages, 1):
        row = turn(api, session, message)
        row['caseId'] = 'D' + str(i).zfill(2)
        checks = [row['status'] == 'SUCCEEDED', row['continuationPassed']]
        if i in (1,2,3,8): checks.append(row['result'].get('intent') == 'CHAT')
        if i == 4: checks.extend([row['state'].get('language') == 'Java', row['state'].get('maxDurationDays') == 180])
        if i == 6: checks.extend([row['state'].get('language') == 'Java', row['state'].get('maxDurationDays') == 60])
        for card in row['result'].get('projects', []):
            checks.append(card.get('language') in ('Java', None, '') or bool(card.get('conditionGaps')))
            if card.get('matchStatus') == 'NEEDS_VERIFICATION': checks.extend([bool(card.get('conditionGaps')), not card.get('importable')])
        row['semanticPassed'] = all(checks)
        rows.append(row)
        OUTPUT.write_text(json.dumps({'rows': rows}, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'caseId': row['caseId'], 'status': row['status'], 'passed': row['semanticPassed'], 'answer': row['result'].get('answer'), 'projects': len(row['result'].get('projects', []))}, ensure_ascii=False), flush=True)
    calls = [c for row in rows for c in row['metrics'].get('modelCalls', [])]
    summary = dict(turns=len(rows), succeeded=sum(r['status']=='SUCCEEDED' for r in rows), semanticPassed=sum(r['semanticPassed'] for r in rows),
                   continuationPassed=sum(r['continuationPassed'] for r in rows), dedupePassed=sum(r['dedupePassed'] for r in rows),
                   modelAttempts=len(calls), reportedTokens=sum(c.get('usage',{}).get('total_tokens',0) for c in calls), costUsd='UNKNOWN',
                   p95Ms=sorted(r['durationMs'] for r in rows)[int(.95*len(rows))-1],
                   limitations=['Development regression, not independent production acceptance', 'Token totals exclude unreported embedding usage', 'Conversation quality still requires reading the answers'])
    OUTPUT.write_text(json.dumps(dict(summary=summary, rows=rows), ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
