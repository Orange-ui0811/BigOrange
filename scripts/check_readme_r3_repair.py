"""Offline R3 repair verification. Historical R5 evidence is preserved, not overwritten/rescored."""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from score_readme_quality_v2 import evaluate

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2/r3-repair-v2'
STAGE=ROOT/'_experiment_staging/readme-r3-repair-v2'
PKG='com.orangejd.server.ragb.ingest.'
CLASSES=['ReadmeKnowledgeFilterTest','ReadmeRecommendationProjectionTest','ReadmeDependencyClosureTest',
         'ReadmeModelReviewTest','ReadmeSpringAiReviewTransportTest','RagBTokenChunkerTest','ReadmeR3RepairTest','ReadmeR3RepairReplayTest']
VERSIONS={'parser':'CONTIGUOUS_MARKDOWN_BLOCKS_V1','rulePolicy':'README_RULE_PREVIEW_V1_4',
          'selectionPolicy':'README_MODEL_REVIEW_V1_4','prompt':'README_SELECTOR_ZH_V5_SOURCE_ADMISSION',
          'schema':'README_SELECTIONS_V2','projectionPolicy':'README_RECOMMENDATION_V2'}


def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def history():
    before=read(OUT/'before.json')
    for name,digest in before['historicalFiles'].items():
        assert sha(ROOT/name)==digest,'Historical evidence changed: '+name
    for name,entry in before['mutableSourceBaseline'].items():
        assert sha(ROOT/entry['archive'])==entry['sha256'],'Archived source changed: '+name
    # Frozen manifests intentionally refer to the old source. Resolve only explicitly archived source paths.
    old=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2/r4'
    frozen={**read(old/'r4-seal.json')['files'],**read(old/'freeze-manifest.json')['files'],**read(old/'freeze-manifest.json')['historicalFiles']}
    for name,digest in frozen.items():
        path=ROOT/before['mutableSourceBaseline'][name]['archive'] if name in before['mutableSourceBaseline'] else ROOT/name
        assert sha(path)==digest,'Historical source/evidence mismatch: '+name
    ledger=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2/r5/provider-calls.jsonl'
    assert len([s for s in ledger.read_text(encoding='utf-8').splitlines() if s.strip()])==35
    return before


def inputs():
    base=ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest'
    tests=ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest'
    return list(base.glob('Readme*.java'))+[tests/(c+'.java') for c in CLASSES]+[
        ROOT/'pom.xml',ROOT/'orange-common/pom.xml',ROOT/'orange-pojo/pom.xml',ROOT/'orange-server/pom.xml',
        ROOT/'_experiment_staging/readme-filter-v1-20261029/snapshot.json',Path(__file__).resolve(),
        ROOT/'scripts/score_readme_quality_v2.py',ROOT/'scripts/test_readme_quality_v2.py']


def run():
    history();STAGE.mkdir(parents=True,exist_ok=True)
    write(STAGE/'test-inputs.json',{p.relative_to(ROOT).as_posix():sha(p) for p in inputs()})
    # The old corpus test has a fixed historical output destination. Its replacement runs below in a new directory.
    text=(ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeRecommendationProjectionTest.java').read_text(encoding='utf-8')
    methods=re.findall(r'@Test\s+void\s+(\w+)\(',text)
    selected=[c for c in CLASSES if c!='ReadmeRecommendationProjectionTest']+['ReadmeRecommendationProjectionTest#'+'+'.join(methods)]
    command=['docker','run','--rm','--pull','never','--network','none','-e','MAVEN_OPTS=-Xmx512m',
        '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
        'mvn','-o','-B','-pl','orange-server','-am','-Dtest='+','.join(selected),'-Dsurefire.failIfNoSpecifiedTests=false',
        '-DargLine=-Xmx512m','-Dreadme.repair.root=/workspace','-Dreadme.dependency.root=/workspace',
        '-Dreadme.dependency.output=/workspace/_experiment_staging/readme-r3-repair-v2/dependency','test']
    with (STAGE/'tests.log').open('w',encoding='utf-8') as out:
        result=subprocess.run(command,stdout=out,stderr=subprocess.STDOUT)
    print(json.dumps({'exitCode':result.returncode,'network':'none','log':str(STAGE/'tests.log')}))
    return result.returncode


def report():
    history()
    for name,digest in read(STAGE/'test-inputs.json').items():assert sha(ROOT/name)==digest,'Input changed since tests: '+name
    suites=[]
    for cls in CLASSES:
        path=ROOT/f'orange-server/target/surefire-reports/TEST-{PKG}{cls}.xml'
        suite=ET.parse(path).getroot()
        assert all(int(suite.attrib[k])==0 for k in ['failures','errors','skipped']),cls
        assert path.stat().st_mtime_ns>=(STAGE/'test-inputs.json').stat().st_mtime_ns
        suites.append({'class':cls,'tests':int(suite.attrib['tests'])})
    assert 'BUILD SUCCESS' in (STAGE/'tests.log').read_text(encoding='utf-8')
    corpus=read(STAGE/'corpus-check.json');assert corpus['documents']==303
    assert all(corpus[k]==0 for k in ['sourceMappingErrors','dispositionLeaks','idempotenceErrors','additionalProviderCalls','indexWrites'])
    data=deepcopy(read(ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2/r4/frozen-dataset.json'))
    data['versions']=VERSIONS # Compare new outputs with their explicit new implementation; labels/denominators are unchanged.
    replay=read(STAGE/'replay-results.json')
    results={d['documentId']:d['result'] for d in replay['documents']}
    sources=read(ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2/r4/sources.json')['documents']
    evaluated=evaluate(data,sources,results)
    evaluated.update({'mode':replay['mode'],'newModelCalls':0,'newPromptEvaluated':False,'versions':VERSIONS,
        'labelsUnchanged':True,'independentValidation':False,'oldAcceptedSelections':replay['oldAcceptedSelections'],
        'reusedSelections':replay['reusedSelections'],'supersededByFixedRule':replay['supersededByFixedRule']})
    write(OUT/'offline-replay-evaluation.json',evaluated)
    summary={'status':'R3_REPAIR_V2_OFFLINE_VERIFIED','tests':sum(s['tests'] for s in suites),'suites':suites,
        'corpus':corpus,'versions':VERSIONS,'newProviderCalls':0,'historicalR5Calls':35,'indexWrites':0,
        'historicalEvidenceUnchanged':True,'oldSourceArchived':True,'replayMetrics':evaluated['metrics'],
        'newPromptQuality':'NOT_RUN','independentQualityGate':'NOT_RUN',
        'testInputsSha256':sha(STAGE/'test-inputs.json'),'testLogSha256':sha(STAGE/'tests.log'),
        'replaySha256':sha(STAGE/'replay-results.json')}
    write(OUT/'verification.json',summary)
    print(json.dumps({'tests':summary['tests'],'newProviderCalls':0,'replayMetrics':evaluated['metrics'],'independentQualityGate':'NOT_RUN'},ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',action='store_true')
    if parser.parse_args().run:raise SystemExit(run())
    report()
