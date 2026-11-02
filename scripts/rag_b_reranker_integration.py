"""Freeze, exercise and verify the actual B retrieval service with pre-TopK Gitee reranking."""
import argparse,collections,hashlib,json,os,pathlib,shutil,subprocess,sys,xml.etree.ElementTree as ET
import rag_b_reranker_eval as previous
ROOT=previous.ROOT
OUT=ROOT/'docs/rag-b/reranker-integration-v1'
STAGE=ROOT/'_experiment_staging/reranker-integration-v1'
read=previous.read
write=previous.write
sha=previous.sha
def history():subprocess.run([sys.executable,str(ROOT/'scripts/verify_rag_b_reranker_history.py')],cwd=ROOT,check=True)
def check():
    for n,h in read(OUT/'manifest.json')['files'].items():assert sha(ROOT/n)==h,n
def freeze():
    history();assert not (OUT/'manifest.json').exists()
    files=[pathlib.Path(__file__),ROOT/'scripts/verify_rag_b_reranker_history.py',OUT/'README.md',
        ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBReranker.java',
        ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBSearchHit.java',
        ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/service/RagBSearchService.java',
        ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/service/RagBRelevanceGuard.java',
        ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/config/RagBRerankProperties.java',
        ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/config/RagBProperties.java',
        ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/service/RagBRerankerIntegrationTest.java',
        ROOT/'docs/rag-b/reranker-v1/cases.json',ROOT/'docs/rag-b/reranker-v1/candidate-snapshot.json',ROOT/'docs/rag-b/reranker-v1/completion-seal.json']
    write(OUT/'manifest.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in files},'maximumInferenceRequests':50,'maximumCases':8,'documentEmbeddingCalls':0,'chatCalls':0,'previousAttempts':['attempt-1/attempt-seal.json','attempt-2/attempt-seal.json']})
def run():
    check();history();assert not (OUT/'live-started.json').exists()
    cfg=subprocess.run(['docker','compose','--env-file','.env.local','-f','compose.local.yml','config','--format','json'],cwd=ROOT,capture_output=True,text=True,encoding='utf8',check=True)
    envs=json.loads(cfg.stdout)['services']['backend']['environment'];env={**os.environ,**envs}
    env.update({'RUN_RERANK_INTEGRATION':'true','RAG_B_RERANK_ENABLED':'true','CUSTOMER_ENABLED':'false','RAG_B_GITHUB_SYNC_ENABLED':'false','INGESTION_PIPELINE_V2_ENABLED':'false','AI_TOOL_POOL_ENABLED':'false','SPRING_PROFILES_ACTIVE':'dev','MAVEN_OPTS':'-Xmx768m'})
    args=['docker','run','--rm','--pull','never','--network','nxz-bigorange_default']
    for k in set(envs)|{'RUN_RERANK_INTEGRATION','SPRING_PROFILES_ACTIVE','MAVEN_OPTS'}:args+=['-e',k]
    args+=['-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21','mvn','-o','-B','-pl','orange-server','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx768m','-Drerank.root=/workspace','-Dorange.acceptance.limit=50','-Dorange.acceptance.ledger=/workspace/docs/rag-b/reranker-integration-v1/inference-calls.jsonl','-Dtest=RagBRerankerIntegrationTest','test']
    STAGE.mkdir(parents=True,exist_ok=True)
    with (STAGE/'live.log').open('w',encoding='utf8') as f:r=subprocess.run(args,cwd=ROOT,env=env,stdout=f,stderr=subprocess.STDOUT)
    assert r.returncode==0,'Integration failed: '+str(STAGE/'live.log')
    report=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.service.RagBRerankerIntegrationTest.xml';a=ET.parse(report).getroot().attrib
    assert int(a['tests'])==1 and all(int(a[k])==0 for k in ['failures','errors','skipped']);shutil.copyfile(report,STAGE/'live.xml');summarize()
def summarize():
    check();history();assert not (OUT/'completion-seal.json').exists()
    cases={c['key']:c for c in read(ROOT/'docs/rag-b/reranker-v1/cases.json')['cases']};old={r['key']:r['baseline'] for r in read(ROOT/'docs/rag-b/reranker-v1/candidate-snapshot.json')}
    rows=read(OUT/'live-results.json');assert len(rows)==8;details=[]
    for row in rows:
        key=row['key'];c=cases[key];before=old[key];after=row['result']
        d={'key':key,'stratum':c['stratum'],'rerank':after['rerank'],'policyBefore':before['answerPolicy'],'policyAfter':after['answerPolicy'],'expectedPolicy':c['expectedPolicy']}
        for side,result in [('before',before),('after',after)]:
            d[side]={'sourceRank':previous.rank(c,result['hits']),'evidenceRank':previous.rank(c,result['hits'],True)}
        details.append(d)
    metrics={}
    for group in ['SEMANTIC','NAMED']:
        subset=[d for d in details if d['stratum']==group];m={'cases':len(subset)}
        for side in ['before','after']:
            m[side]={}
            for target in ['source','evidence']:
                values=[d[side][target+'Rank'] for d in subset]
                m[side][target]={**{'hitAt'+str(k):sum(0<r<=k for r in values) for k in [1,3,5,8]},'mrr8':sum(1/r for r in values if r)/len(subset)}
        metrics[group]=m
    guards=[d for d in details if d['stratum']=='GUARD'];metrics['GUARD']={'cases':len(guards),'before':sum(d['policyBefore']==d['expectedPolicy'] for d in guards),'after':sum(d['policyAfter']==d['expectedPolicy'] for d in guards)}
    ledger=[json.loads(x) for x in (OUT/'inference-calls.jsonl').read_text(encoding='utf8').splitlines()];counts=dict(collections.Counter(x['kind'] for x in ledger));assert len(ledger)<=50
    write(OUT/'evaluation.json',{'metrics':metrics,'cases':details,'requests':counts})
    lines=['# B版正式检索服务接入Reranker：验收','','从旧24题中选取8道接入回归题与相同活动索引，当前实现改为：混合召回 → 业务过滤 → 去重 → 最多80条模型分批重排 → 多样性与TopK → 证据策略。每批20条，共享5秒截止时间，任一批失败整体回退。','',
        '| 语义题指标 /4 | 原B版 | 接入后 |','|---|---:|---:|']
    for t,label in [('source','目标项目'),('evidence','关键原文')]:
        for k in [1,3,5,8]:lines.append(f"| {label}@{k} | {metrics['SEMANTIC']['before'][t]['hitAt'+str(k)]} | {metrics['SEMANTIC']['after'][t]['hitAt'+str(k)]} |")
    lines+=['',f"业务策略匹配：原B {metrics['GUARD']['before']}/{len(guards)}，接入后 {metrics['GUARD']['after']}/{len(guards)}。两道禁用开关回归与旧结果完全一致。",'',
      '## 逐题','','| 题号 | 原文排名：原B | 原文排名：接入后 | 重排状态 | 候选数 | 模型处理数 |','|---|---:|---:|---|---:|---:|']
    for d in details:lines.append(f"| {d['key']} | {d['before']['evidenceRank'] or '未命中'} | {d['after']['evidenceRank'] or '未命中'} | {d['rerank']['status']} {d['rerank']['fallbackReason']} | {d['rerank']['candidateCount']} | {d['rerank']['rerankedCount']} |")
    lines+=['','接口超时、无凭据、无效响应、并发繁忙时回退原排序；HTTP不重试。原RRF分数保留在score，模型分数另存rerankScore。','',
      '本轮使用已有助手标注题与字面原文锚点，未测试最终生成答案、未证明完整事实覆盖或全场景准确率。旧基线来自同一索引的上轮快照，并以两个当前禁用开关查询验证未漂移。',
      '', '本轮推理请求：'+json.dumps(counts,ensure_ascii=False)+'；无文档重新向量化、无聊天调用、无业务索引切换。',
      '', '运行说明见README.md；旧实验通过python scripts/verify_rag_b_reranker_history.py验证，使用接入前的原始代码快照，原始材料不改写。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    paths=[p for p in OUT.rglob('*') if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT/'completion-seal.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths},'requests':counts,'cases':8,'previousFailedAttemptRequests':84});verify()
def verify():
    check();history()
    for n,h in read(OUT/'completion-seal.json')['files'].items():assert sha(ROOT/n)==h,n
    print(json.dumps({'status':'B_RERANK_INTEGRATION_VERIFIED','cases':8,'requests':read(OUT/'completion-seal.json')['requests']}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('phase',choices=['freeze','run','summarize','verify']);a=p.parse_args()
    try:globals()[a.phase]()
    except Exception as ex:print(str(ex),file=sys.stderr);sys.exit(1)
