"""Execute the explicitly authorized quality round; default is the 15 frozen primary batches."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v1'
STAGE=ROOT/'_experiment_staging/readme-quality-v1'


def state(path):
    raw=path.read_bytes() if path.exists() else b''
    return {'entries':sum(bool(r.strip()) for r in raw.splitlines()),'sha256':hashlib.sha256(raw).hexdigest()}


def main():
    args=argparse.ArgumentParser(description=__doc__);args.add_argument('--retry-ids',default='');opts=args.parse_args()
    approval=json.loads((DOC/'authorization.json').read_text(encoding='utf-8'))
    if approval.get('authorized') is not True or approval.get('maximumAttempts')!=20:
        raise RuntimeError('Explicit 20-attempt authorization required')
    if hashlib.sha256((DOC/'frozen-dataset.json').read_bytes()).hexdigest()!=approval['datasetSha256']:
        raise RuntimeError('Dataset changed since authorization')
    dataset=json.loads((DOC/'frozen-dataset.json').read_text(encoding='utf-8'))
    if opts.retry_ids:
        allowed={d['documentId'] for d in dataset['documents'] if d['cohort']=='regression'}
        if not set(opts.retry_ids.split(',')).issubset(allowed):raise RuntimeError('Reserve retries are restricted to regression repositories')
    current=DOC/'provider-calls.jsonl';before=state(current)
    selected=[d['documentId'] for d in dataset['documents'] if not opts.retry_ids or d['documentId'] in opts.retry_ids.split(',')]
    phase='retry' if opts.retry_ids else 'primary'
    if all((STAGE/f'results/{ident}-{phase}.json').exists() for ident in selected):
        print(json.dumps({'alreadyComplete':True,'used':before['entries'],'limit':20,'newAttempts':0}));return 0
    if before['entries']>=20:raise RuntimeError('Quality budget exhausted; no new allowance created')
    histories=[ROOT/'docs/customer/context-v3/live-provider-calls.jsonl',ROOT/'docs/customer/workflow-v2/provider-calls.jsonl']
    old={str(p.relative_to(ROOT)):state(p) for p in histories}
    config=json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],encoding='utf-8'))[0]
    values=dict(s.split('=',1) for s in config['Config']['Env'] if '=' in s)
    endpoint=values.get('AI_TREND_ENDPOINT','');key=values.get('AI_TREND_KEY','');model=values.get('CUSTOMER_MODEL') or values.get('AI_TREND_MODEL','')
    if not endpoint or not key or not model:raise RuntimeError('Existing backend model config missing; values withheld')
    env=dict(os.environ,README_K3_ENDPOINT=endpoint,README_K3_KEY=key,README_K3_MODEL=model,
             CUSTOMER_ACCEPTANCE_LEDGER='/workspace/docs/rag-b/readme-filter-v1/k3/quality-v1/provider-calls.jsonl',CUSTOMER_ACCEPTANCE_LIMIT='20')
    command=['docker','run','--rm','--pull','never','-e','MAVEN_OPTS=-Xmx512m',
             '-e','README_K3_ENDPOINT','-e','README_K3_KEY','-e','README_K3_MODEL','-e','CUSTOMER_ACCEPTANCE_LEDGER','-e','CUSTOMER_ACCEPTANCE_LIMIT',
             '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
             'mvn','-o','-B','-pl','orange-server','-am','-Dtest=ReadmeClassificationQualityTest#executeApprovedFrozenBatches',
             '-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m','-Dreadme.quality.root=/workspace','-Dreadme.quality.live=true']
    if opts.retry_ids:command.append('-Dreadme.quality.retry-ids='+opts.retry_ids)
    command.append('test');STAGE.mkdir(parents=True,exist_ok=True)
    phase='retry' if opts.retry_ids else 'primary';log=STAGE/(phase+'-maven.log')
    try:
        with log.open('w',encoding='utf-8') as out:result=subprocess.run(command,env=env,stdout=out,stderr=subprocess.STDOUT)
    finally:
        after=state(current);preserved=all(state(p)==old[str(p.relative_to(ROOT))] for p in histories)
        receipt={'before':before,'after':after,'maximumAttempts':20,'historicalLedgers':old,'historicalLedgersUnchanged':preserved,'phase':phase}
        receipt_path=DOC/(phase+'-budget-receipt.json')
        if receipt_path.exists():
            import time
            receipt_path=DOC/(phase+'-budget-receipt-'+str(time.time_ns())+'.json')
        receipt_path.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if after['entries']>20 or not preserved:raise RuntimeError('Budget or historical ledger integrity violation')
    print(json.dumps({'exitCode':result.returncode,'phase':phase,'newAttempts':after['entries']-before['entries'],'used':after['entries'],'limit':20},ensure_ascii=False))
    return result.returncode


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');raise SystemExit(main())
