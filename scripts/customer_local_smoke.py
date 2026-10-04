"""Local-only smoke for explicitly authorized account 5; never prints or persists its token."""
import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import time
from pathlib import Path
from run_customer_cases import Api

ROOT = Path(__file__).resolve().parents[1]

def configured_environment():
    inspected = json.loads(subprocess.check_output(['docker', 'inspect', 'nxz-bigorange-backend-1'], text=True))[0]
    return dict(value.split('=', 1) for value in inspected['Config']['Env'] if '=' in value)

def sql(query):
    mysql = json.loads(subprocess.check_output(['docker', 'inspect', 'nxz-bigorange-mysql-1'], text=True))[0]
    env = dict(value.split('=', 1) for value in mysql['Config']['Env'] if '=' in value)
    backend = configured_environment()
    database = re.search(r':3306/([^?]+)', backend['MYSQL_URL']).group(1)
    args = ['docker', 'exec', '-e', 'MYSQL_PWD=' + env['MYSQL_ROOT_PASSWORD'],
        'nxz-bigorange-mysql-1', 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '-B', '-r', database, '-e', query]
    result = subprocess.run(args, capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        raise RuntimeError('Local database query failed; details withheld to protect credentials')
    return result.stdout.strip()

def authorized_token(account_id=5):
    if account_id not in (1, 5):
        raise RuntimeError('Only explicitly authorized local accounts 1 and 5 may be tested')
    status, version = sql('SELECT status,password_version FROM account WHERE id=' + str(account_id)).split('\t')
    if status != 'ACTIVE':
        raise RuntimeError('Authorized account 5 is not active')
    env = configured_environment()
    secret = env.get('ORANGE_JWT_APP_SECRET_KEY', 'change-me-app').encode()
    if len(secret) < 32:
        secret = secret.ljust(32, b'\0')
    encode = lambda value: base64.urlsafe_b64encode(value).rstrip(b'=')
    now = int(time.time())
    claims = {'id': account_id, 'tokenType': 'APP', 'iss': 'orangejd', 'aud': 'APP', 'passwordVersion': int(version), 'iat': now, 'exp': now + 1800}
    unsigned = encode(b'{"alg":"HS256"}') + b'.' + encode(json.dumps(claims).encode())
    return 'sk-' + (unsigned + b'.' + encode(hmac.new(secret, unsigned, hashlib.sha256).digest())).decode()

def main():
    print('admin account: ' + sql("SELECT id,status,role FROM account WHERE username='admin'"), flush=True)
    api = Api('http://127.0.0.1:18080', authorized_token())
    row = api.case({'id': 'LOCAL_CHAIN_01', 'category': 'MULTI_TURN', 'messages': ['我刚学Python，目标是学习，想找项目', '第二个怎么部署', '不要Docker'],
        'expected': 'Authenticates account 5, persists state, resolves candidates safely, never relaxes Docker constraint'}, 1)
    output = ROOT / 'docs/customer/acceptance/http-smoke.json'
    output.write_text(json.dumps([row], ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'turns': len(row['turns']), 'statuses': [turn['status'] for turn in row['turns']]}, ensure_ascii=False))

if __name__ == '__main__':
    main()
