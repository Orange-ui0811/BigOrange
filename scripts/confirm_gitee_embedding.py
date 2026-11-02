"""Minimal provider diagnosis using backend container configuration; no corpus or account data sent."""
import argparse, json, time, urllib.request, urllib.error
from datetime import datetime, timezone
from pathlib import Path
from customer_local_smoke import configured_environment, ROOT

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--model', choices=['bge-m3','bge-small-zh-v1.5'], default='bge-m3')
    parser.add_argument('--catalogue', action='store_true')
    parser.add_argument('--credential', choices=['AI_EMBEDDING_KEY','AI_TREND_KEY']+['GITEE_AI_KEY_'+str(i) for i in range(1,11)], default='AI_EMBEDDING_KEY')
    args=parser.parse_args()
    env=configured_environment()
    key=env.get(args.credential,'')
    endpointName='GITEE_AI_ENDPOINT' if args.credential.startswith('GITEE_AI_KEY_') else 'AI_TREND_ENDPOINT' if args.credential=='AI_TREND_KEY' else 'AI_EMBEDDING_ENDPOINT'
    endpoint=env.get(endpointName,'').rstrip('/')
    # Use each credential only at its configured endpoint; never transpose credentials between hosts.
    if endpoint not in ['https://ai.gitee.com/v1','https://api.moark.com/v1'] or not key:
        raise RuntimeError('Expected configured Gitee/MoArk credential and matching endpoint unavailable')
    opener=urllib.request.build_opener(NoRedirect())
    rows=[]
    def call(path, body=None):
        row={'path':path,'endpoint':endpoint,'credentialConfig':args.credential,'startedAt':datetime.now(timezone.utc).isoformat()}
        started=time.monotonic()
        request=urllib.request.Request(endpoint+path, data=None if body is None else json.dumps(body).encode(),
            headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'})
        try:
            with opener.open(request,timeout=20) as response:
                row['status']=response.status
                parsed=json.load(response)
                if path=='/models':
                    ids=[str(v.get('id','')) for v in parsed.get('data',[])]
                    row.update(modelCount=len(ids),bgeM3Listed='bge-m3' in ids,baselineListed='bge-small-zh-v1.5' in ids)
                else:
                    data=parsed.get('data',[])
                    row.update(model=args.model,dimensions=len(data[0].get('embedding',[])) if data else 0,usage=parsed.get('usage'))
        except urllib.error.HTTPError as ex:
            row['status']=ex.code
            row['headers']={k:v for k,v in ex.headers.items() if k.lower() in ['retry-after','x-request-id','x-ratelimit-limit-requests','x-ratelimit-remaining-requests','x-ratelimit-reset-requests']}
            try:
                error=json.loads(ex.read(16384).decode('utf-8')).get('error',{})
                if isinstance(error,dict):row['error']={k:str(error[k]).replace(key,'[REDACTED]')[:600] for k in ['code','type','message'] if k in error}
            except (ValueError,UnicodeError):row['error']={'type':'NON_JSON_RESPONSE'}
        except Exception as ex: row['error']={'type':type(ex).__name__}
        row['durationMs']=round((time.monotonic()-started)*1000)
        rows.append(row)
        print(json.dumps(row,ensure_ascii=True),flush=True)
    if args.catalogue:call('/models')
    call('/embeddings',{'model':args.model,'input':['连接诊断：检索项目资料。']})
    report={'scope':'One fixed synthetic embedding request; optional read-only model catalogue; no redirects or retries',
        'credentialSource':'Backend container configuration '+args.credential,'endpoint':endpoint,'rows':rows}
    folder=ROOT/'docs/customer/langgraph4j/repair-20261007/provider-diagnosis'
    folder.mkdir(parents=True,exist_ok=True)
    name=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+args.model+'-'+args.credential+'.json'
    (folder/name).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':main()
