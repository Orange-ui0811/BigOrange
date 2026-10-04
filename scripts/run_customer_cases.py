"""Authenticated customer HTTP evaluator. Leaves semantic verdicts for independent review."""
import argparse
import json
import os
import time
import uuid
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TERMINAL = {'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'INTERRUPTED'}

class Api:
    def __init__(self, base, token):
        self.base, self.token = base.rstrip('/'), token

    def request(self, method, path, body=None):
        request = urllib.request.Request(self.base + '/app/customer' + path,
            data=None if body is None else json.dumps(body, ensure_ascii=False).encode(), method=method,
            headers={'App-Token': self.token, 'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=15) as response:
            value = json.load(response)
        if value.get('code') != 1:
            raise RuntimeError('Customer business response rejected')
        return value['data']

    def case(self, case, round_number):
        session = self.request('POST', '/sessions', {'title': '验收 ' + case['id']})
        row = {'caseId': case['id'], 'round': round_number, 'variant': 'B', 'category': case['category'],
            'sessionId': session['id'], 'manualPassed': None, 'criticalFailure': False, 'turns': [], 'metrics': {'modelCalls': []}}
        for message in case['messages']:
            started = time.monotonic()
            run = self.request('POST', f"/sessions/{session['id']}/runs", {'requestId': str(uuid.uuid4()), 'message': message})
            deadline = time.monotonic() + 95
            while run['status'] not in TERMINAL and time.monotonic() < deadline:
                time.sleep(.5)
                run = self.request('GET', '/runs/' + run['id'])
            metrics = json.loads(run.get('metrics_json') or '{}')
            # Read once more after terminal state so a just-finished worker can publish usage.
            if run['status'] in TERMINAL:
                time.sleep(.2)
                run = self.request('GET', '/runs/' + run['id'])
                metrics = json.loads(run.get('metrics_json') or '{}')
            row['metrics']['modelCalls'].extend(metrics.get('modelCalls', []))
            row['turns'].append({'message': message, 'runId': run['id'], 'status': run['status'],
                'errorCode': run.get('error_code'), 'durationMs': round((time.monotonic() - started) * 1000),
                'generation': run.get('generation_key'), 'result': json.loads(run.get('result_json') or '{}')})
            if run['status'] not in TERMINAL:
                self.request('POST', '/runs/' + run['id'] + '/cancel', {})
                break
        row['durationMs'] = sum(turn['durationMs'] for turn in row['turns'])
        row['expected'] = case['expected']
        row['split'] = case.get('split', 'development')
        return row

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:8080')
    parser.add_argument('--split', choices=['development', 'acceptance'], default='development')
    parser.add_argument('--ids', default='')
    parser.add_argument('--rounds', type=int, default=1)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    token = os.environ.get('ORANGE_CUSTOMER_APP_TOKEN')
    if not token:
        raise SystemExit('Provide ORANGE_CUSTOMER_APP_TOKEN for an authorized whitelist account. Token is never written.')
    api = Api(args.base, token)
    if not api.request('GET', '/capabilities')['enabled']:
        raise SystemExit('Customer is disabled for this account.')
    cases = json.loads((ROOT / 'docs/customer/cases-v1.json').read_text(encoding='utf-8'))['cases']
    selected = set(filter(None, args.ids.split(',')))
    rows = []
    for round_number in range(1, args.rounds + 1):
        for case in cases:
            if case['split'] != args.split or selected and case['id'] not in selected:
                continue
            if 'messages' not in case:
                rows.append({'caseId': case['id'], 'round': round_number, 'variant': 'B', 'category': case['category'],
                    'executionStatus': 'REQUIRES_BOUNDARY_HARNESS', 'manualPassed': None, 'expected': case['expected']})
            else:
                try:
                    rows.append(api.case(case, round_number))
                except Exception as error:
                    rows.append({'caseId': case['id'], 'round': round_number, 'variant': 'B', 'manualPassed': None,
                        'executionStatus': 'HTTP_FAILURE', 'errorClass': type(error).__name__})
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps({'caseId': case['id'], 'round': round_number, 'recorded': len(rows)}), flush=True)

if __name__ == '__main__':
    main()
