"""Run real local UI validation without writing tokens or credential-bearing browser traces."""
import json
import os
import subprocess
from customer_local_smoke import ROOT, authorized_token
from customer_login_probe import probe

def main():
    env = dict(os.environ)
    token,password,login=probe()
    env['CUSTOMER_LIVE_BROWSER_TOKEN'] = token or authorized_token(1)
    if password:
        env['CUSTOMER_LIVE_BROWSER_PASSWORD']=password
    env['ORANGEVC_E2E_EXTERNAL'] = '1'
    result = subprocess.run(['npx.cmd', 'playwright', 'test', 'e2e/customer-service-live.spec.js', '--reporter=json'],
        cwd=ROOT / 'orange-web', env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    report = result.stdout.replace(env['CUSTOMER_LIVE_BROWSER_TOKEN'], '[REDACTED]')
    errors = result.stderr.replace(env['CUSTOMER_LIVE_BROWSER_TOKEN'], '[REDACTED]')
    if password:
        report=report.replace(password,'[REDACTED]'); errors=errors.replace(password,'[REDACTED]')
    (ROOT / 'docs/customer/acceptance/browser-live.json').write_text(report, encoding='utf-8')
    (ROOT / 'docs/customer/acceptance/browser-live.log').write_text(errors, encoding='utf-8')
    print(json.dumps({'exitCode': result.returncode, 'realLogin':login['passed'], 'scope': 'Actual HTTP + UI; login credential used only when its local probe succeeds'}))
    raise SystemExit(result.returncode)

if __name__ == '__main__':
    main()
