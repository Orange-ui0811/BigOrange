"""Loopback-only HTTP smoke against disposable fixtures; never prints test JWTs."""
import argparse, base64, hashlib, hmac, json, time, urllib.request, urllib.error

def token(owner):
    def encode(value):
        return base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=')
    head=encode({'alg':'HS256','typ':'JWT'})
    body=encode({'id':owner,'tokenType':'APP','iss':'orangevc','aud':['APP'],'exp':int(time.time())+300})
    message=head+b'.'+body
    # The isolated server uses the repository's public development default.
    signature=hmac.new(b'change-me-app'.ljust(32,b'\0'),message,hashlib.sha256).digest()
    return 'sk-'+(message+b'.'+base64.urlsafe_b64encode(signature).rstrip(b'=')).decode()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['disabled','whitelist'])
    args=parser.parse_args();opener=urllib.request.build_opener(urllib.request.ProxyHandler({}));checks=[]
    def call(path,owner=None,method='GET',body=None):
        headers={'Content-Type':'application/json'}
        if owner is not None:headers['App-Token']=token(owner)
        request=urllib.request.Request('http://127.0.0.1:18880'+path,headers=headers,method=method,
            data=None if body is None else json.dumps(body).encode())
        try:
            with opener.open(request,timeout=15) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as error:
            try:data=json.load(error)
            except (ValueError,UnicodeDecodeError):data={}
            return error.code,data
    def require(label,condition):
        if not condition:raise AssertionError(label)
        checks.append(label)
    status,health=call('/health/detail');require('DB/Redis/A vector store healthy',status==200 and all(health.get(k)=='UP' for k in ['db','redis','rag_vector_store']))
    status,_=call('/app/customer/sessions');require('anonymous requests denied',status==401)
    status,cap=call('/app/customer/capabilities',900001)
    require('authenticated capability flag',status==200 and cap['data']['enabled']==(args.mode=='whitelist'))
    status,_=call('/app/customer/sessions',900002,'POST',{'title':'synthetic HTTP fixture'})
    require('non-whitelisted user denied',status==403)
    if args.mode=='disabled':
        status,_=call('/app/customer/sessions',900001,'POST',{'title':'synthetic HTTP fixture'})
        require('feature switch denies whitelisted user too',status==403)
    else:
        with opener.open('http://127.0.0.1:18999/requests',timeout=5) as response:before=json.load(response)['requests']
        status,created=call('/app/customer/sessions',900001,'POST',{'title':'synthetic HTTP fixture'})
        require('whitelisted user creates own session',status==200 and created.get('code')==1)
        session=created['data']['id']
        status,_=call(f'/app/customer/sessions/{session}/messages',900002)
        require('foreign session cannot be read',status==403 or status==404)
        payload={'requestId':'http-smoke-request-001','message':'你好'}
        status,first=call(f'/app/customer/sessions/{session}/runs',900001,'POST',payload)
        require('run accepted',status==200 and first.get('code')==1)
        run=first['data']['id']
        status,second=call(f'/app/customer/sessions/{session}/runs',900001,'POST',payload)
        require('request replay preserves run identity',status==200 and second['data']['id']==run)
        for _ in range(100):
            status,current=call('/app/customer/runs/'+run,900001)
            if current['data'].get('status') in ['SUCCEEDED','FAILED','CANCELLED','INTERRUPTED']:break
            time.sleep(.1)
        require('graph run completes through local provider stub',current['data'].get('status')=='SUCCEEDED')
        with opener.open('http://127.0.0.1:18999/requests',timeout=5) as response:after=json.load(response)['requests']
        require('retry does not make a second model call',after==before+1)
        request=urllib.request.Request('http://127.0.0.1:18880/app/customer/runs/'+run+'/events',headers={'App-Token':token(900001)})
        with opener.open(request,timeout=5) as response:events=response.read().decode()
        require('SSE reconnect returns terminal state', 'event:status' in events and 'SUCCEEDED' in events)
        status,history=call(f'/app/customer/sessions/{session}/messages',900001)
        require('history is readable by owner',status==200 and history.get('code')==1)
        status,_=call('/app/customer/runs/'+run,900002)
        require('foreign run cannot be read',status==403 or status==404)
    print(json.dumps({'mode':args.mode,'checks':checks,'passed':len(checks)},ensure_ascii=False))

if __name__=='__main__':main()
