"""Verify expanded quality evidence, immutable pre-call labels and the 20-attempt allowance."""
import ast
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v1'
STAGE=ROOT/'_experiment_staging/readme-quality-v1'


def read(path):return json.loads(path.read_text(encoding='utf-8'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def require(value,message):
    if not value:raise ValueError(message)


def main():
    authorization=read(DOC/'authorization.json');data=read(DOC/'frozen-dataset.json')
    require(authorization['authorized'] and authorization['maximumAttempts']==20,'Missing authorization')
    require(sha(DOC/'frozen-dataset.json')==authorization['datasetSha256'],'Post-call relabeling detected')
    require(len(data['cases'])==54 and len(data['controls'])==14 and len(data['documents'])==15,'Wrong dataset size')
    snapshot=ROOT/'_experiment_staging/readme-filter-v1-20261029/snapshot.json'
    require(sha(snapshot)==data['sourceSnapshotSha256'],'Frozen source changed')
    old_ids={int(d['documentId']) for d in read(ROOT/'docs/rag-b/readme-filter-v1/sample-manifest.json')['samples']}
    fresh=[d for d in data['documents'] if d['cohort']=='new_repository']
    require(len(fresh)==8 and all(int(d['documentId']) not in old_ids for d in fresh),'Repository split overlap')
    records=[json.loads(row) for row in (DOC/'provider-calls.jsonl').read_text(encoding='utf-8').splitlines() if row.strip()]
    journal=[json.loads(row) for row in (STAGE/'selection-attempts.jsonl').read_text(encoding='utf-8').splitlines() if row.strip()]
    require(len(records)==18 and len(records)<=20,'Budget exceeded or wrong request count')
    require(sum(r['event']=='START' for r in journal)==18,'Attempt journals differ')
    for name in ['primary-budget-receipt.json','retry-budget-receipt.json']:
        receipt=read(DOC/name);require(receipt['historicalLedgersUnchanged'],'Historical ledgers changed')
        for filename,baseline in receipt['historicalLedgers'].items():
            path=ROOT/filename
            require(sha(path)==baseline['sha256'],'Old ledger hash changed')
    primary=read(DOC/'primary-evaluation.json');final=read(DOC/'final-evaluation.json')
    require(primary['metrics']['completedBatches']==15 and final['metrics']['completedBatches']==15,'Incomplete results')
    for r in final['cases']:
        require(r['resultPresent'],'Missing labelled result')
    for metric in [primary['metrics'],final['metrics']]:
        require(metric['sourceMappingErrors']==metric['sourceRewriteErrors']==metric['nonKeepCandidateLeaks']==metric['controlErrors']==0,'Source/control invariant failure')
    # The eight new repositories retain exactly the same first-pass responses and are never retried.
    for sample in fresh:
        require(not (STAGE/f"results/{sample['documentId']}-retry.json").exists(),'New cohort used for retry tuning')
    require(not final['metrics']['meetsProposedGate'],'This recorded round is expected to retain the failed gate')
    repair=(STAGE/'repair-offline.log').read_text(encoding='utf-8')
    invalidation=(STAGE/'cache-invalidation-offline.log').read_text(encoding='utf-8')
    require('Tests run: 38, Failures: 0, Errors: 0, Skipped: 0' in repair and 'BUILD SUCCESS' in repair,'Repair regression evidence missing')
    require('Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' in invalidation and 'BUILD SUCCESS' in invalidation,'Cache invalidation not verified')
    cache=read(ROOT/'_experiment_staging/readme-filter-k3/cache-policy-invalidation.json')
    require(cache['newPromptInvalidatesOldCache'] and cache['providerCalls']==0 and cache['journalUnchanged'],'Old prompt cache incorrectly reused')
    require(len(list((STAGE/'traces').glob('*-MODEL_REQUEST.json')))==18,'Request traces missing')
    require(len(list((STAGE/'traces').glob('*-MODEL_RESPONSE.json')))==18,'Response traces missing')
    for script in ['prepare_readme_quality_dataset.py','run_readme_quality_live.py','score_readme_quality.py','verify_readme_quality.py']:
        ast.parse((ROOT/'scripts'/script).read_text(encoding='utf-8'))
    files=[ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeModelReview.java',
           ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeClassificationQualityTest.java',
           ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeModelReviewTest.java']
    files += [ROOT/'scripts'/s for s in ['prepare_readme_quality_dataset.py','run_readme_quality_live.py','score_readme_quality.py','verify_readme_quality.py']]
    summary={'status':'QUALITY_EVALUATION_COMPLETE_GATE_NOT_MET','checkedAt':datetime.now(timezone.utc).isoformat(),
             'labelledTargets':54,'ruleControls':14,'repositories':16,'modelBatches':15,'newRepositories':8,
             'budgetAuthorized':20,'budgetUsed':18,'budgetRemaining':2,'historicalLedgersUnchanged':True,
             'frozenDatasetSha256':authorization['datasetSha256'],'labelsUserConfirmed':False,
             'primaryMetrics':primary['metrics'],'diagnosticAggregateMetrics':final['metrics'],
             'newCohortRetried':False,'uniformNewVersionQualityPassed':False,'repairOfflineTestsPassed':38,
             'cacheInvalidationOfflineTestsPassed':1,'productionIndexWrites':0,
             'nextStep':'Fix context dependency closure and clarify navigation/learning resource policy, then evaluate one version on new frozen repositories before K4 activation',
             'implementationHashes':{p.relative_to(ROOT).as_posix():sha(p) for p in files},
             'resultHashes':{p.name:sha(p) for p in sorted((STAGE/'results').glob('*.json'))}}
    (DOC/'verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:summary[k] for k in ['status','labelledTargets','ruleControls','budgetUsed','budgetRemaining','newCohortRetried','uniformNewVersionQualityPassed']},ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
