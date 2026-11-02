"""Compare legacy and project-content scoring on identical frozen inputs and all real retrieval routes."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from readme_chunk_retrieval import covered, percentile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/rag-b/readme-filter-v1/ranking-v2'
STAGE = ROOT / '_experiment_staging/readme-ranking-v2'
OLD = ROOT / 'docs/rag-b/readme-filter-v1/retrieval-v1'
CONTAINER = 'orangejd-readme-retrieval-v1'
JAVA = 'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeEvidenceRankingAcceptanceTest.java'
POLICIES = ['LEGACY_RRF', 'PROJECT_CONTENT', 'SCOPED_CONTENT']


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def write(p, value):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def previous():
    subprocess.run([sys.executable, str(ROOT / 'scripts/readme_chunk_retrieval.py'), 'verify'], check=True, cwd=ROOT)
    a = read(OLD / 'analysis-seal.json')
    assert sha(OLD / 'completion-seal.json') == a['primaryCompletionSealSha256']
    for name, value in a['files'].items():
        assert sha(ROOT / name) == value


def check():
    m = read(OUT / 'experiment-manifest.json')
    assert sha(OLD / 'completion-seal.json') == m['previousCompletionSealSha256']
    for name, value in m['files'].items():
        if sha(ROOT / name) != value:
            raise ValueError('Frozen ranking input changed: ' + name)


def maven(live):
    STAGE.mkdir(parents=True, exist_ok=True)
    args = ['docker', 'run', '--rm', '--pull', 'never', '--network', 'container:' + CONTAINER if live else 'none',
            '-e', 'MAVEN_OPTS=-Xmx512m']
    if live:
        args += ['-e', 'RAG_B_REDIS_TEST_HOST=127.0.0.1', '-e', 'RAG_B_REDIS_TEST_PORT=6379']
    args += ['-v', str(ROOT) + ':/workspace', '-v', 'nxz-bigorange_orangejd-maven-cache:/root/.m2', '-w', '/workspace',
             'maven:3.9.9-eclipse-temurin-21', 'mvn', '-o', '-B', '-pl', 'orange-server', '-am',
             '-Dsurefire.failIfNoSpecifiedTests=false', '-DargLine=-Xmx512m', '-Dreadme.ranking.root=/workspace']
    if live:
        selected = ['ReadmeEvidenceRankingAcceptanceTest#runAllPoliciesOnIdenticalFullRoutesWithoutReembeddingDocuments', 'RagBRedisStackIntegrationTest']
        args += ['-Dtest=' + ','.join(selected), '-Dreadme.ranking.live=true', '-Dorange.acceptance.limit=30',
                 '-Dorange.acceptance.ledger=/workspace/docs/rag-b/readme-filter-v1/ranking-v2/local-embedding-calls.jsonl', 'surefire:test']
    else:
        selected = ['ReadmeEvidenceRankingAcceptanceTest#freezesTheSameQueriesAndKnowledgeAndValidatesBothScoringPolicies',
                    'RagBProjectEvidenceScorerTest', 'RagBFusionScorerTest', 'RagBSearchServiceTest', 'RagBRelevanceGuardTest']
        args += ['-Dtest=' + ','.join(selected), 'test']
    with (STAGE / ('live.log' if live else 'offline.log')).open('w', encoding='utf-8') as log:
        result = subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise ValueError('Checks failed: ' + str(STAGE / ('live.log' if live else 'offline.log')))
    tests = 0
    for cls in selected:
        cls = cls.split('#')[0]
        report = ROOT / ('orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.'
                         + ('service.' if cls in ['RagBSearchServiceTest', 'RagBRelevanceGuardTest'] else 'retrieval.') + cls + '.xml')
        attrs = ET.parse(report).getroot().attrib
        if any(int(attrs[k]) for k in ('failures', 'errors', 'skipped')):
            raise ValueError('Required checks failed or skipped: ' + cls)
        dest = STAGE / ('live-java-reports' if live else 'offline-java-reports') / report.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(report, dest)
        tests += int(attrs['tests'])
    write(STAGE / ('live-check.json' if live else 'offline-check.json'), {'tests': tests, 'failures': 0, 'skipped': 0})


def offline():
    previous()
    if (OUT / 'experiment-manifest.json').exists():
        raise ValueError('This generation is already frozen')
    maven(False)
    names = [JAVA, 'scripts/readme_evidence_ranking.py', 'docs/rag-b/readme-filter-v1/ranking-v2/plan.md',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBProjectEvidenceScorer.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBRedisStackRepository.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBFusionScorer.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBQueryTokenizer.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBOllamaEmbeddingModel.java',
             'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/RagBProjectEvidenceScorerTest.java',
             'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/RagBRedisStackIntegrationTest.java',
             'scripts/readme_chunk_retrieval.py', 'docs/rag-b/readme-filter-v1/retrieval-v1/cases.json',
             'docs/rag-b/readme-filter-v1/retrieval-v1/corpus.json']
    write(OUT / 'experiment-manifest.json', {'files': {n: sha(ROOT / n) for n in names}, 'maximumLocalInferenceRequests': 30,
          'plannedLocalInferenceRequests': 29, 'cloudRequestsAllowed': 0, 'existingExperimentIndexWritesAllowed': 0,
          'productionIndexWritesAllowed': 0, 'temporaryIntegrationIndexWritesAllowed': True,
          'previousCompletionSealSha256': sha(OLD / 'completion-seal.json')})
    print(json.dumps({'status': 'RANKING_INPUTS_FROZEN', 'cases': 28, 'policies': 3, 'localInferencePlanned': 29}))


def run():
    previous()
    check()
    if (OUT / 'live-started.json').exists():
        raise ValueError('Acquisition already started; no hidden repeat or ledger reset')
    container = json.loads(subprocess.check_output(['docker', 'inspect', CONTAINER], encoding='utf-8'))[0]
    assert container['State']['Running'] and container['Config']['Labels']['orangejd.experiment'] == 'readme-retrieval-v1-20261031'
    assert not container['HostConfig'].get('PortBindings')
    maven(True)
    report()


def report():
    check()
    assert not (OUT / 'completion-seal.json').exists(), 'Results already sealed; use verify'
    cases = {c['id']: c for c in read(OLD / 'cases.json')['cases']}
    corpus = {c['id']: c for c in read(OLD / 'corpus.json')}
    results = read(OUT / 'retrieval-results.json')
    assert len(results) == 28
    metrics, details = {}, []
    for variant in ['RAW_STRUCT', 'KNOWLEDGE_V2']:
        metrics[variant] = {}
        for policy in POLICIES:
            rows = []
            for result in results:
                case = cases[result['id']]
                repetitions = result['variants'][variant]
                assert len(repetitions) == 3
                hits = repetitions[0]['policies'][policy]['hits']
                spans = [s for h in hits for s in corpus[h['id']]['expandedSpans']]
                direct = [s for h in hits for s in corpus[h['id']]['sourceSpans']]
                detail = {'id': result['id'], 'variant': variant, 'policy': policy, 'category': case['category'], 'answerable': case['answerable'],
                          'projectAt1': any(h['documentId'] in case['expectedDocumentIds'] for h in hits[:1]),
                          'projectAt5': any(h['documentId'] in case['expectedDocumentIds'] for h in hits),
                          'directComplete': bool(case['evidenceGroups']) and all(any(covered(g, direct) for g in group) for group in case['evidenceGroups']),
                          'complete': bool(case['evidenceGroups']) and all(any(covered(g, spans) for g in group) for group in case['evidenceGroups']),
                          'hitIds': [h['id'] for h in hits],
                          'stableAcrossThreeRuns': all([h['id'] for h in r['policies'][policy]['hits']] == [h['id'] for h in hits] for r in repetitions[1:])}
                details.append(detail)
                if case['answerable']:
                    rows.append(detail)
            timing = [r['timing']['sharedRetrievalMs'] + r['policies'][policy]['fusionMs'] for q in results for r in q['variants'][variant]]
            metrics[variant][policy] = {'positiveCases': 22, 'projectAt1': sum(r['projectAt1'] for r in rows), 'projectAt5': sum(r['projectAt5'] for r in rows),
                                        'evidenceCompleteAt5': sum(r['complete'] for r in rows), 'retrievalP50Ms': percentile(timing,.5), 'retrievalP95Ms': percentile(timing,.95)}
    ledger = [json.loads(r) for r in (OUT / 'local-embedding-calls.jsonl').read_text(encoding='utf-8').splitlines()]
    assert len(ledger) == 29 and [r['attempt'] for r in ledger] == list(range(1,30)) and all(r['kind']=='OLLAMA_EMBEDDING' for r in ledger)
    write(OUT / 'evaluation.json', {'metrics': metrics, 'cases': details, 'localInferenceRequests': len(ledger), 'cloudRequests': 0,
                                  'evaluationScope': 'Same assistant-labeled, tuned 28-case set; no independent answer evaluation'})
    lines = ['# 项目内证据排序：固定候选消融验证', '',
             '同一批 5 仓库、同样 86 个原文块和 28 个知识块、同样 28 题。保留原模型与索引，每次召回的完整候选同时应用三种评分，避免模型或分块变化混入排序效果。', '',
             '| 分块 | 排序 | 首位项目命中 | 前5块项目命中 | 前5块证据完整 | 检索P50 / P95 ms |', '|---|---|---:|---:|---:|---:|']
    for variant, policies in metrics.items():
        for policy, m in policies.items():
            lines.append(f"| {variant} | {policy} | {m['projectAt1']}/22 | {m['projectAt5']}/22 | {m['evidenceCompleteAt5']}/22 | {m['retrievalP50Ms']:.1f} / {m['retrievalP95Ms']:.1f} |")
    lines += ['', 'LEGACY_RRF 保留原名称排名加分；PROJECT_CONTENT 只保留每个显式匹配项目相同的 0.1 加分，项目内部由向量与正文关键词排序。SCOPED_CONTENT 额外只保留候选中显式匹配的项目，用于实验对照，本轮不将硬范围过滤接入网站。', '',
              '生产 Java Redis 检索代码已接入 PROJECT_CONTENT_RRF_V1；本轮没有重启网站后端或切换活动索引。直接调用真实生产 Repository 的 Redis 集成测试另外通过，包含过滤与并发检索。', '',
              '这不是完整客服准确率：Top5 按片段计数；22 道正例按助手冻结原文块的严格完整性标准评分。6 道缺证据题只用于观察返回候选，未实现自动判断全部事实充分，也未生成回答。', '',
              '本轮复用旧索引向量，正文 Embedding 0 次，仅本地预热与28题查询共29次推理，使用新账本；云端调用0。查询向量保存供后续离线消融。', '',
              '检索耗时不包含查询 Embedding、模型回答、账号或项目资格校验；只有5仓库，不能推断全库吞吐。旧问题、标签、分类、分块、首轮结果与账本保持原样。', '',
              '| 题号 | 排序 | 新知识组证据完整 |', '|---|---|---|']
    for d in details:
        if d['variant']=='KNOWLEDGE_V2' and d['answerable']:
            lines.append(f"| {d['id']} | {d['policy']} | {d['complete']} |")
    (OUT / 'report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    previous()
    files=[p for p in OUT.iterdir() if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT / 'completion-seal.json', {'files': {p.relative_to(ROOT).as_posix():sha(p) for p in files}, 'localInferenceRequests':29,'cloudRequests':0,
          'existingExperimentIndexWrites':0,'productionIndexWrites':0,'temporaryIntegrationIndexWrites':True})
    print(json.dumps({'metrics':metrics,'localInferenceRequests':29,'report':str(OUT/'report.md')}))


def verify():
    check()
    for name, value in read(OUT / 'completion-seal.json')['files'].items():
        assert sha(ROOT / name)==value, name
    previous()
    print(json.dumps({'status':'RANKING_VERIFIED','localInferenceRequests':29,'cloudRequests':0,'existingExperimentIndexWrites':0,'productionIndexWrites':0}))


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('phase',choices=['offline','run','report','verify'])
    args=p.parse_args()
    try:
        globals()[args.phase]()
    except Exception as error:
        print(str(error),file=sys.stderr)
        sys.exit(1)
