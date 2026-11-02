"""Offline customer gap routing acceptance. No provider keys, database or application startup."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/customer/evidence-gap-v2'
STAGE=ROOT/'_experiment_staging/customer-evidence-gap-v2'
SELECTED=[
    'CustomerEvidenceCoverageTest','CustomerEvidenceDecisionTest','CustomerEvidenceGapConversationTest','CustomerGraphEvidenceGapConversationTest',
    'CustomerEvidenceGapMatchingTest#strictDiscardedUnknownCandidatesKeepTheirFactGapInsteadOfRequestingUserConfirmation+choosingExplorationDoesNotCertifyAnUnknownProjectFactOrGrantImport+failedRequirementVerificationDoesNotCreateATradeoffQuestion',
    'CustomerEvidenceTest','CustomerConversationTest','CustomerGraphConversationRegressionTest','CustomerEvidenceRoutingTest','CustomerGraphEvidenceRoutingTest',
    'CustomerWorkflowV2Test','CustomerWorkflowMatchingTest','CustomerWorkflowGovernanceTest','CustomerRecommendationFitTest','CustomerImportServiceTest',
    'CustomerModelTest','CustomerContextAssemblerTest',
    'CustomerEvidenceGapScopeTest#rejectedUnknownCandidateDoesNotPolluteTheVerifiedPresentedProject+noPresentedProjectStillRetainsTheCandidatePoolsUnknownFacts+knownBeginnerProfileDoesNotBecomeAnotherUserQuestionWhenLearningScopeIsUnknown+legacyCardWithUnknownConditionsIsNotFullySupportedJustBecauseItHasOneCitation']

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def history():
    previous=ROOT/'docs/customer/evidence-gap-v1'
    manifest=read(previous/'verification.json')
    archives=read(OUT/'previous-input-archives.json')
    assert set(archives)==set(manifest['inputs'])
    for name,value in manifest['inputs'].items():assert sha(ROOT/archives[name])==value,name
    for name,value in manifest['outputs'].items():assert sha(ROOT/name)==value,name
    for name,row in read(previous/'before.json').items():assert sha(ROOT/row['archive'])==row['sha256'],name
    subprocess.run([sys.executable,str(ROOT/'scripts/readme_content_query_replay.py'),'verify'],cwd=ROOT,check=True)

def inputs():
    folder='orange-server/src/main/java/com/orangejd/server/customer/'
    main=['CustomerEvidenceCoverage.java','CustomerEvidenceDecision.java','CustomerConversation.java','CustomerEvidenceSteps.java','CustomerEvidence.java',
          'CustomerRequirementMatcher.java','CustomerTaskService.java','CustomerOutcome.java','CustomerTurnInterpreter.java']
    paths=[folder+n for n in main]+['scripts/check_customer_evidence_gap_scope.py','docs/customer/evidence-gap-v2/plan.md','docs/customer/evidence-gap-v2/previous-input-archives.json','docs/customer/evidence-gap-v1/verification.json',
          'orange-web/src/components/CustomerChat.vue','pom.xml','orange-server/pom.xml']
    paths+=['orange-server/src/test/java/com/orangejd/server/customer/'+s.split('#')[0]+'.java' for s in SELECTED]
    return sorted(set(paths))

def run():
    history()
    assert not (OUT/'verification.json').exists(),'This generation is sealed; use --verify or a new generation'
    STAGE.mkdir(parents=True,exist_ok=True)
    hashes={name:sha(ROOT/name) for name in inputs()};write(STAGE/'inputs.json',hashes)
    args=['docker','run','--rm','--pull','never','--network','none','-e','MAVEN_OPTS=-Xmx512m',
          '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
          'mvn','-o','-B','-pl','orange-server','-am','-Dtest='+','.join(SELECTED),'-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m',
          '-Dcustomer.gaps.output=/workspace/_experiment_staging/customer-evidence-gap-v2/responses','test']
    with (STAGE/'tests.log').open('w',encoding='utf-8') as log:process=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    if process.returncode:
        print(json.dumps({'status':'TESTS_FAILED','log':str(STAGE/'tests.log')}));return process.returncode
    tests=0;suites=[]
    for selected in SELECTED:
        cls=selected.split('#')[0];source=ROOT/('orange-server/target/surefire-reports/TEST-com.orangejd.server.customer.'+cls+'.xml')
        attrs=ET.parse(source).getroot().attrib
        assert all(int(attrs[k])==0 for k in ['errors','failures','skipped']),cls
        tests+=int(attrs['tests']);suites.append({'class':cls,'tests':int(attrs['tests'])})
        saved=STAGE/'java-reports'/source.name;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,saved)
    assert all(sha(ROOT/name)==value for name,value in hashes.items()),'Inputs changed while tests were running'
    responses=[]
    for path in sorted((STAGE/'responses').glob('*.json')):
        row=read(path);result=row['result'];decision=result['evidenceDecision']
        responses.append({'file':path.relative_to(ROOT).as_posix(),'message':row['message'],'status':result['status'],
                          'decision':decision['status'],'nextAction':decision['nextAction'],'question':result.get('pendingQuestion',''),
                          'coverage':decision['coverage'],'factCount':len(decision['projectFacts'])})
    assert len(responses)==20, 'Ten behavioral cases must run through both engines'
    write(OUT/'responses.json',responses)
    lines=['# 证据不足分流 V2：范围隔离验收','',f'通过 {tests} 项检查，错误、失败、跳过均为0；20条新行为回复由真实业务路由及LangGraph4j执行器各生成一遍。模型为测试替身，真实外部调用0。','',
           '| 用户消息 | 回复状态 | 缺口分类 | 下一步 | 实际追问 |','|---|---|---|---|---|']
    for r in responses:lines.append(f"| {r['message']} | {r['status']} | {r['decision']} | {r['nextAction']} | {r['question'] or '无'} |")
    lines+=['','另外验证4条范围边界：已排除候选不污染已展示项目；无已展示项目时保留候选池缺口；已知用户基础不因项目学习资料不足而重复追问；旧卡片的一条引用不代表全部必要条件已核实。', '', '前端沿用V1已成功构建的同一源码，本轮校验其构建收据与文件哈希；未部署。', '', '用户条件来源是当前任务的开放问题；项目事实来源是本轮覆盖核验或逐项条件核验。原有任务插问不自动回答或取消，当前纯事实问答不再附带旧问题。',
            '','这轮验证了路由、状态及协议边界，没有验证真实模型的新覆盖判断准确率。短事实现可进入原有生成／核验，真实调用量及延迟需后续单独记录。',
            '','源码已接入，未启动网站后台、未迁移数据库或切换知识索引。复验：`python scripts/check_customer_evidence_gap_scope.py --verify`。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    history()
    files=[p for p in STAGE.rglob('*') if p.is_file()]+[OUT/'responses.json',OUT/'report.md',OUT/'before.json']
    write(OUT/'verification.json',{'tests':tests,'suites':suites,'inputs':hashes,'outputs':{p.relative_to(ROOT).as_posix():sha(p) for p in files},
          'externalModelCalls':0,'embeddingCalls':0,'databaseWrites':0,'productionIndexWrites':0})
    verify();return 0

def verify():
    history();m=read(OUT/'verification.json')
    for name,value in {**m['inputs'],**m['outputs']}.items():assert sha(ROOT/name)==value,name
    print(json.dumps({'status':'CUSTOMER_EVIDENCE_GAPS_VERIFIED','tests':m['tests'],'externalModelCalls':0,'embeddingCalls':0}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--verify',action='store_true');a=p.parse_args()
    try:sys.exit((verify() or 0) if a.verify else run())
    except Exception as error:print(str(error),file=sys.stderr);sys.exit(1)
