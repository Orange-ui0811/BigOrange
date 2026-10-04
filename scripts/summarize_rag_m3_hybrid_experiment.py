"""Produce a reviewable decision report from actual isolated experiment outputs."""
import json
import numpy as np
from run_rag_model_chunk_experiment import STAGE, load_json, write_json
from run_rag_m3_hybrid_translation_experiment import OUT


def score(rows):
    return {'n':len(rows), 'evidenceRecall8':sum(r['rank']>0 for r in rows)/len(rows),
        'evidenceMRR8':sum(1/r['rank'] if r['rank'] else 0 for r in rows)/len(rows),
        'evidenceTop1':sum(r['rank']==1 for r in rows)/len(rows)}


def main():
    names = ['current-small-char500--ann_raw', 'bge-m3--native512--ann_raw',
        'bge-m3--native512--ann_dual', 'bge-m3--native512--hybrid_raw',
        'bge-m3--native512--hybrid_diverse_raw',
        'bge-m3--native512--hybrid_diverse_raw_t055',
        'ablation--name_gated_lex115', 'ablation--name_gated_lex020',
        'ablation--name_gated_lex020_diverse', 'ablation--name_gated_lex020_dual']
    metrics = {}
    data = {name:load_json(OUT/(name+'.json')) for name in names}
    for name,result in data.items():
        metrics[name] = {
            'semantic90':score([r for r in result['rows'] if r['category']!='named_regression']),
            'chinese60':score([r for r in result['rows'] if r['category'] in
                ['business_zh','readme_cross_language','fresh_business_zh','fresh_readme_zh']]),
            'freshChinese30':score([r for r in result['rows'] if r['key'].startswith('fresh-')]),
            'named50':score([r for r in result['rows'] if r['category']=='named_regression'])}
    exact=load_json(OUT/'ablation--exact.json')
    for ef in [64,128]:
        ann=load_json(OUT/f'ablation--ann_ef{ef}.json')
        assert [r['rank'] for r in ann['rows']]==[r['rank'] for r in exact['rows']]
        assert ann['meanExactTop8Overlap']==1.0
    events=[json.loads(line) for line in (STAGE/'calls.jsonl').read_text(encoding='utf-8').splitlines()
        if json.loads(line)['purpose'] in ['fresh-queries','translated-queries']]
    cost={}
    for model in sorted({e['model'] for e in events}):
        rows=[e for e in events if e['model']==model]
        cost[model]={'requests':len(rows),'items':sum(e['items'] for e in rows),
            'characters':sum(e['inputCharacters'] for e in rows),
            'positiveReportedTokens':sum((e.get('usage') or {}).get('total_tokens',0) for e in rows),
            'zeroOrMissingUsageRequests':sum(not (e.get('usage') or {}).get('total_tokens',0) for e in rows)}
    raw=data['bge-m3--native512--ann_raw']['rows']; dual=data['bge-m3--native512--ann_dual']['rows']
    deltas=[]
    for i in range(30):
        keys={f'fact-{i:02}-zh',f'fact-{i:02}-en',f'fresh-{i:02}'}
        values=[]
        for a,b in zip(raw,dual):
            if a['key'] in keys: values.append((1/b['rank'] if b['rank'] else 0)-(1/a['rank'] if a['rank'] else 0))
        deltas.append(np.mean(values))
    rng=np.random.default_rng(20261002)
    bootstrap=np.array(deltas)[rng.integers(0,30,size=(10000,30))].mean(axis=1)
    limitations=[
        '140 questions: 50 named regressions, original 60 semantic questions, 30 new Chinese paraphrases.',
        'New wording shares the same facts/anchors; not independent human gold or an independent corpus.',
        'Evidence correctness is expected source plus normalized anchor containment, not semantic entailment.',
        'Isolated actual Redis HNSW and replayed B RRF/diversity; not Java endpoint or deployed B configuration.',
        'No live business status guards, hard filters other than source type, preferences, relevance guard, LLM generation or Java citation validation.',
        'English companion queries were preauthored; not actual automatic translation. Gains are optimistic.',
        'Translation API call blocked by automatic approval before any request; no translation or generation text-model calls.',
        'Redis latency outputs originally named redisP95Ms include Python logic and transport, exclude embedding/translation/generation.',
        'Weight tuning is exploratory on the same set; no final superiority or production deployment claim.',
        'Threshold 0.55 was replayed to expose sensitivity, not calibrated for the new embedding model.',
    ]
    report={'metrics':metrics, 'newEmbeddingConsumption':cost,
        'textModelRequests':0,'newDocumentEmbeddingRequests':0,
        'dualMinusRawMRR90':float(np.mean(deltas)),
        'dualMinusRawMRR95CI_factGrouped':np.percentile(bootstrap,[2.5,97.5]).tolist(),
        'hnswCheck':'EF_RUNTIME 64 and 128 returned the same top8 as exact cosine for all 140 cases.',
        'recommendation':'Keep M3 structural512 as candidate; use vector ranking as fact-query baseline. '
            'Do not deploy existing global title/fulltext weights or global source diversity unchanged. '
            'Evaluate translation only as selective fallback, pending actual blind translation testing.',
        'limitations':limitations}
    write_json(OUT/'decision-report.json',report)
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__': main()
