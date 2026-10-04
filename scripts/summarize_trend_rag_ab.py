"""Measured metrics only; source binding is not factual correctness. Manual review is separate."""
import json,statistics,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-ab-20261003'

def n(value):return float(value or 0)
def repos(result):
    return {ref['url'] for rec in result.get('recommendations',[]) for ref in rec.get('references',[]) if ref.get('url')}
def summarize():
    rows=[]
    for path in sorted(OUT.glob('*-?-?.json')):
        raw=json.loads(path.read_text(encoding='utf-8'));response=raw.get('response',{});result=response.get('result',{})
        metrics=result.get('modelMetrics',{});usage=metrics.get('usage',{});retrieval=response.get('retrieval',{});trace=retrieval.get('trace',{})
        recs=result.get('recommendations',[])
        known={e.get('repositoryUrl') for e in trace.get('evidence',[]) if e.get('repositoryUrl')}
        for source in ['beginnerCases','careerCases']:
            known.update(p['htmlUrl'].lower().removesuffix('.git').rstrip('/') for p in result.get(source,[]) if p.get('htmlUrl'))
        binding=[bool(item.get('references')) and all(ref.get('url') in known for ref in item.get('references',[])) for item in recs]
        prompt=n(usage.get('prompt_tokens'));output=n(usage.get('completion_tokens'));reason=n(usage.get('completion_tokens_details',{}).get('reasoning_tokens'))
        row=dict(case=raw['case'],round=raw['round'],variant=raw['variant'],file=path.name,
            successful=bool(metrics) and not raw.get('error') and not result.get('error'),error=raw.get('error') or result.get('error'),
            wallSeconds=n(raw['clientWallMs'])/1000,retrievalSeconds=n(trace.get('durationMs'))/1000,modelSeconds=n(metrics.get('durationMs'))/1000,
            inputTokens=prompt,outputTokens=output,reasoningTokens=reason,visibleOutputTokens=output-reason,
            cacheHitTokens=n(usage.get('prompt_cache_hit_tokens') or usage.get('prompt_tokens_details',{}).get('cached_tokens')),
            usageAvailable=metrics.get('usageSource')=='PROVIDER',model=metrics.get('model'),
            basePriceCny=(prompt*12+output*24)/1e6 if usage else None,
            alternate0813PriceCny=(prompt*9+output*27)/1e6 if usage else None,
            recommendationCount=len(recs),boundRecommendations=sum(binding),projects=sorted(repos(result)),
            ragRecommendations=sum(str(item.get('candidateId','')).startswith('E') for item in recs),
            businessRecommendations=sum(str(item.get('candidateId','')).startswith(('B:','C:')) for item in recs),
            evidenceCount=len(trace.get('evidence',[])),promptEvidenceChars=len(retrieval.get('text','')),
            retrievedProjects=sorted(e.get('repositoryUrl') for e in trace.get('evidence',[]) if e.get('repositoryUrl')),
            evidenceHash=hashlib.sha256(retrieval.get('text','').encode()).hexdigest(),
            retrievalStatus=trace.get('status'),businessCases=len(result.get('beginnerCases',[]))+len(result.get('careerCases',[])))
        rows.append(row)
    aggregate={}
    for side in ['A','B']:
        chosen=[r for r in rows if r['variant']==side];good=[r for r in chosen if r['successful']]
        if not chosen:continue
        total_recs=sum(r['recommendationCount'] for r in chosen)
        aggregate[side]=dict(attempts=len(chosen),successes=len(good),failures=len(chosen)-len(good),
            meanWallSeconds=statistics.mean(r['wallSeconds'] for r in chosen),medianWallSeconds=statistics.median(r['wallSeconds'] for r in chosen),
            maxWallSeconds=max(r['wallSeconds'] for r in chosen),
            meanRetrievalSeconds=statistics.mean(r['retrievalSeconds'] for r in good) if good else None,
            meanModelSeconds=statistics.mean(r['modelSeconds'] for r in good) if good else None,
            meanInputTokens=statistics.mean(r['inputTokens'] for r in good) if good else None,
            meanOutputTokens=statistics.mean(r['outputTokens'] for r in good) if good else None,
            meanReasoningTokens=statistics.mean(r['reasoningTokens'] for r in good) if good else None,
            meanBasePriceCny=statistics.mean(r['basePriceCny'] for r in good) if good else None,
            totalBasePriceCny=sum(r['basePriceCny'] or 0 for r in chosen),
            unknownUsageAttempts=sum(not r['usageAvailable'] for r in chosen),cacheHitTokens=sum(r['cacheHitTokens'] for r in chosen),
            recommendations=total_recs,boundRecommendations=sum(r['boundRecommendations'] for r in chosen),
            ragRecommendations=sum(r['ragRecommendations'] for r in chosen),businessRecommendations=sum(r['businessRecommendations'] for r in chosen),
            actualModels={m:sum(r['model']==m for r in chosen) for m in {r['model'] for r in chosen}},
            distinctRepositories=len(set().union(*(set(r['projects']) for r in chosen))),
            noRecommendationRuns=sum(r['recommendationCount']==0 for r in good))
    pairs=[]
    for key in sorted({r['case'] for r in rows}):
        group=[r for r in rows if r['case']==key]
        entry={'case':key}
        for side in ['A','B']:
            selected=[r for r in group if r['variant']==side]
            if not selected:continue
            first=set(selected[0]['projects']);last=set(selected[-1]['projects']);union=first|last
            rfirst=set(selected[0]['retrievedProjects']);rlast=set(selected[-1]['retrievedProjects']);runion=rfirst|rlast
            entry[side]={'rounds':len(selected),'meanSeconds':statistics.mean(r['wallSeconds'] for r in selected),
                'meanBasePriceCny':statistics.mean(r['basePriceCny'] or 0 for r in selected),
                'projects':[r['projects'] for r in selected],
                'retrievalRepeatJaccard':len(rfirst&rlast)/len(runion) if len(selected)>1 and runion else None,
                'sameEvidenceAcrossRounds':len({r['evidenceHash'] for r in selected})==1,
                'repeatJaccard':len(first&last)/len(union) if len(selected)>1 and union else None}
        pairs.append(entry)
    matched=[]
    for a in rows:
        if a['variant']!='A' or not a['successful']:continue
        b=next((r for r in rows if r['case']==a['case'] and r['round']==a['round'] and r['variant']=='B' and r['successful']),None)
        if b and a['model']==b['model']:
            matched.append(dict(case=a['case'],round=a['round'],model=a['model'],aSeconds=a['wallSeconds'],bSeconds=b['wallSeconds'],aCost=a['basePriceCny'],bCost=b['basePriceCny']))
    matched_summary={'pairs':len(matched),'details':matched}
    if matched:
        for field in ['aSeconds','bSeconds','aCost','bCost']:
            matched_summary[field]=statistics.mean(r[field] for r in matched)
    result=dict(scope='Actual full product generator pipeline with lab-injected evidence. Synthetic profiles; 8 scenarios repeated, not 16 independent scenarios.',
        pricing=dict(requestedModel='DeepSeek-V4-Pro',inputCnyPerMillion=12,outputCnyPerMillion=24,
            source='https://ai.gitee.com/serverless-api?model=DeepSeek-V4-Pro',verifiedAt='2026-10-03',
            type='Public list-price estimate, not invoiced debit. Actual returned GA alias differs from requested SKU; alternative 0813 rates retained per row. No reasoning double-count.'),
        aggregate=aggregate,pairs=pairs,sameReturnedModelPairedSubset=matched_summary,rows=rows)
    (OUT/'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(aggregate,ensure_ascii=False,indent=2))
    lines=['# 智能分析 A/B 原始内容对照','', '逐条保留模型生成结果与真正送入 Prompt 的证据。来源绑定不等于事实核实。','']
    for rawrow in rows:
        raw=json.loads((OUT/rawrow['file']).read_text(encoding='utf-8'));res=raw.get('response',{});result=res.get('result',{})
        lines += [f"## {rawrow['case']} / 第 {rawrow['round']} 轮 / {rawrow['variant']}",'',f"耗时 {rawrow['wallSeconds']:.2f}s；检索 {rawrow['retrievalSeconds']:.2f}s；按标价估算 ¥{rawrow['basePriceCny'] or 0:.4f}。",'','### 输入','',json.dumps(raw['profile'],ensure_ascii=False),'','### 实际 Prompt 证据','',res.get('retrieval',{}).get('text','（无）'),'','### 最终内容','']
        lines += ['```json',json.dumps({k:v for k,v in result.items() if k in ['analysis','learning','recommendations','error']},ensure_ascii=False,indent=2),'```','']
    (OUT/'outputs.md').write_text('\n'.join(lines),encoding='utf-8')

if __name__=='__main__':summarize()
