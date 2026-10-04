"""Execute independent message cases twice with authorized local account 5, preserving boundary gaps."""
import os
import subprocess
import sys
from customer_local_smoke import ROOT, authorized_token

def main():
    env=dict(os.environ)
    env['ORANGE_CUSTOMER_APP_TOKEN']=authorized_token(5)
    result=subprocess.run([sys.executable,str(ROOT/'scripts/run_customer_cases.py'),'--base','http://127.0.0.1:18080',
        '--split','acceptance','--rounds','2','--output',str(ROOT/'docs/customer/acceptance/independent-results.json')],env=env)
    raise SystemExit(result.returncode)

if __name__ == '__main__':
    main()
