"""Model-free verification of project-name-separated body queries, preserving prior sealed experiments."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from readme_chunk_retrieval import covered
from readme_evidence_ranking import previous as foundations

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/readme-filter-v1/ranking-v3'
OLD=ROOT/'docs/rag-b/readme-filter-v1/ranking-v2'
FIRST=ROOT/'docs/rag-b/readme-filter-v1/retrieval-v1'
STAGE=ROOT/'_experiment_staging/readme-ranking-v3'
JAVA='orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeContentQueryReplayTest.java'

def read(p): return json.loads(p.read_text(encoding='utf-8'))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def history():
    foundations()
    archives=read(OUT/'previous-input-archives.json')
    manifest=read(OLD/'experiment-manifest.json')
    for name,digest in manifest['files'].items():
        assert sha(ROOT/archives[name])==digest, name
    for name,digest in read(OLD/'completion-seal.json')['files'].items():
        assert sha(ROOT/name)==digest, name
    assert len((OLD/'local-embedding-calls.jsonl').read_text(encoding='utf-8').splitlines())==29

def maven(live):
    STAGE.mkdir(parents=True,exist_ok=True)
    selected=['ReadmeContentQueryReplayTest#compareBodyQueriesOnFrozenVectorRoutesWithoutAnyEmbeddingRequests','RagBRedisStackIntegrationTest'] if live else [
        'ReadmeContentQueryReplayTest#replayUsesAllTwentyEightUnchangedQuestionsWithoutAModel','RagBContentQueryTest','RagBProjectEvidenceScorerTest',
        'RagBFusionScorerTest','RagBSearchServiceTest','RagBRelevanceGuardTest']
    args=['docker','run','--rm','--pull','never','--network','container:orangejd-readme-retrieval-v1' if live else 'none','-e','MAVEN_OPTS=-Xmx512m']
    if live:args+=['-e','RAG_B_REDIS_TEST_HOST=127.0.0.1','-e','RAG_B_REDIS_TEST_PORT=6379']
    args+=['-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
           'mvn','-o','-B','-pl','orange-server','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m',
           '-Dreadme.content.root=/workspace','-Dtest='+','.join(selected)]
    args+=['-Dreadme.content.live=true','surefire:test'] if live else ['test']
    with (STAGE/('live.log' if live else 'offline.log')).open('w',encoding='utf-8') as log:
        result=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise ValueError('Maven check failed; see '+str(STAGE))
    count=0
    for cls in selected:
        cls=cls.split('#')[0]
        package='service' if cls in ['RagBSearchServiceTest','RagBRelevanceGuardTest'] else 'retrieval'
        p=ROOT/f'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.{package}.{cls}.xml'
        attrs=ET.parse(p).getroot().attrib
        assert all(int(attrs[k])==0 for k in ['failures','errors','skipped']), cls
        saved=STAGE/('live-java-reports' if live else 'offline-java-reports')/p.name
        saved.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,saved);count+=int(attrs['tests'])
    write(STAGE/('live-check.json' if live else 'offline-check.json'),{'tests':count,'failed':0,'skipped':0})

def check():
    m=read(OUT/'experiment-manifest.json')
    for name,value in m['files'].items():assert sha(ROOT/name)==value,name
    assert sha(OLD/'completion-seal.json')==m['previousCompletionSealSha256']

def offline():
    history()
    assert not (OUT/'experiment-manifest.json').exists()
    maven(False)
    names=[JAVA,'scripts/readme_content_query_replay.py','docs/rag-b/readme-filter-v1/ranking-v3/plan.md',
           'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBContentQuery.java',
           'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBRedisStackRepository.java',
           'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBProjectEvidenceScorer.java',
           'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBFusionScorer.java',
           'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBQueryTokenizer.java',
           'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/RagBContentQueryTest.java',
           'scripts/readme_evidence_ranking.py','scripts/readme_chunk_retrieval.py',
           'docs/rag-b/readme-filter-v1/retrieval-v1/cases.json','docs/rag-b/readme-filter-v1/retrieval-v1/corpus.json',
           'docs/rag-b/readme-filter-v1/ranking-v2/retrieval-results.json','docs/rag-b/readme-filter-v1/ranking-v2/evaluation.json',
           'docs/rag-b/readme-filter-v1/ranking-v2/remaining-case-audit.json',
           'docs/rag-b/readme-filter-v1/ranking-v3/previous-input-archives.json']
    write(OUT/'experiment-manifest.json',{'files':{n:sha(ROOT/n) for n in names},'previousCompletionSealSha256':sha(OLD/'completion-seal.json'),
          'newModelCallsAllowed':0,'existingIndexWritesAllowed':0,'temporaryIntegrationIndexWritesAllowed':True})
    print(json.dumps({'status':'CONTENT_QUERY_INPUTS_FROZEN','modelCalls':0}))

def run():
    history();check()
    assert not (OUT/'replay-results.json').exists()
    maven(True)
    report()

def report():
    check();history()
    assert not (OUT/'completion-seal.json').exists()
    cases={c['id']:c for c in read(FIRST/'cases.json')['cases']}
    corpus={c['id']:c for c in read(FIRST/'corpus.json')}
    result=read(OUT/'replay-results.json');assert len(result)==28
    details=[];metrics={};previous=read(OLD/'evaluation.json')
    for variant in ['RAW_STRUCT','KNOWLEDGE_V2']:
        rows=[]
        for q in result:
            c=cases[q['id']];reps=q['variants'][variant]['repetitions'];hits=reps[0]['hits']
            spans=[s for h in hits for s in corpus[h['id']]['expandedSpans']]
            d={'id':c['id'],'variant':variant,'answerable':c['answerable'],'category':c['category'],
               'projectAt1':any(h['documentId'] in c['expectedDocumentIds'] for h in hits[:1]),
               'projectAt5':any(h['documentId'] in c['expectedDocumentIds'] for h in hits),
               'complete':bool(c['evidenceGroups']) and all(any(covered(g,spans) for g in group) for group in c['evidenceGroups']),
               'hitIds':[h['id'] for h in hits],
               'stable':all([h['id'] for h in r['hits']]==[h['id'] for h in hits] for r in reps[1:])}
            details.append(d)
            if c['answerable']:rows.append(d)
        metrics[variant]={'positiveCases':22,'projectAt1':sum(r['projectAt1'] for r in rows),'projectAt5':sum(r['projectAt5'] for r in rows),
                          'evidenceCompleteAt5':sum(r['complete'] for r in rows),'unstableCases':sum(not d['stable'] for d in details if d['variant']==variant)}
    write(OUT/'evaluation.json',{'metrics':metrics,'cases':details,'newEmbeddingCalls':0,'indexWrites':0,'method':'Fresh lexical retrieval on frozen full real vector candidates'})
    lines=['# 项目名与正文关键词分离：验证结果','','固定5仓库、28题、模型、原文、分块和向量，分别比较：原始名称排序、项目内相关性排序、进一步分离正文中的项目名。',
           '','| 分块 | 原始排序证据完整 | 项目内排序证据完整 | 项目名分离后证据完整 | 分离后首位项目命中 |', '|---|---:|---:|---:|---:|']
    for variant,m in metrics.items():
        prev=previous['metrics'][variant]
        lines.append(f"| {variant} | {prev['LEGACY_RRF']['evidenceCompleteAt5']}/22 | {prev['PROJECT_CONTENT']['evidenceCompleteAt5']}/22 | {m['evidenceCompleteAt5']}/22 | {m['projectAt1']}/22 |")
    lines+=['','这轮复用保存的真实完整向量／名称排名，只刷新真实 Redis 正文查询，新增 Embedding/聊天模型调用0次；不能将回放耗时当成新的端到端客服延迟。',
            '','生产 Java Repository 已接入两项修复：取消项目内部的名称序号加分；正文关键词只保留内容词，完整原问题仍用于向量和名称召回。未重启网站后端，未更换网站知识索引。',
            '','回归及实际生产 Repository Redis 集成测试通过，包含真实索引、过滤和并发，所有必需检查无跳过。旧结果、账本和输入源码归档保持可追溯。',
            '','标注来自助手，样本曾用于调试，这属于已知样本回归，不是独立人工评测。22道正例按冻结原文块的严格完整性标准；6道不足题仍可能召回候选，不能因此宣称已实现自动拒答或可靠追问。',
            '','V2源码已归档。复验当前改进使用 `python scripts/readme_content_query_replay.py verify`；旧V2脚本直接比较当前Repository源码会失配，当前复验会按旧哈希检查归档源码和原始产物。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    files=[p for p in OUT.rglob('*') if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT/'completion-seal.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in files},'newEmbeddingCalls':0,'productionWrites':0,'temporaryIntegrationIndexWrites':True})
    print(json.dumps({'metrics':metrics,'newEmbeddingCalls':0,'report':str(OUT/'report.md')}))

def verify():
    check();history()
    for name,value in read(OUT/'completion-seal.json')['files'].items():assert sha(ROOT/name)==value,name
    print(json.dumps({'status':'CONTENT_QUERY_VERIFIED','newEmbeddingCalls':0,'productionWrites':0}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['offline','run','report','verify']);args=p.parse_args()
    try:globals()[args.phase]()
    except Exception as error:print(str(error),file=sys.stderr);sys.exit(1)
