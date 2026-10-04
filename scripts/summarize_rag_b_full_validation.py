"""Local-only cost, retrieval and claim-review artifact; no model calls."""
import json
import re
import numpy as np
from pathlib import Path
from collections import Counter
from export_rag_model_corpus import query
from run_rag_model_chunk_experiment import norm

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/rag-b/full-original-validation'

def read(name):
    return json.loads((OUT/name).read_text(encoding='utf-8'))

def write(name, value):
    (OUT/name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

def main():
    phases = {}
    chunk_ids = set()
    for phase in ['baseline', 'fixed']:
        data = read(phase+'-answers.json')
        totals = Counter()
        rows = []
        for row in data['rows']:
            result = row.get('result', {})
            usage = result.get('generationUsage', {})
            for name in ['prompt_tokens', 'completion_tokens', 'total_tokens',
                         'prompt_cache_hit_tokens', 'prompt_cache_miss_tokens']:
                totals[name] += int(usage.get(name, 0))
            totals['reasoning_tokens'] += int(usage.get('completion_tokens_details', {}).get('reasoning_tokens', 0))
            evidence = result.get('evidence', [])
            chunk_ids.update(int(e['chunkId']) for e in evidence)
            numbers = re.findall(r'\[C(\d+)\]', result.get('answer', ''))
            invalid = [n for n in numbers if not 1 <= int(n) <= len(evidence)]
            rows.append({'key': row['key'], 'question': row.get('question'),
                         'answer': result.get('answer'), 'evidence': evidence,
                         'expectedRefs': row.get('expectedRefs'), 'goldAnchors': row.get('goldAnchors'),
                         'validator': result.get('citationValidation'), 'invalidPointers': invalid})
        peak = (totals['prompt_cache_hit_tokens']*.044 + totals['prompt_cache_miss_tokens']*1.32
                + totals['completion_tokens']*3.96)/1_000_000
        phases[phase] = {'requests': len(data['rows']), 'statuses': dict(Counter(
            r.get('result', {}).get('status', 'ERROR') for r in data['rows'])),
            'usage': dict(totals), 'estimatedUsdOffPeak': peak/2, 'estimatedUsdPeak': peak,
            'invalidCitationPointers': sum(len(r['invalidPointers']) for r in rows), 'reviewRows': rows}
    ids = ','.join(map(str, sorted(chunk_ids)))
    chunks = query("SELECT JSON_OBJECT('chunkId',id,'documentId',document_id,'versionId',version_id,"
                   "'section',section_path,'content',content) FROM rag_b_chunk WHERE id IN ("+ids+")")
    write('answer-evidence-review.json', {'phases': phases, 'chunks': chunks,
        'limitations': ['Local code/assistant review, not blind independent human assessment.',
                        'Citation pointer validity does not imply factual entailment.',
                        'Cost range uses official peak/off-peak rates, not account billing.',
                        'Completion tokens already include reasoning tokens; do not double count.'],
        'pricingSource': 'https://api-docs.deepseek.com/quick_start/pricing/'})
    cases = json.loads((ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json').read_text(encoding='utf-8'))['cases']
    by_key = {c['key']: c for c in cases}
    retrieval = {}
    for phase in phases:
        rows = read(phase+'-retrieval.json')['rows']
        revised = []
        for row in rows:
            case = by_key[row['key']]
            rank = 0
            for i, hit in enumerate(row.get('result', {}).get('hits', []), 1):
                ref = hit['sourceRef'].split('|', 1)[0] if hit['sourceType']=='GITHUB' else hit['sourceRef']
                expected = [r.split('|', 1)[0] if hit['sourceType']=='GITHUB' else r for r in case['refs']]
                if ref in expected and (not case['anchors'] or any(norm(a) in norm(hit['content']) for a in case['anchors'])):
                    rank = i
                    break
            revised.append({'key': row['key'], 'category': row['category'], 'rank': rank, 'strictRank': row['rank']})
        semantic = [r for r in revised if r['category']!='named_regression']
        retrieval[phase] = {'n': len(semantic), 'canonicalEvidenceRecall8': sum(r['rank']>0 for r in semantic)/len(semantic),
            'canonicalEvidenceMRR8': sum(1/r['rank'] if r['rank'] else 0 for r in semantic)/len(semantic),
            'changedByCanonicalization': [r for r in revised if r['rank']!=r['strictRank']], 'rows': revised}
    write('canonical-retrieval-assessment.json', {'phases': retrieval,
        'rule': 'GitHub repo|period counts as the same repository; still requires gold anchor containment. Applied equally to both phases.'})
    comparison = {}
    for phase, value in phases.items():
        answers = read(phase+'-answers.json')['rows']
        claims = Counter(s['reason'] for r in value['reviewRows'] for s in (r.get('validator') or {}).get('sentences', []))
        comparison[phase] = {'semanticRetrieval': read(phase+'-retrieval.json')['semantic'],
            'retrievalCategories': read(phase+'-retrieval.json')['categories'],
            'businessBenchmark': read(phase+'-business-ab.json')['summary'],
            'load': [{k:v for k,v in x.items() if k!='rows'} for x in read(phase+'-load.json')['loads']],
            'generation': {k:v for k,v in value.items() if k!='reviewRows'},
            'citationSentenceHeuristic': dict(claims),
            'answerWallP50Ms': float(np.percentile([r['wallMs'] for r in answers],50)),
            'answerWallP95Ms': float(np.percentile([r['wallMs'] for r in answers],95))}
    direct = {'fact-00-zh','fact-02-zh','fact-03-zh','fact-04-zh','fact-07-zh',
              'fact-12-zh','fact-16-zh','fact-18-zh','fresh-02','fresh-03'}
    review = []
    for row in phases['fixed']['reviewRows']:
        notes = []
        if row['key']=='fact-29-zh':
            notes.append('C5 仅含 Graphite 标识图片；不能支持回答中“2D 内容创作程序化工具箱”的描述。最后一个证据不足结论未加引用。')
        if row['key']=='fresh-02':
            notes.append('首句摄像头权限事实缺引用；末句本地 HTTP 方案还应明确安全来源条件，不能仅凭避免 file:// 保证可用。')
        if row['key']=='fact-16-zh':
            notes.append('已说明原证据是“持久化后台智能体”而非完全相同的“后台任务”，平台摘要未独立验证。')
        if row['key']=='fact-12-zh':
            notes.append('引用支持来自平台职业项目摘要，不代表所有能力都已对照上游 README 核验。')
        review.append({'key': row['key'], 'behavior': 'DIRECT_TARGET_ANSWER' if row['key'] in direct else 'INSUFFICIENT_EVIDENCE_RESPONSE',
                       'notes': notes})
    states = query("SELECT JSON_OBJECT('status',d.status,'documents',COUNT(*),'chunks',COALESCE(SUM(v.chunk_count),0)) "
                   "FROM rag_b_document d LEFT JOIN rag_b_document_version v ON v.id=d.active_version_id GROUP BY d.status")
    report = {'outcome': '融合与去重修正有明确收益，但当前 B 版尚未达到直接替换 A 版的上线边界。',
        'import': {'nonblankOriginalDocuments': len(read('import-manifest.json')['documents']),
                   'sourceHashMismatches': 0, 'importedActiveChunks': sum(r['chunks'] for r in read('import-result.json')['audited']),
                   'currentBStates': states, 'fallbackDocumentsRetained': 16},
        'fixedConfiguration': {'embedding': 'bge-small-zh-v1.5', 'dimensions': 512,
            'chunkBaseline': '现有按章节/字符切割，500 字符上限、50 字符重叠；本轮未更换模型或分块。',
            'index': 'Redis Stack HNSW_HYBRID; COSINE; M=16; EF_CONSTRUCTION=200',
            'fusion': 'RRF k=60，向量 1.0、关键词 0.2；名称 3.0 和 0.1 奖励仅对明确完整名称命中启用。',
            'dedup': 'GitHub 跨周期同仓库同文本去重；事实查询保留同项目多个事实，推荐列表优先来源多样性。',
            'generation': 'deepseek-v4-pro，temperature=0.1；相同提示词和词面引用校验规则。'},
        'comparison': comparison, 'indexStats': read('index-stats.json'),
        'fixedAnswerReview': {'directTargetAnswers':10,'insufficientEvidenceResponses':8,'rows':review,
            'method': '代码与原片段逐条核对；非独立人工盲评，不能宣称为通用事实准确率。'},
        'limits': ['A 为直接 VectorStore 检索基线，B 为带业务筛选的混合检索；语料亦不同，不能将 60 题通过率当成实际推荐系统全面优劣。',
                   '固定题集金标要求命中来源和事实片段；可能存在其他有效答案。本轮参数依据已有诊断修正，没有独立未使用过的保留集。',
                   'Redis 短时负载并非长期容量测试，P95 包含外部 Embedding 的网络波动。',
                   '金额为生成 token 按官方峰谷价估算区间，非实际账单；不含历史入库调用、Embedding 和服务器费用。',
                   '旧库与平台用户路由保持不变；不批准生产替换。'],
        'nextAcceptanceGates': ['在独立代际测试 bge-m3 + token 结构分块，避免影响 A 的 512 维索引。',
            '把全文技术事实与平台生成的项目评价区分，并减少 HTML、徽章等无信息片段进入召回。',
            '验证跨语言事实支持，单独处理无证据拒答，保留真实缺引用/错误引用拦截。',
            '使用独立保留集、多轮生成与较长时间负载，完成业务入口和失败/回滚测试后再决定上线。']}
    write('comparison-report.json', report)
    print('REPORT '+str(OUT/'comparison-report.json'))
    for phase, value in phases.items():
        print(phase, json.dumps({k:v for k,v in value.items() if k!='reviewRows'}, ensure_ascii=False))

if __name__ == '__main__':
    main()
