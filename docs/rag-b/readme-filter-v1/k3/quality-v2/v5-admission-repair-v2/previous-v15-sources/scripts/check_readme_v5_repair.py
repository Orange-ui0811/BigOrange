"""Verify deterministic V5 repairs and prepare fresh diagnostic requests; never performs provider HTTP."""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from score_readme_quality_v2 import evaluate as evaluate_legacy
from score_readme_quality_v3 import evaluate

ROOT=Path(__file__).resolve().parents[1]
Q=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2'
OUT=Q/'v5-deterministic-repair-v1'
STAGE=ROOT/'_experiment_staging/readme-v5-deterministic-repair-v1'
PILOT=Q/'v5-pilot-v2'
IDS=['240','244','427','522','546']
CLASSES=['ReadmeKnowledgeFilterTest','ReadmeRecommendationProjectionTest','ReadmeDependencyClosureTest',
         'ReadmeModelReviewTest','ReadmeSpringAiReviewTransportTest','RagBTokenChunkerTest','ReadmeR3RepairTest',
         'K3V5DeterministicRepairTest','K3V5RepairEvidenceTest']
VERSIONS={'parser':'CONTIGUOUS_MARKDOWN_BLOCKS_V1','rulePolicy':'README_RULE_PREVIEW_V1_5','selectionPolicy':'README_MODEL_REVIEW_V1_5',
          'prompt':'README_SELECTOR_ZH_V5_SOURCE_ADMISSION','schema':'README_SELECTIONS_V2','projectionPolicy':'README_RECOMMENDATION_V3'}


def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def digest(s):return hashlib.sha256(s.encode('utf-8')).hexdigest()
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def history():
    for name,value in read(OUT/'before.json')['historicalFiles'].items():assert sha(ROOT/name)==value,'Historical file changed: '+name
    ledger=Q/'r5/provider-calls.jsonl';assert len([s for s in ledger.read_text(encoding='utf-8').splitlines() if s.strip()])==35
    assert not (PILOT/'provider-calls.jsonl').exists() and not (PILOT/'authorization.json').exists()


def inputs():
    folder=ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest'
    tests=ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest'
    return list(folder.glob('Readme*.java'))+[tests/(c+'.java') for c in CLASSES]+[Path(__file__).resolve(),ROOT/'scripts/score_readme_quality_v2.py',
        ROOT/'scripts/score_readme_quality_v3.py',ROOT/'scripts/test_readme_quality_v3.py',
        ROOT/'pom.xml',ROOT/'orange-common/pom.xml',ROOT/'orange-pojo/pom.xml',ROOT/'orange-server/pom.xml',
        ROOT/'_experiment_staging/readme-filter-v1-20261029/snapshot.json',Q/'v5-audit-v1/reviewed-labels-v3.json',
        Q/'r4/sources.json',Q/'r4/provider-config.json']


def run():
    history();STAGE.mkdir(parents=True,exist_ok=True)
    write(STAGE/'test-inputs.json',{p.relative_to(ROOT).as_posix():sha(p) for p in inputs()})
    text=(ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeRecommendationProjectionTest.java').read_text(encoding='utf-8')
    methods=re.findall(r'@Test\s+void\s+(\w+)\(',text)
    selected=[c for c in CLASSES if c!='ReadmeRecommendationProjectionTest']+['ReadmeRecommendationProjectionTest#'+'+'.join(methods)]
    command=['docker','run','--rm','--pull','never','--network','none','-e','MAVEN_OPTS=-Xmx512m',
        '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
        'mvn','-o','-B','-pl','orange-server','-am','-Dtest='+','.join(selected),'-Dsurefire.failIfNoSpecifiedTests=false',
        '-DargLine=-Xmx512m','-Dreadme.v5.repair.root=/workspace','-Dreadme.dependency.root=/workspace',
        '-Dreadme.dependency.output=/workspace/_experiment_staging/readme-v5-deterministic-repair-v1/dependency','test']
    with (STAGE/'tests.log').open('w',encoding='utf-8') as log:r=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT)
    if r.returncode==0:
        with (STAGE/'scorer-tests.log').open('w',encoding='utf-8') as log:
            r=subprocess.run([sys.executable,'-m','unittest','discover','-s','scripts','-p','test_readme_quality_v3.py','-v'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    print(json.dumps({'exitCode':r.returncode,'network':'none','log':str(STAGE/'tests.log')}));return r.returncode


def coverage(doc,bid,variant):
    target=[b['cacheKey'] for b in variant['batches'] if bid in b['targetIds']]
    context=[b['cacheKey'] for b in variant['batches'] if bid in b['contextIds']]
    if target:return {'status':'MODEL_TARGET','targeted':True,'targetBatchKeys':target,'contextBatchKeys':context}
    if bid in doc['needsModel']:
        unit=next((u for u in variant['units'] if bid in u['targetIds']),None)
        if unit:status=unit['reason']
        else:
            blocks={b['blockId']:b for b in doc['preview']['blocks']};seen=set();pending=[bid]
            while pending:
                key=pending.pop()
                if key in seen:continue
                seen.add(key);pending.extend(i for i in doc['structuralDependencies'][key] if blocks[i]['kind']!='HEADING')
            hard=[i for i in seen if blocks[i]['decision']=='REVIEW' and i not in doc['softEligible']]
            assert hard,'Unexplained planner omission';status='HARD_DIRECTED_PREREQUISITE'
    else:status='RULE_OR_HARD_REVIEW'
    return {'status':status,'targeted':False,'targetBatchKeys':[],'contextBatchKeys':context}


def report():
    history()
    for n,v in read(STAGE/'test-inputs.json').items():assert sha(ROOT/n)==v,'Changed test input: '+n
    suites=[]
    for cls in CLASSES:
        path=ROOT/f'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.{cls}.xml'
        suite=ET.parse(path).getroot();assert all(int(suite.attrib[k])==0 for k in ['failures','errors','skipped']),cls
        assert path.stat().st_mtime_ns>=(STAGE/'test-inputs.json').stat().st_mtime_ns
        suites.append({'class':cls,'tests':int(suite.attrib['tests'])})
    assert 'BUILD SUCCESS' in (STAGE/'tests.log').read_text(encoding='utf-8')
    assert 'Ran 5 tests' in (STAGE/'scorer-tests.log').read_text(encoding='utf-8') and '\nOK\n' in (STAGE/'scorer-tests.log').read_text(encoding='utf-8')
    assert (STAGE/'scorer-tests.log').stat().st_mtime_ns>=(STAGE/'test-inputs.json').stat().st_mtime_ns
    red=ET.parse(STAGE/'red-tests.xml').getroot();assert int(red.attrib['tests'])==int(red.attrib['failures'])==3 and int(red.attrib['errors'])==0
    corpus=read(STAGE/'corpus-check.json');assert corpus['documents']==303
    assert all(corpus[k]==0 for k in ['sourceMappingErrors','idempotenceErrors','dispositionLeaks','providerCalls','indexWrites'])
    plans=read(STAGE/'all-plans.json');replay=read(STAGE/'replay-results.json');assert plans['versions']==replay['versions']==VERSIONS
    assert plans['providerCalls']==replay['providerCalls']==0 and not replay['newPromptExecuted']
    results={d['documentId']:d['result'] for d in replay['documents']};assert len(results)==12
    sources=read(Q/'r4/sources.json')['documents'];labels=read(Q/'v5-audit-v1/reviewed-labels-v3.json')
    augmented=deepcopy(labels);augmented['versions']=VERSIONS
    write(OUT/'evaluation-149-legacy-scorer.json',evaluate_legacy(augmented,sources,results))
    evaluated=evaluate(augmented,sources,results)
    evaluated.update({'mode':'CURRENT_RULES_WITH_OLD_PROTOCOL_ACCEPTED_SELECTIONS','newPromptExecuted':False,'newProviderCalls':0,'independentValidation':False,
        'annotationAuthority':labels['annotationAuthority'],'labelDatasetSha256':sha(Q/'v5-audit-v1/reviewed-labels-v3.json')})
    write(OUT/'evaluation-149.json',evaluated)
    legacy=read(Q/'r4/frozen-dataset.json');legacy['versions']=VERSIONS
    write(OUT/'evaluation-147.json',evaluate(legacy,sources,results))
    docs={d['source']['documentId']:d for d in plans['documents']}
    old_audit=read(Q/'v5-audit-v1/audit.json')['cases']
    matrix=[{'caseId':c['caseId'],'documentId':c['documentId'],'blockId':c['blockId'],
        'coverage':{v['name']:coverage(docs[c['documentId']],c['blockId'],v) for v in docs[c['documentId']]['variants']}} for c in old_audit]
    from collections import Counter
    write(OUT/'target-coverage.json',{'cases':matrix,'summary':{v:dict(Counter(r['coverage'][v]['status'] for r in matrix)) for v in ['max4','max8']},'planningPolicy':plans['planningPolicy']})
    batches=[]
    for ident in IDS:
        v=next(v for v in docs[ident]['variants'] if v['maxBlocks']==8)
        for at,b in enumerate(v['batches'],1):batches.append({'requestId':f'V15-{ident}-{at:02d}',**b,'outputTokens':2048,'systemSha256':digest(b['system']),'userSha256':digest(b['user'])})
    assert 0<len(batches)<=38
    subset=deepcopy(labels);subset['cases']=[c for c in labels['cases'] if c['documentId'] in IDS];subset['versions']=VERSIONS
    subset['scope']='REGRESSION_PILOT_NOT_INDEPENDENT_ACCEPTANCE';subset['roleMeaning']='Historical label sampling roles; actual targets are the frozen request IDs.'
    subset['documents']=[{'documentId':i,'sourceRef':docs[i]['source']['sourceRef'],'sourceSha256':docs[i]['sourceSha256'],
        'currentRequests':sum(b['documentId']==i for b in batches),'currentModelTargets':sum(len(b['targetIds']) for b in batches if b['documentId']==i)} for i in IDS]
    labelled={c['block']['blockId'] for c in subset['cases']};unlabelled=sorted({i for b in batches for i in b['targetIds']}-labelled)
    assert not unlabelled,'New pilot targets require labels before any request'
    provider=read(Q/'r4/provider-config.json');provider['limits']={'maxInputUpperBound':12000,'maxBlocks':8,'retries':0}
    provider['configurationSource']='Nonsecret reference fingerprint; actual runtime must be revalidated before HTTP'
    write(PILOT/'requests.json',{'versions':VERSIONS,'batches':batches})
    write(PILOT/'frozen-labels.json',subset);write(PILOT/'sources.json',{'documents':[s for s in sources if s['source']['documentId'] in IDS]})
    write(PILOT/'provider-config.json',provider)
    maximum=len(batches)+2
    proposal={'authorized':False,'technicalInputsVerified':True,'readyForExecution':False,'requiresNewBudgetAuthorization':True,
        'repositories':[docs[i]['source']['sourceRef'] for i in IDS],'primaryRequests':len(batches),'reserveRequests':2,'maximumProposedAttempts':maximum,
        'modelTargets':sum(len(b['targetIds']) for b in batches),'labelledCases':len(subset['cases']),'maxBlocks':8,'maxInputUpperBound':12000,'maxOutputTokens':2048,
        'timeoutSeconds':35,'automaticRetries':0,'firstPassReservedUpperBound':sum(b['inputUpperBound']+2048 for b in batches),
        'maximumReservedUpperBound':maximum*(12000+2048),'newLedgerPath':(PILOT/'provider-calls.jsonl').relative_to(ROOT).as_posix(),
        'reservePolicy':'Same request only; one retry each for first two transport/protocol failures; preserve first-pass scores; no semantic resampling.',
        'oldBudgetTransferable':False,'purpose':'Check reachable V5 targets and final admission after deterministic repairs. Existing rule/uncertainty holds remain in denominators.'}
    write(PILOT/'execution-proposal.json',proposal)
    # Independently verify wire inputs and cache identity before declaring this request snapshot prepared.
    for b in batches:
        assert len(b['targetIds'])<=8 and b['inputUpperBound']<=12000
        assert b['inputUpperBound']==len(b['system'].encode())+len(b['user'].encode())+256
        expected='\n'.join([VERSIONS['selectionPolicy'],VERSIONS['prompt'],VERSIONS['schema'],VERSIONS['projectionPolicy'],VERSIONS['rulePolicy'],
                           VERSIONS['parser'],provider['modelIdentity'],'2048',b['system'],b['user']])
        assert digest(expected)==b['cacheKey']
    summary={'status':'V5_DETERMINISTIC_REPAIRS_OFFLINE_VERIFIED','versions':VERSIONS,'tests':sum(s['tests'] for s in suites),'suites':suites,'scorerTests':5,
        'redRegressions':{'tests':3,'failedBeforeRepair':3},'corpus':corpus,'regressionMetrics149':evaluated['metrics'],
        'historicalFilesUnchanged':True,'newProviderCalls':0,'indexWrites':0,'newPromptQuality':'NOT_RUN','pilotProposal':proposal,
        'testInputsSha256':sha(STAGE/'test-inputs.json'),'testLogSha256':sha(STAGE/'tests.log'),'plansSha256':sha(STAGE/'all-plans.json'),
        'requestsSha256':sha(PILOT/'requests.json'),'labelsSha256':sha(PILOT/'frozen-labels.json')}
    write(OUT/'verification.json',summary)
    print(json.dumps({'tests':summary['tests'],'corpus':corpus,'regressionMetrics149':evaluated['metrics'],'pilotProposal':proposal},ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',action='store_true')
    if parser.parse_args().run:raise SystemExit(run())
    report()
