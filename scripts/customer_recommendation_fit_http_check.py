"""Read-only draft endpoint regression against an actual old false-positive result. No AI calls."""
import hashlib,json
from pathlib import Path
from urllib.error import HTTPError
from customer_workflow_http_acceptance import api

ROOT=Path(__file__).resolve().parents[1]

def main():
    folder=ROOT/'docs/customer/context-v3';old=json.loads((folder/'live-http-acceptance.json').read_text(encoding='utf-8'))
    row=next(t for t in old['turns'] if t['case']=='LIVE-08');card=next(p for p in row['result']['projects'] if p['name']=='ToolJet/ToolJet')
    assert row['result']['status']=='VERIFIED_PROJECTS' and card['importable'] is True
    ledger=folder/'live-provider-calls.jsonl';before=hashlib.sha256(ledger.read_bytes()).hexdigest()
    status=200;body={}
    try:body=api(old).request('POST','/runs/'+row['runId']+'/import-draft',dict(type=card['type'],projectId=card['id']))
    except HTTPError as error:
        status=error.code;body=json.loads(error.read().decode('utf-8'))
    passed=status==409 and '具体用途' in json.dumps(body,ensure_ascii=False)
    result=dict(scope='Real HTTP draft endpoint; persisted old ToolJet result and current task; no new recommendation/model call',oldRunId=row['runId'],oldStatus=row['result']['status'],oldImportable=card['importable'],httpStatus=status,response=body,ledgerUnchanged=before==hashlib.sha256(ledger.read_bytes()).hexdigest(),newModelRequests=0,passed=passed)
    (folder/'recommendation-fit-http-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False));assert passed and result['ledgerUnchanged']

if __name__=='__main__':main()
