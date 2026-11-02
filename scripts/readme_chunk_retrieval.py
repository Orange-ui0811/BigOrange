"""Frozen, local-only retrieval experiment. Does not read keys or connect to application databases."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/rag-b/readme-filter-v1/retrieval-v1'
STAGE = ROOT / '_experiment_staging/readme-retrieval-v1'
PILOT = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3'
JAVA = 'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeChunkRetrievalAcceptanceTest.java'
CONTAINER = 'orangejd-readme-retrieval-v1'
RUN = 'readme-retrieval-v1-20261031'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def check_previous():
    subprocess.run([sys.executable, str(ROOT / 'scripts/check_readme_chunk_v2.py'), '--verify'], check=True, cwd=ROOT)


def prepare():
    check_previous()
    target = OUT / 'cases.json'
    if target.exists():
        raise ValueError('Cases already frozen; use offline/run/report/verify phases')
    documents = {row['source']['documentId']: row for row in read(PILOT / 'sources.json')['documents']}
    ready_ids = {}
    for doc in documents:
        preview = read(ROOT / ('docs/rag-b/readme-filter-v1/chunk-v2/previews/' + doc + '.json'))
        ready_ids[doc] = {s['blockId'] for u in preview['units'] if u['status'] == 'READY' for s in u['sourceSpans']}
    specs = [
        ('想要可以与文档对话、支持多用户的 AI 应用。', '240', ['与您的文档对话'], 'NAMELESS'),
        ('Flowise 安装需要哪个版本的 Node.js？', '244', ['版本 >= 20.0.0'], 'VERSION'),
        ('Flowise 如何通过 npm 安装并启动？', '244', ['npm install -g flowise', 'npx flowise start', '版本 >= 20.0.0'], 'PROCEDURE'),
        ('Flowise 本地 Docker 镜像怎么构建和运行？', '244', ['docker build --no-cache', 'docker run -d --name flowise'], 'PROCEDURE'),
        ('Flowise Docker Compose 配置环境文件后如何启动？', '244', ['docker compose up -d'], 'PROCEDURE'),
        ('Flowise 后端和前端分别用什么技术，第三方节点在哪个模块？', '244', ['`server`：Node 后端'], 'ARCHITECTURE'),
        ('Flowise 源码如何安装依赖并构建？', '244', ['npm i -g pnpm', 'pnpm install', 'pnpm build'], 'PROCEDURE'),
        ('Flowise 构建报错 134，Windows PowerShell 怎么调大 Node 堆内存？', '244', ['错误代码 134', '$env:NODE_OPTIONS='], 'CONDITION'),
        ('Appsmith 可以搭建管理后台和仪表盘吗？', '522', ['Organizations build custom applications'], 'PURPOSE'),
        ('Appsmith 使用什么开源协议？', '522', ['Appsmith is licensed under'], 'LICENSE'),
        ('Docguard 是做什么的？', '546', ['A concurrent Go CLI'], 'PURPOSE'),
        ('Docguard 有外部依赖吗？', '546', ['Zero external dependencies'], 'DEPENDENCY'),
        ('Docguard 构建需要什么 Go 版本，怎么构建？', '546', ['go build -o docguard', 'Requires Go 1.22+'], 'VERSION_PROCEDURE'),
        ('Docguard 如何让 CI 对 medium 级别告警失败？', '546', ['docguard --fail-on medium', 'Exit code is `1`'], 'PROCEDURE'),
        ('Docguard 默认扫描哪些文件扩展名？', '546', ['default: txt,md,mdx'], 'OPTIONS'),
        ('Docguard 如何并发扫描，默认有多少 worker？', '546', ['walks the target directory', 'Number of concurrent worker'], 'ARCHITECTURE'),
        ('Docguard 是否保证检测到的都是攻击？', '546', ['This is a heuristic tripwire'], 'LIMITATION'),
        ('Docguard 支持输出 JSON 吗，怎么使用？', '546', ['docguard --json ./docs'], 'PROCEDURE'),
        ('Flowise 需要多少内存才能生产运行？', '244', [], 'INSUFFICIENT_RESOURCE_REQUIREMENT'),
        ('Flowise 的 VITE_PORT 开发环境怎么配置？', '244', [], 'WITHHELD_REVIEW_EVIDENCE'),
        ('n8n 社区版与企业版分别允许什么商业用途？', '427', [], 'WITHHELD_MIXED_LICENSE'),
        ('AnythingLLM 有没有明确的最低 CPU 与内存要求？', '240', [], 'INSUFFICIENT_RESOURCE_REQUIREMENT'),
        ('能推荐一个 Java Spring Boot 两周完成的商城吗？', None, [], 'ABSENT_PROJECT'),
        ('Appsmith 的单点登录是免费社区版自带的吗？', '522', [], 'UNVERIFIED_FEATURE_EDITION'),
        ('想找一个 Go 写的命令行工具，预先扫描知识库中的隐藏提示注入。', '546', ['A concurrent Go CLI'], 'NAMELESS'),
        ('想要一个开源低代码平台搭建企业内部管理后台。', '522', ['Organizations build custom applications'], 'NAMELESS'),
        ('想找一个前端 React、后端 Node 并能扩展第三方节点的工具。', '244', ['`server`：Node 后端'], 'NAMELESS'),
        ('有没有能够在知识入库前接入 CI 的静态扫描工具？', '546', ['is a single static binary'], 'NAMELESS'),
    ]
    cases = []
    for number, (query, doc, markers, category) in enumerate(specs, 1):
        groups = []
        for marker in markers:
            blocks = [b for b in documents[doc]['blocks'] if marker in b['text'] and b['blockId'] in ready_ids[doc]]
            if not blocks:
                raise ValueError('Missing source marker: ' + marker)
            # Equivalent repeated commands are alternative source references; none is synthesized.
            refs = [{'blockId': b['blockId'], 'documentId': doc, 'startUtf16': b['startUtf16'], 'endUtf16': b['endUtf16'],
                     'startLine': b['startLine'], 'endLine': b['endLine'], 'text': b['text']} for b in blocks]
            groups.append(refs)
        cases.append({'id': f'R{number:02}', 'query': query, 'category': category, 'answerable': bool(markers),
                      'expectedDocumentIds': [doc] if markers else [], 'targetDocumentId': doc, 'evidenceGroups': groups})
    write(target, {'annotation': 'Assistant-labeled, known/tuned five-repository diagnostic set. Not independent human or blind evaluation.',
                   'sources': [{'documentId': d, 'versionId': r['source']['versionId'], 'repository': r['source']['sourceRef'], 'sha256': r['sha256']}
                               for d, r in documents.items()], 'cases': cases})
    print(json.dumps({'cases': 28, 'positive': 22, 'insufficient': 6, 'modelCalls': 0}))


def maven(live=False):
    STAGE.mkdir(parents=True, exist_ok=True)
    args = ['docker', 'run', '--rm', '--pull', 'never', '-e', 'MAVEN_OPTS=-Xmx512m',
            '--network', 'container:' + CONTAINER if live else 'none',
            '-v', str(ROOT) + ':/workspace', '-v', 'nxz-bigorange_orangejd-maven-cache:/root/.m2', '-w', '/workspace',
            'maven:3.9.9-eclipse-temurin-21', 'mvn', '-o', '-B', '-pl', 'orange-server', '-am',
            '-Dreadme.retrieval.root=/workspace', '-DargLine=-Xmx512m', '-Dsurefire.failIfNoSpecifiedTests=false']
    if live:
        args += ['-Dtest=ReadmeChunkRetrievalAcceptanceTest#compareTwoIsolatedIndexesUsingOnlyPinnedLocalEmbeddings',
                 '-Dreadme.retrieval.live=true', '-Dorange.acceptance.limit=50',
                 '-Dorange.acceptance.ledger=/workspace/docs/rag-b/readme-filter-v1/retrieval-v1/local-embedding-calls.jsonl', 'surefire:test']
    else:
        args += ['-Dtest=ReadmeChunkRetrievalAcceptanceTest#exportRawBaselineAndValidateFrozenGoldWithoutNetwork,RagBTokenChunkerTest,RagBFusionScorerTest', 'test']
    with (STAGE / ('live.log' if live else 'offline.log')).open('w', encoding='utf-8') as log:
        result = subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise ValueError('Maven checks failed; see ' + str(STAGE / ('live.log' if live else 'offline.log')))


def offline():
    check_previous()
    if (OUT / 'experiment-manifest.json').exists():
        raise ValueError('Inputs already sealed; no regeneration')
    maven()
    corpus = read(OUT / 'corpus.json')
    planned = 1 + math.ceil(len({r['text'] for r in corpus}) / 8) + 28
    if planned > 50:
        raise ValueError('Local inference plan exceeds 50; do not start')
    names = [JAVA, 'scripts/readme_chunk_retrieval.py', 'docs/rag-b/readme-filter-v1/retrieval-v1/plan.md',
             'docs/rag-b/readme-filter-v1/retrieval-v1/cases.json', 'docs/rag-b/readme-filter-v1/retrieval-v1/corpus.json',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBOllamaEmbeddingModel.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBFusionScorer.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBQueryTokenizer.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeChunker.java',
             'orange-server/src/main/java/com/orangejd/server/ragb/ingest/RagBTokenChunker.java',
             'orange-server/src/main/java/com/orangejd/server/foundation/external/AiAcceptanceBudget.java']
    names += ['docs/rag-b/readme-filter-v1/chunk-v2/previews/' + d + '.json' for d in ['240','244','427','522','546']]
    write(OUT / 'experiment-manifest.json', {'runId': RUN, 'files': {n: sha(ROOT / n) for n in names},
          'chunkVerificationSha256': sha(ROOT / 'docs/rag-b/readme-filter-v1/chunk-v2/verification.json'),
          'plannedLocalInferenceRequests': planned, 'maximumLocalInferenceRequests': 50, 'cloudRequestsAllowed': 0,
          'rawChunks': sum(r['variant'] == 'RAW_STRUCT' for r in corpus), 'knowledgeChunks': 28})
    print(json.dumps({'status': 'FROZEN', 'rawChunks': sum(r['variant'] == 'RAW_STRUCT' for r in corpus), 'knowledgeChunks': 28,
                      'plannedLocalInferenceRequests': planned, 'cloudRequests': 0}))


def check_manifest():
    m = read(OUT / 'experiment-manifest.json')
    for name, digest in m['files'].items():
        if sha(ROOT / name) != digest:
            raise ValueError('Frozen retrieval input changed: ' + name)
    return m


def run():
    check_previous()
    check_manifest()
    if (OUT / 'live-started.json').exists():
        raise ValueError('Live acquisition already started. Do not reset or silently rerun.')
    inspection = subprocess.run(['docker', 'inspect', CONTAINER], capture_output=True, text=True, encoding='utf-8')
    if inspection.returncode:
        subprocess.run(['docker', 'run', '-d', '--pull', 'never', '--name', CONTAINER,
                        '--label', 'orangejd.experiment=' + RUN, '--memory', '256m',
                        'redis/redis-stack-server:latest'], check=True, capture_output=True)
    else:
        c = json.loads(inspection.stdout)[0]
        if c['Config'].get('Labels', {}).get('orangejd.experiment') != RUN or c['HostConfig'].get('PortBindings'):
            raise ValueError('Container ownership or isolation mismatch')
        if not c['State']['Running']:
            subprocess.run(['docker', 'start', CONTAINER], check=True, capture_output=True)
    maven(True)
    report()


def covered(gold, spans):
    intervals = sorted((max(gold['startUtf16'], s['startUtf16']), min(gold['endUtf16'], s['endUtf16']))
                       for s in spans if s['blockId'] == gold['blockId'] and s.get('documentId', gold['documentId']) == gold['documentId'])
    at = gold['startUtf16']
    for a, b in intervals:
        if a > at:
            return False
        at = max(at, b)
    return at >= gold['endUtf16']


def percentile(values, p):
    v = sorted(values)
    return v[min(len(v)-1, math.ceil(len(v)*p)-1)]


def report():
    check_manifest()
    completion = read(OUT / 'live-completion.json')
    queries = read(OUT / 'retrieval-results.json')
    assert len(queries) == completion['cases'] == 28
    cases = {c['id']: c for c in read(OUT / 'cases.json')['cases']}
    corpus = {r['id']: r for r in read(OUT / 'corpus.json')}
    ledgers = [json.loads(line) for line in (OUT / 'local-embedding-calls.jsonl').read_text(encoding='utf-8').splitlines()]
    assert len(ledgers) <= 50 and all(r['kind'] == 'OLLAMA_EMBEDDING' for r in ledgers)
    metrics, details = {}, []
    for variant in ['RAW_STRUCT', 'KNOWLEDGE_V2']:
        rows, timings = [], []
        for result in queries:
            c = cases[result['id']]
            reps = result['variants'][variant]
            assert len(reps) == 3
            hits = reps[0]['hits']
            raw_spans = [s for h in hits for s in corpus[h['id']]['sourceSpans']]
            expanded = [s for h in hits for s in corpus[h['id']]['expandedSpans']]
            detail = {'id': c['id'], 'variant': variant, 'category': c['category'], 'answerable': c['answerable'],
                      'topProject': hits[0]['documentId'] if hits else None, 'returnedCandidates': len(hits),
                      'projectHitAt1': any(h['documentId'] in c['expectedDocumentIds'] for h in hits[:1]),
                      'projectHitAt3': any(h['documentId'] in c['expectedDocumentIds'] for h in hits[:3]),
                      'projectHitAt5': any(h['documentId'] in c['expectedDocumentIds'] for h in hits),
                      'directEvidenceCompleteAt5': bool(c['evidenceGroups']) and all(any(covered(g, raw_spans) for g in group) for group in c['evidenceGroups']),
                      'expandedEvidenceCompleteAt5': bool(c['evidenceGroups']) and all(any(covered(g, expanded) for g in group) for group in c['evidenceGroups']),
                      'returnedChunkIds': [h['id'] for h in hits]}
            details.append(detail)
            if c['answerable']:
                rows.append(detail)
            timings.extend(r['timing']['retrievalMs'] for r in reps)
        metrics[variant] = {'positiveCases': len(rows), 'projectHitAt1': sum(r['projectHitAt1'] for r in rows),
                            'projectHitAt3': sum(r['projectHitAt3'] for r in rows), 'projectHitAt5': sum(r['projectHitAt5'] for r in rows),
                            'directEvidenceCompleteAt5': sum(r['directEvidenceCompleteAt5'] for r in rows),
                            'expandedEvidenceCompleteAt5': sum(r['expandedEvidenceCompleteAt5'] for r in rows),
                            'retrievalP50Ms': percentile(timings, .5), 'retrievalP95Ms': percentile(timings, .95),
                            'retrievalSamples': len(timings),
                            'unsupportedQueriesReturningCandidates': sum(d['returnedCandidates'] > 0 for d in details if d['variant'] == variant and not d['answerable'])}
    output = {'metrics': metrics, 'cases': details, 'localInferenceRequests': len(ledgers),
              'queryEmbeddingP50Ms': percentile([r['embeddingMs'] for r in queries], .5),
              'queryEmbeddingP95Ms': percentile([r['embeddingMs'] for r in queries], .95),
              'evaluation': 'Assistant-labeled diagnostic retrieval experiment, not answer-quality evaluation',
              'cloudRequests': 0, 'productionWrites': 0, 'reranker': completion['reranker']}
    write(OUT / 'evaluation.json', output)
    lines = ['# 过滤知识分块与原文结构分块：独立检索验证', '',
             '同一批 5 个冻结 README、固定本地 BGE-M3、Redis HNSW＋正文关键词＋名称召回，复用 Java 分词与 RRF 评分。每题每组运行 3 次；没有模型回答、查询改写或额外重排模型。', '',
             '28 题：22 道有批准证据，6 道证据不足。问题和预期证据由助手标注，样本已用于调试；不能当成独立人工评测或网站回答准确率。', '',
             '| 方案 | 项目命中@1 | 项目命中@3 | 项目命中@5 | 直接证据完整@5 | 回查后证据完整@5 | 检索P50 / P95 ms |',
             '|---|---:|---:|---:|---:|---:|---:|']
    for name, m in metrics.items():
        lines.append(f"| {name} | {m['projectHitAt1']}/22 | {m['projectHitAt3']}/22 | {m['projectHitAt5']}/22 | {m['directEvidenceCompleteAt5']}/22 | "
                     f"{m['expandedEvidenceCompleteAt5']}/22 | {m['retrievalP50Ms']:.1f} / {m['retrievalP95Ms']:.1f} |")
    lines += ['', f"本轮本地 Embedding 推理请求 {len(ledgers)} 次，包含预热与入库；云端调用 0。单题本地 Embedding P50 / P95：{output['queryEmbeddingP50Ms']:.1f} / {output['queryEmbeddingP95Ms']:.1f} ms。",
              '', '检索耗时使用预先计算的相同查询向量，仅包含三路召回与融合；不包含 Embedding、回答生成、业务校验和网络传输到网页。查询 Embedding 单独测量，不能把检索时间当成完整客服响应时间。', '',
              '证据不足的问题仍可能返回相似候选。此处未接入客服拒答／追问节点，不能宣称已正确拒答；`evaluation.json` 单独列出这些候选。', '',
              '原文组包含完整冻结原文；知识组仅含批准知识与必要上下文，且加入检索前缀。因此这是过滤＋分块＋上下文整体方案对照，不能将全部差异归因于分块长度。', '',
              '项目命中按仓库文档身份统计，本轮没有核验业务 projectId 映射、在线状态和导入资格。未知配置与许可不由模型补写。', '',
              '逐题诊断：', '', '| 题目 | 方案 | 首位项目 | 项目@5 | 直接／回查证据完整 |', '|---|---|---|---|---|']
    for d in details:
        lines.append(f"| {d['id']} | {d['variant']} | {d['topProject']} | {d['projectHitAt5']} | {d['directEvidenceCompleteAt5']} / {d['expandedEvidenceCompleteAt5']} |")
    (OUT / 'report.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    check_previous()
    files = [p for p in OUT.iterdir() if p.is_file() and p.name != 'completion-seal.json']
    files += [STAGE / 'offline.log', STAGE / 'live.log']
    write(OUT / 'completion-seal.json', {'files': {p.relative_to(ROOT).as_posix(): sha(p) for p in files},
          'localInferenceRequests': len(ledgers), 'cloudRequests': 0, 'productionWrites': 0})
    print(json.dumps({'metrics': metrics, 'localInferenceRequests': len(ledgers), 'report': str(OUT / 'report.md')}))


def verify():
    check_manifest()
    for name, digest in read(OUT / 'completion-seal.json')['files'].items():
        if sha(ROOT / name) != digest:
            raise ValueError('Retrieval artifact changed: ' + name)
    check_previous()
    print(json.dumps({'status': 'RETRIEVAL_VERIFIED', 'cloudRequests': 0, 'productionWrites': 0}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['prepare', 'offline', 'run', 'report', 'verify'])
    args = parser.parse_args()
    try:
        globals()[args.phase]()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
