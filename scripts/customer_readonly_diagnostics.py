"""Local read-only customer access check; no model calls, no printed tokens or message text."""
import json
import urllib.request
import urllib.error
from customer_local_smoke import authorized_token, configured_environment

def main():
    env=configured_environment()
    print(json.dumps({k:env.get(k) for k in ('CUSTOMER_ENABLED','CUSTOMER_ALLOWED_ACCOUNTS','CUSTOMER_MODEL')},ensure_ascii=False))
    for owner in (1,5):
        token=authorized_token(owner)
        for path in ('/app/customer/capabilities','/app/customer/sessions'):
            request=urllib.request.Request('http://127.0.0.1:18080'+path,headers={'App-Token':token,'Origin':'http://127.0.0.1:15173'})
            try:
                with urllib.request.urlopen(request,timeout=10) as response:
                    result=json.load(response)
                    info=dict(account=owner,path=path,status=response.status,code=result.get('code'),cors=response.headers.get('Access-Control-Allow-Origin'))
                    data=result.get('data')
                    if path.endswith('capabilities'):
                        info['capabilities']=data
                    else:
                        info['sessionCount']=len(data) if isinstance(data,list) else None
                    if result.get('code')!=1:
                        info['message']=result.get('msg')
                    print(json.dumps(info,ensure_ascii=False))
            except urllib.error.HTTPError as ex:
                print(json.dumps(dict(account=owner,path=path,status=ex.code)))

if __name__=='__main__':
    main()
