"""Finish a failed retrieval run using its immutable, already acquired vectors. Zero inference."""
import argparse
import json
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import readme_three_arm as e

OUT=e.OUT
STAGE=e.STAGE
ROOT=e.ROOT

def freeze():
    e.check();e.history();assert not (OUT/'repair-manifest.json').exists()
    assert not (OUT/'retrieval-results.json').exists(),'Scope repair must precede observing rankings'
    assert len((OUT/'local-embedding-calls.jsonl').read_text().splitlines())==36
    paths=[ROOT/'scripts/readme_three_arm_repair.py',ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeThreeArmReplayTest.java',
           OUT/'repair-plan.md',OUT/'vectors.json',OUT/'local-embedding-calls.jsonl',OUT/'live-started.json',OUT/'experiment-manifest.json',STAGE/'live.log']
    e.write(OUT/'repair-manifest.json',{'files':{p.relative_to(ROOT).as_posix():e.sha(p) for p in paths},'newInferenceRequests':0,'oldLocalInferenceRequests':36})

def check():
    e.check()
    for name,digest in e.read(OUT/'repair-manifest.json')['files'].items():assert e.sha(ROOT/name)==digest,name

def run():
    check();assert not (OUT/'cached-replay-started.json').exists(),'No overwrite or silent retry'
    args=['docker','run','--rm','--pull','never','--network','container:'+e.CONTAINER,'-e','MAVEN_OPTS=-Xmx512m',
          '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
          'mvn','-o','-B','-pl','orange-server','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m',
          '-Dthree.arm.root=/workspace','-Dthree.arm.live=true','-Dorange.acceptance.limit=36',
          '-Dorange.acceptance.ledger=/workspace/docs/rag-b/readme-filter-v1/retrieval-three-arm-v1/local-embedding-calls.jsonl',
          '-Dtest=ReadmeThreeArmReplayTest#replayCachedVectorsWithoutAnyProvider','test']
    with (STAGE/'cached-replay.log').open('w',encoding='utf8') as log:r=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    assert r.returncode==0,'Cached replay failed: '+str(STAGE/'cached-replay.log')
    p=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.retrieval.ReadmeThreeArmReplayTest.xml'
    a=ET.parse(p).getroot().attrib
    assert int(a['tests'])==1 and all(int(a[k])==0 for k in ['failures','errors','skipped'])
    shutil.copyfile(p,STAGE/'cached-replay-java-report.xml');check()
    e.report();verify()

def verify():
    check();e.verify()
    assert e.read(OUT/'live-completion.json')['localEmbeddingRequests']==0
    assert len((OUT/'local-embedding-calls.jsonl').read_text().splitlines())==36
    print(json.dumps({'status':'THREE_ARMS_AND_REPAIR_VERIFIED','localEmbeddingRequestsTotal':36,'repairInferenceRequests':0,'realCases':40,'syntheticCases':12}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['freeze','run','verify']);a=p.parse_args()
    try:globals()[a.phase]()
    except Exception as ex:print(str(ex),file=sys.stderr);sys.exit(1)
