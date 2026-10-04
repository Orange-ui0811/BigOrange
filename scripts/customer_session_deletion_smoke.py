"""Local CRUD-only acceptance. Creates/deletes its own synthetic session; no model calls."""
import json
import urllib.request
import urllib.error
from pathlib import Path
from customer_local_smoke import authorized_token, sql


def main():
    base = 'http://127.0.0.1:18080/app/customer'
    tokens = {owner: authorized_token(owner) for owner in (5, 1)}
    checks = []

    def request(owner, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(base + path, data=data, method=method,
            headers={'App-Token': tokens[owner], 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def check(name, actual, expected):
        assert actual == expected, name + ' failed'
        checks.append({'name': name, 'passed': True})

    check('Flyway V42 applied', sql("SELECT success FROM flyway_schema_history WHERE version='42'"), '1')
    status, body = request(5, 'POST', '/sessions', {'title': 'UI v4.3 删除验收（合成空会话）'})
    check('create synthetic session', (status, body['code']), (200, 1))
    session = body['data']['id']
    try:
        check('owner can read session', request(5, 'GET', f'/sessions/{session}/messages')[0], 200)
        check('other authorized owner cannot delete', request(1, 'DELETE', f'/sessions/{session}')[0], 404)
        check('other authorized owner cannot read', request(1, 'GET', f'/sessions/{session}/messages')[0], 404)
        check('owner can delete', request(5, 'DELETE', f'/sessions/{session}')[0], 200)
        check('owner deletion is idempotent', request(5, 'DELETE', f'/sessions/{session}')[0], 200)
        check('deleted history unavailable', request(5, 'GET', f'/sessions/{session}/messages')[0], 404)
        status, body = request(5, 'GET', '/sessions')
        check('deleted session absent from list', any(row['id'] == session for row in body['data']), False)
        check('deleted marker retained', sql(f'SELECT deleted_at IS NOT NULL FROM customer_session WHERE id={int(session)}'), '1')
    finally:
        request(5, 'DELETE', f'/sessions/{session}')
    report = {'checks': checks, 'realModelCalls': 0, 'existingSessionsChanged': False}
    Path('docs/customer/acceptance/customer-v43-deletion-smoke.json').write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
