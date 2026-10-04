"""Local access-only smoke: no model calls, profiles, or existing conversations."""
import base64
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.request
from pathlib import Path
from customer_local_smoke import authorized_token, configured_environment, sql
from run_customer_cases import Api


def ordinary_account_token():
    # The user authorized all platform accounts. This token is used only for the
    # capability endpoint and an ownership check against our newly created session.
    row = sql("SELECT id,password_version FROM account WHERE status='ACTIVE' AND id NOT IN (1,5) ORDER BY id LIMIT 1")
    if not row:
        raise RuntimeError('No active non-whitelisted account exists for the access smoke')
    account, version = map(int, row.split('\t'))
    secret = configured_environment().get('ORANGE_JWT_APP_SECRET_KEY', 'change-me-app').encode()
    if len(secret) < 32:
        secret = secret.ljust(32, b'\0')
    encode = lambda value: base64.urlsafe_b64encode(value).rstrip(b'=')
    now = int(time.time())
    claims = {'id': account, 'tokenType': 'APP', 'iss': 'orangejd', 'aud': 'APP',
              'passwordVersion': version, 'iat': now, 'exp': now + 300}
    unsigned = encode(b'{"alg":"HS256"}') + b'.' + encode(json.dumps(claims).encode())
    token = 'sk-' + (unsigned + b'.' + encode(hmac.new(secret, unsigned, hashlib.sha256).digest())).decode()
    return account, token


def expect_denied(path, token, expected):
    request = urllib.request.Request('http://127.0.0.1:18080/app/customer' + path,
                                    headers={'App-Token': token} if token else {})
    try:
        with urllib.request.urlopen(request, timeout=15):
            raise AssertionError('An unauthorized request unexpectedly succeeded')
    except urllib.error.HTTPError as ex:
        assert ex.code == expected, f'Expected {expected}, got {ex.code}'
        return ex.code


def main():
    account, token = ordinary_account_token()
    ordinary = Api('http://127.0.0.1:18080', token)
    capabilities = ordinary.request('GET', '/capabilities')
    assert capabilities['enabled'] is True
    anonymous = expect_denied('/capabilities', None, 401)
    owner = Api('http://127.0.0.1:18080', authorized_token(5))
    session = owner.request('POST', '/sessions', {'title': '全账号开放权限验收'})
    try:
        history = owner.request('GET', f"/sessions/{session['id']}/messages")
        assert history['messages'] == []
        isolation = expect_denied(f"/sessions/{session['id']}/messages", token, 404)
    finally:
        owner.request('DELETE', f"/sessions/{session['id']}")
    result = {'ordinaryAccountId': account, 'outsidePreviousWhitelist': account not in (1, 5),
              'enabled': capabilities['enabled'], 'anonymousHttpStatus': anonymous,
              'crossAccountHttpStatus': isolation, 'ownedSessionCreatedReadAndDeleted': True,
              'modelCalls': 0, 'existingConversationsRead': False}
    target = Path(__file__).resolve().parents[1] / 'docs/customer/all-users-access-smoke.json'
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
