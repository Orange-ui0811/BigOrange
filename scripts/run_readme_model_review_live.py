"""Two opt-in K3 smoke attempts using the remainder of the existing 40-attempt authorization.

No new allowance is created. Credentials remain in process environment, never in command arguments or output.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/readme-filter-k3'
CURRENT = ROOT / 'docs/customer/context-v3/live-provider-calls.jsonl'
OLD = ROOT / 'docs/customer/workflow-v2/provider-calls.jsonl'


def ledger(path):
    raw=path.read_bytes()
    return {'sha256':hashlib.sha256(raw).hexdigest(),'entries':sum(bool(s.strip()) for s in raw.splitlines())}


def main():
    STAGE.mkdir(parents=True,exist_ok=True)
    before=ledger(CURRENT)
    if before['entries']!=38:
        raise RuntimeError('Expected exactly 38/40 before this two-attempt smoke; no allowance was reset')
    if (STAGE/'live-results.json').exists():
        raise RuntimeError('Existing smoke results retained; do not rerun paid requests')
    legacy=ledger(OLD)
    # Capture locally, never print Docker's credential-bearing inspection response.
    config=json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],encoding='utf-8'))[0]
    values=dict(row.split('=',1) for row in config['Config']['Env'] if '=' in row)
    endpoint=values.get('AI_TREND_ENDPOINT','')
    key=values.get('AI_TREND_KEY','')
    model=values.get('CUSTOMER_MODEL') or values.get('AI_TREND_MODEL','')
    if not endpoint or not key or not model:
        raise RuntimeError('Required backend environment configuration is missing; values withheld')
    env=dict(os.environ,README_K3_ENDPOINT=endpoint,README_K3_KEY=key,README_K3_MODEL=model,
             CUSTOMER_ACCEPTANCE_LIMIT='40',CUSTOMER_ACCEPTANCE_LEDGER='/workspace/docs/customer/context-v3/live-provider-calls.jsonl')
    args=['docker','run','--rm','--pull','never','-e','MAVEN_OPTS=-Xmx512m',
          '-e','README_K3_ENDPOINT','-e','README_K3_KEY','-e','README_K3_MODEL',
          '-e','CUSTOMER_ACCEPTANCE_LIMIT','-e','CUSTOMER_ACCEPTANCE_LEDGER',
          '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2',
          '-w','/workspace','maven:3.9.9-eclipse-temurin-21','mvn','-o','-B','-pl','orange-server','-am',
          '-Dtest=ReadmeModelReviewCorpusTest#liveSmokeWithinExistingTwoAttemptBudget','-Dsurefire.failIfNoSpecifiedTests=false',
          '-DargLine=-Xmx512m','-Dreadme.k3.root=/workspace','-Dreadme.k3.live=true','test']
    # Historical records are append-only; save an exact local prefix for independent verification.
    prefix=STAGE/'legacy40-before-k3.jsonl'
    if prefix.exists() and prefix.read_bytes()!=CURRENT.read_bytes():
        raise RuntimeError('Existing pre-smoke prefix differs; inspect instead of overwriting')
    prefix.write_bytes(CURRENT.read_bytes())
    log=STAGE/'live-maven.log'
    try:
        with log.open('w',encoding='utf-8') as handle:
            result=subprocess.run(args,env=env,stdout=handle,stderr=subprocess.STDOUT)
    finally:
        after=ledger(CURRENT)
        receipt={'budgetLimit':40,'before':before,'after':after,'newAttempts':after['entries']-before['entries'],
                 'original50Unchanged':legacy==ledger(OLD),'prior38PrefixPreserved':CURRENT.read_bytes().startswith(prefix.read_bytes()),
                 'configurationSource':'Existing backend environment; not Redis dynamic configuration',
                 'log':str(log.relative_to(ROOT))}
        (STAGE/'live-budget-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if not 0<=receipt['newAttempts']<=2 or after['entries']>40 or not receipt['prior38PrefixPreserved'] or not receipt['original50Unchanged']:
        raise RuntimeError('Budget/prefix integrity failure; inspect receipt')
    print(json.dumps({'exitCode':result.returncode,'newAttempts':receipt['newAttempts'],'used':after['entries'],
                      'limit':40,'receipt':str((STAGE/'live-budget-receipt.json').relative_to(ROOT))},ensure_ascii=False))
    return result.returncode


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    raise SystemExit(main())
