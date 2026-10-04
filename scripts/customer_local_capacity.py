"""Two-account local capacity and HTTP boundary checks; no automatic semantic approval."""
import concurrent.futures
import json
import math
import time
import urllib.error
import urllib.request
import uuid
from customer_local_smoke import authorized_token, ROOT
from run_customer_cases import Api

def main():
    apis = {owner: Api('http://127.0.0.1:18080', authorized_token(owner)) for owner in (1, 5)}
    for api in apis.values():
        if not api.request('GET', '/capabilities')['enabled']:
            raise SystemExit('Both authorized accounts must be enabled before capacity testing.')
    questions = json.loads((ROOT / 'docs/customer/cases-v1.json').read_text(encoding='utf-8'))['cases'][:10]
    rows = []
    def worker(owner):
        output = []
        for case in questions:
            row = apis[owner].case(case, 1)
            row['capacityAccountId'] = owner
            output.append(row)
        return output
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for batch in pool.map(worker, (1, 5)):
            rows.extend(batch)
    folder = ROOT / 'docs/customer/acceptance'
    (folder / 'capacity-results.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    durations = sorted(turn['durationMs'] for row in rows for turn in row['turns'])
    statuses = [turn['status'] for row in rows for turn in row['turns']]
    report = {'scope': '20 real customer runs, 2 concurrent authorized accounts; authenticated HTTP, not interactive login',
        'runs': len(statuses), 'p95Ms': durations[math.ceil(len(durations) * .95) - 1],
        'failedRuns': sum(status != 'SUCCEEDED' for status in statuses),
        'allWithin90Seconds': all(ms <= 90000 for ms in durations), 'qualityApproved': False}
    api = apis[5]
    session = api.request('POST', '/sessions', {'title': 'HTTP边界验收'})
    checks = {}
    def rejected(name, work, status):
        try:
            work(); checks[name] = False
        except urllib.error.HTTPError as error:
            checks[name] = error.code == status
    rejected('foreignSession404', lambda: apis[1].request('GET', f"/sessions/{session['id']}/messages"), 404)
    request_id = str(uuid.uuid4())
    body = {'requestId': request_id, 'message': '我想学习Java，我是新手'}
    run = api.request('POST', f"/sessions/{session['id']}/runs", body)
    replay = api.request('POST', f"/sessions/{session['id']}/runs", body)
    checks['sameRequestSameRun'] = run['id'] == replay['id']
    rejected('differentContent409', lambda: api.request('POST', f"/sessions/{session['id']}/runs", {**body, 'message': '换成Python'}), 409)
    rejected('foreignRun404', lambda: apis[1].request('GET', '/runs/' + run['id']), 404)
    started = time.monotonic()
    request = urllib.request.Request(api.base + '/app/customer/runs/' + run['id'] + '/events', headers={'App-Token': api.token})
    with urllib.request.urlopen(request, timeout=10) as stream:
        event = stream.readline() + stream.readline()
    checks['sseFirstEventWithinOneSecond'] = b'data:' in event and time.monotonic() - started < 1
    cancelled = api.request('POST', '/runs/' + run['id'] + '/cancel', {})
    checks['cancelTerminal'] = cancelled['status'] == 'CANCELLED'
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        cancelled = api.request('GET', '/runs/' + run['id'])
        if json.loads(cancelled.get('metrics_json') or '{}').get('modelCalls'):
            break
        time.sleep(.5)
    checks['lateResultCannotResurrect'] = cancelled['status'] == 'CANCELLED'
    report['boundaryChecks'] = checks
    report['capacityGatePassed'] = report['failedRuns'] == 0 and report['p95Ms'] <= 60000 and report['allWithin90Seconds'] and all(checks.values())
    (folder / 'capacity.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    (folder / 'cancelled-run.json').write_text(json.dumps(cancelled, ensure_ascii=False, indent=2, default=str), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__':
    main()
