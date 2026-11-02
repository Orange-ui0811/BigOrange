"""Run source-bound README chunk previews with the Docker network disabled. No provider configuration is read."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/rag-b/readme-filter-v1/chunk-v1'
STAGE = ROOT / '_experiment_staging/readme-chunk-v1'
PILOT = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3'
CLASSES = ['ReadmeKnowledgeChunkerTest', 'ReadmeKnowledgeChunkPreviewTest', 'RagBTokenChunkerTest', 'RagBChunkerTest']


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def protected():
    seal = read(PILOT / 'completion-seal.json')
    assert seal['providerCalls'] == seal['limit'] == 18
    assert seal['indexWrites'] == 0 and not seal['qualityGatePassed']
    files = {**seal['files'], (PILOT / 'completion-seal.json').relative_to(ROOT).as_posix(): sha(PILOT / 'completion-seal.json')}
    for path, digest in files.items():
        if sha(ROOT / path) != digest:
            raise ValueError('Prior pilot artifact changed: ' + path)
    # The pilot's read-only verifier also checks earlier stage histories and exact provider count.
    result = subprocess.run([sys.executable, str(ROOT / 'scripts/run_readme_v5_pilot.py'), '--verify'],
                            cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        raise ValueError('Prior pilot verification failed: ' + result.stdout + result.stderr)
    return files


def inputs():
    names = [
        'scripts/check_readme_chunk_v1.py', 'docs/rag-b/readme-filter-v1/chunk-v1/contract.md',
        'orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeChunker.java',
        'orange-server/src/main/java/com/orangejd/server/ragb/ingest/RagBTokenChunker.java',
        'orange-server/src/main/java/com/orangejd/server/ragb/ingest/RagBChunker.java',
        'orange-server/src/main/resources/rag-b/tokenizers/bge-m3.json',
        'pom.xml', 'orange-common/pom.xml', 'orange-pojo/pom.xml', 'orange-server/pom.xml',
    ]
    names += ['orange-server/src/test/java/com/orangejd/server/ragb/ingest/' + c + '.java' for c in CLASSES]
    names += [p.relative_to(ROOT).as_posix() for p in (ROOT / 'orange-server/src/main/java/com/orangejd/server/ragb/ingest').glob('Readme*.java')]
    return sorted(set(names))


def render_previews():
    summary = read(OUT / 'preview-summary.json')
    text = ['# 5 个仓库的离线分块预览', '', '输入为已封存的 V1.6 分类结果；没有重新分类、补抓链接、调用 Embedding 或写入索引。', '',
            '| 仓库 | K3 正文候选块 | 可形成完整单元的候选块 | 就绪／暂缓单元 | 检索块 | 最大 Token | 导航元数据 |',
            '|---|---:|---:|---:|---:|---:|---:|']
    for row in summary['repositories']:
        text.append(f"| {row['repository']} | {row['candidatePrimaryBlocks']} | {row['readyPrimaryBlocks']} | "
                    f"{row['readyUnits']}／{len(row['deferredUnits'])} | {row['chunks']} | {row['maxEmbeddingTokens']} | {row['navigation']} |")
        p = read(OUT / ('previews/' + row['documentId'] + '.json'))
        lines = ['# ' + row['repository'], '', '这些是分块草稿，没有进入向量库。`projectId=null` 表示本轮没有核验业务映射。', '']
        for unit in p['units']:
            lines += ['## ' + unit['unitId'][:16], '', '- 状态：' + unit['status'], '- 标题：' + ' > '.join(unit['sectionPath']),
                      '- 主体范围：' + ', '.join(unit['subjectScopes']), '- 原因：' + '; '.join(unit['reasons']),
                      '- 子块数：' + str(len(unit['chunkIds'])), '']
            for span in unit['sourceSpans']:
                lines += [f"来源 `{span['startUtf16']}–{span['endUtf16']}`，{span['role']}，范围：{span['scopes']}", '',
                          *['> ' + line for line in span['text'].splitlines()], '']
        lines += ['## 检索块与 Token', '', '| 子块 | 所属单元 | 最终 Embedding 文本 Token | 来源片段数 |', '|---|---|---:|---:|']
        for c in p['chunks']:
            lines.append(f"| {c['chunkId'][:16]} | {c['unitId'][:16]} | {c['tokens']} | {len(c['sourceSpans'])} |")
        (OUT / ('previews/' + row['documentId'] + '.md')).write_text('\n'.join(lines) + '\n', encoding='utf-8')
    text += ['', '“可形成完整单元”只描述结构准入，不代表分类正确或检索命中。暂缓不改写 K3 原始结果，完整原因保存在各仓库 JSON。', '',
             'Flowise 第 155 行及第 157 行按完整步骤处理：子说明仍待复核时，步骤标题也没有检索块。', '',
             '下一步为隔离测试索引和固定问题集验证：项目命中、支持证据、限定条件、回答引用及耗时分别计量。当前无真实检索性能结论。', '']
    (OUT / 'preview-report.md').write_text('\n'.join(text), encoding='utf-8')


def verify():
    manifest = read(OUT / 'verification.json')
    for name, digest in {**manifest['protectedHistory'], **manifest['inputs'], **manifest['outputs']}.items():
        if sha(ROOT / name) != digest:
            raise ValueError('Sealed chunk evidence changed: ' + name)
    protected()
    print(json.dumps({'status': 'CHUNK_V1_VERIFIED', 'tests': manifest['tests'], 'repositories': 5,
                      'providerCalls': 0, 'embeddingCalls': 0, 'indexWrites': 0}))


def run():
    if (OUT / 'verification.json').exists():
        raise ValueError('This generation is sealed; use --verify or a new experiment generation')
    history = protected()
    before = OUT / 'before.json'
    if before.exists():
        assert read(before)['protectedHistory'] == history
    else:
        write(before, {'protectedHistory': history, 'priorProviderCalls': 18, 'priorProviderLimit': 18})
    input_hashes = {name: sha(ROOT / name) for name in inputs()}
    STAGE.mkdir(parents=True, exist_ok=True)
    write(STAGE / 'inputs.json', input_hashes)
    command = ['docker', 'run', '--rm', '--pull', 'never', '--network', 'none', '-e', 'MAVEN_OPTS=-Xmx512m',
               '-v', str(ROOT) + ':/workspace', '-v', 'nxz-bigorange_orangejd-maven-cache:/root/.m2',
               '-w', '/workspace', 'maven:3.9.9-eclipse-temurin-21', 'mvn', '-o', '-B', '-pl', 'orange-server', '-am',
               '-Dtest=' + ','.join(CLASSES), '-Dsurefire.failIfNoSpecifiedTests=false', '-DargLine=-Xmx512m',
               '-Dreadme.chunk.preview.root=/workspace', 'test']
    with (STAGE / 'tests.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print(json.dumps({'status': 'TESTS_FAILED', 'log': str(STAGE / 'tests.log')}))
        return result.returncode
    suites = []
    for cls in CLASSES:
        source = ROOT / ('orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.' + cls + '.xml')
        tree = ET.parse(source).getroot().attrib
        if any(int(tree[k]) for k in ('failures', 'errors', 'skipped')):
            raise ValueError('Failed or skipped required tests: ' + cls)
        saved = STAGE / 'java-reports' / source.name
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, saved)
        suites.append(int(tree['tests']))
    render_previews()
    if protected() != history:
        raise ValueError('Historical pilot changed during offline chunk checks')
    assert all(sha(ROOT / name) == digest for name, digest in input_hashes.items())
    outputs = list((OUT / 'previews').glob('*')) + [OUT / 'preview-summary.json', OUT / 'preview-report.md', before]
    outputs += list(STAGE.rglob('*'))
    write(OUT / 'verification.json', {'status': 'OFFLINE_STRUCTURE_VERIFIED', 'tests': sum(suites), 'repositories': 5,
          'protectedHistory': history, 'inputs': input_hashes,
          'outputs': {p.relative_to(ROOT).as_posix(): sha(p) for p in outputs if p.is_file()},
          'providerCalls': 0, 'embeddingCalls': 0, 'indexWrites': 0, 'productionActivated': False,
          'semanticQualityEvaluated': False, 'retrievalQualityEvaluated': False})
    verify()
    return 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true')
    options = parser.parse_args()
    try:
        sys.exit(verify() or 0 if options.verify else run())
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
