"""Local login check using existing bootstrap credential, without changing passwords or logging secrets."""
import json
import urllib.request
from customer_local_smoke import ROOT, configured_environment

def probe():
    password=configured_environment().get('ORANGE_ADMIN_BOOTSTRAP_PASSWORD','')
    report={'accountId':1,'username':'admin','passwordChanged':False,'passed':False,'scope':'Actual local HTTP login'}
    token=None
    if not password:
        report['status']='NO_CONFIGURED_TEST_CREDENTIAL'
    else:
        try:
            request=urllib.request.Request('http://127.0.0.1:18080/app/auth/login',
                data=json.dumps({'username':'admin','password':password}).encode(),headers={'Content-Type':'application/json'})
            with urllib.request.urlopen(request,timeout=10) as response:
                result=json.load(response)
            data=result.get('data') or {}
            if result.get('code')==1 and str(data.get('id'))=='1' and isinstance(data.get('token'),str):
                token=data['token']; report.update(passed=True,status='PASSED')
            else:
                report['status']='CONFIGURED_CREDENTIAL_NOT_ACCEPTED'
        except Exception as error:
            report.update(status='LOGIN_FAILED',errorClass=type(error).__name__)
    (ROOT/'docs/customer/acceptance/login.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return token,password if token else None,report

if __name__=='__main__':
    print(json.dumps(probe()[2],ensure_ascii=False))
