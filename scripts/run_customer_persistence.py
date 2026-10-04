"""Run Flyway and transaction tests in a fresh, isolated local MySQL database."""
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def main():
    name = 'customer_acceptance_' + str(int(time.time()))
    raw = subprocess.check_output(['docker', 'inspect', 'nxz-bigorange-mysql-1'], text=True)
    env = dict(item.split('=', 1) for item in json.loads(raw)[0]['Config']['Env'] if '=' in item)
    password = env['MYSQL_ROOT_PASSWORD']
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE ' + name + ' CHARACTER SET utf8mb4"'
    subprocess.run(['docker', 'exec', 'nxz-bigorange-mysql-1', 'sh', '-c', command], check=True, capture_output=True)
    args = ['docker', 'exec', '-w', '/workspace',
            '-e', 'CUSTOMER_TEST_MYSQL_URL=jdbc:mysql://mysql:3306/' + name + '?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',
            '-e', 'CUSTOMER_TEST_MYSQL_USER=root', '-e', 'CUSTOMER_TEST_MYSQL_PASSWORD=' + password,
            'nxz-bigorange-backend-1', 'mvn', '-pl', 'orange-server', '-am',
            '-Dtest=CustomerPersistenceIntegrationTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test', '-q']
    result = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    folder = ROOT / 'docs/customer/acceptance'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'persistence.log').write_text((result.stdout + result.stderr).replace(password, '[REDACTED]'), encoding='utf-8')
    (folder / 'persistence.json').write_text(json.dumps({'database': name, 'exitCode': result.returncode,
        'scope': 'Fresh database through V40, upgrade to V41, Flyway validation and actual mapper/transaction tests; no live user data or model calls.'}, indent=2), encoding='utf-8')
    print(json.dumps({'database': name, 'exitCode': result.returncode}))
    raise SystemExit(result.returncode)

if __name__ == '__main__':
    main()
