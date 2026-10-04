"""Offline statistics for the authorized paired product experiment; no API calls."""
import json, statistics, math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/customer/analysis-b-vs-customer-b-20261003'
FIELDS=['description','vibeCodingReason','recommendReason','prerequisites','analysisReason','modificationPlan','proofPoints','mainRisks','supportedExplanation']
def load(p): return json.loads(p.read_text(encoding='utf-8'))
def n(x): return int(x or 0)
def canonical(c):
    refs=c.get('references',[])
    url=c.get('url') or next((v.get('url') for v in refs if isinstance(v,dict) and v.get('url')), '')
    return str(url).lower().rstrip('/')
def quantile(xs,q):
    ys=sorted(xs);return ys[max(0,math.ceil(len(ys)*q)-1)] if ys else None
def normalized(d):
    a=d['product']=='analysisB';response=d.get('response',{});r=response.get('result',{}) if a else d.get('result',{})
    cards=r.get('recommendations',[]) if a else r.get('projects',[])
    calls=[r['modelMetrics']] if a and r.get('modelMetrics') else d.get('metrics',{}).get('modelCalls',[])
    ins=outs=thought=hits=miss=0;unknown=0
    for call in calls:
        u=call.get('usage',{});ins+=n(u.get('prompt_tokens'));outs+=n(u.get('completion_tokens'));thought+=n(u.get('completion_tokens_details',{}).get('reasoning_tokens'))
        cached=n(u.get('prompt_cache_hit_tokens',u.get('prompt_tokens_details',{}).get('cached_tokens',0)));hits+=cached
        miss+=n(u.get('prompt_cache_miss_tokens',n(u.get('prompt_tokens'))-cached))
        if not u:unknown+=1
    visible=[]
    for c in cards:
        v=dict(c)
        if not a: v['description']=c.get('whatIsIt') or c.get('description')
        visible.append({**{f:str(v.get(f) or '') for f in FIELDS},'url':canonical(c),'topLevelUrlPresent':bool(c.get('url')),'name':c.get('name'),'type':c.get('type'),'language':c.get('language'),'duration':c.get('duration'),'matchStatus':c.get('matchStatus'),'importable':c.get('importable'),'conditionGaps':c.get('conditionGaps',[]),'referenceCount':len(c.get('references',[])), 'evidenceStatus':c.get('evidenceStatus')})
    cost=(ins*12+outs*24)/1e6 if a else (hits*.02+miss+outs*4)/1e6
    return dict(case=d['case'],round=d['round'],product=d['product'],seconds=d['clientWallMs']/1000,
        status=('ERROR' if d.get('error') or r.get('error') else 'SUCCEEDED') if a else d.get('run',{}).get('status','ERROR'),
        resultStatus=r.get('status'),error=d.get('error') or r.get('error') or d.get('run',{}).get('error_code'),
        cards=visible,cardCount=len(cards),inputTokens=ins,outputTokens=outs,reasoningTokens=thought,cacheHitTokens=hits,cacheMissTokens=miss,
        calls=len(calls),callsWithoutUsage=unknown,estimatedCny=cost if calls and not unknown else None,
        outputCapHits=sum(n(c.get('usage',{}).get('completion_tokens'))>=1500 for c in calls) if not a else None,
        state=r.get('state',{}),answer=r.get('answer'),callsDetail=calls,sessionSoftDeleted=d.get('sessionSoftDeleted'),
        stages=response.get('labStages',[]) if a else d.get('events',[]))
def main():
    rows=[normalized(load(p)) for p in sorted(OUT.glob('P*-*-*.json'))]
    summary={'rows':rows,'groups':{},'repeatStability':[],'pairedOverlap':[]}
    for side in ['analysisB','customerB']:
        rs=[r for r in rows if r['product']==side];cards=[c for r in rs for c in r['cards']]
        times=[r['seconds'] for r in rs];costs=[r['estimatedCny'] for r in rs if r['estimatedCny'] is not None]
        summary['groups'][side]=dict(requests=len(rs),success=sum(r['status']=='SUCCEEDED' for r in rs),withCards=sum(r['cardCount']>0 for r in rs),cards=len(cards),
            meanSeconds=statistics.mean(times) if times else None,p95Seconds=quantile(times,.95),medianSeconds=statistics.median(times) if times else None,
            inputTokens=sum(r['inputTokens'] for r in rs),outputTokens=sum(r['outputTokens'] for r in rs),reasoningTokens=sum(r['reasoningTokens'] for r in rs),
            calls=sum(r['calls'] for r in rs),callsWithoutUsage=sum(r['callsWithoutUsage'] for r in rs),estimatedCny=sum(costs),unknownCostRequests=len(rs)-len(costs),
            importable=sum(c['importable'] is True for c in cards),withReferences=sum(c['referenceCount']>0 for c in cards),
            fieldPresence={f:sum(bool(c[f].strip()) for c in cards) for f in FIELDS},
            meanCardChars=statistics.mean([sum(len(c[f]) for f in FIELDS) for c in cards]) if cards else 0,
            meanSecondsWithCards=statistics.mean([r['seconds'] for r in rs if r['cardCount']]) if cards else None,
            outputCapHits=sum(r['outputCapHits'] or 0 for r in rs))
    for case in sorted({r['case'] for r in rows}):
        for side in ['analysisB','customerB']:
            rs=[r for r in rows if r['case']==case and r['product']==side]
            if len(rs)==2:
                x,y=[{c['url'] for c in r['cards']} for r in rs];union=x|y
                summary['repeatStability'].append(dict(case=case,product=side,exact=x==y,jaccard=len(x&y)/len(union) if union else None,emptyBoth=not union,counts=[len(x),len(y)]))
        for round_no in [1,2]:
            rs=[r for r in rows if r['case']==case and r['round']==round_no]
            if len(rs)==2:
                x,y=[{c['url'] for c in r['cards']} for r in rs];union=x|y
                summary['pairedOverlap'].append(dict(case=case,round=round_no,intersection=len(x&y),union=len(union),jaccard=len(x&y)/len(union) if union else None))
    (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    lines=['# 逐请求结果与卡片内容','', '字段覆盖只表示有内容；占位文字、已有分析不视为本轮事实已核实。','']
    for r in rows:
        lines.extend([f"## {r['case']} 第{r['round']}轮 {r['product']}",'',f"终态 {r['status']} / {r['resultStatus']}；{r['seconds']:.2f} 秒；{r['calls']}次模型调用；估算费用 {r['estimatedCny']} 元。",'', '条件：`'+json.dumps(r['state'],ensure_ascii=False)+'`','',r.get('answer') or '', ''])
        for c in r['cards']:
            lines.extend([f"### {c['name']}",'',f"{c['url']} | {c['matchStatus']} | 引用 {c['referenceCount']} | 允许导入 {c['importable']}",''])
            for f in FIELDS:
                if c[f]:lines.extend([f"**{f}**：{c[f]}",''])
            if c['conditionGaps']:lines.extend(['待核实：'+'；'.join(c['conditionGaps']),''])
    (OUT/'outputs.md').write_text('\n'.join(lines),encoding='utf-8')
    print(json.dumps(summary['groups'],ensure_ascii=False,indent=2))
if __name__=='__main__':main()
