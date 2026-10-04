"""Offline repair report. Prices match the prior experiment; estimates are not billed totals."""
import json,statistics
from pathlib import Path
from summarize_analysis_customer_b import normalized,load

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/customer'

def summarize(folder):
    rows=[normalized(load(p)) for p in sorted(folder.glob('P*-*-customerB.json'))]
    cards=[c for r in rows for c in r['cards']]
    return dict(requests=len(rows),success=sum(r['status']=='SUCCEEDED' for r in rows),
        clarifications=sum(r['resultStatus']=='NEEDS_CLARIFICATION' for r in rows),
        withCards=sum(bool(r['cards']) for r in rows),cards=len(cards),
        withReferences=sum(c['referenceCount']>0 for c in cards),references=sum(c['referenceCount'] for c in cards),
        importable=sum(c['importable'] is True for c in cards),
        meanSeconds=statistics.mean(r['seconds'] for r in rows) if rows else 0,
        maxSeconds=max((r['seconds'] for r in rows),default=0),
        calls=sum(r['calls'] for r in rows),failedCalls=sum(c['status']=='FAILED' for r in rows for c in r['callsDetail']),
        inputTokens=sum(r['inputTokens'] for r in rows),outputTokens=sum(r['outputTokens'] for r in rows),
        estimatedCny=sum(r['estimatedCny'] or 0 for r in rows),unknownCostRequests=sum(r['estimatedCny'] is None for r in rows),rows=rows)

def main():
    groups={name:summarize(OUT/folder) for name,folder in [('baseline','analysis-b-vs-customer-b-20261003'),('v45','repair-v45-live'),('v46','repair-v46-live'),('v47','repair-v47-live'),('v48','repair-v48-live'),('v49','repair-v49-live'),('v410','repair-v410-live'),('v411','repair-v411-live')]}
    (OUT/'repair-comparison.json').write_text(json.dumps(groups,ensure_ascii=False,indent=2),encoding='utf-8')
    lines=['# 客服修复前后真实调用复测','',
        '相同八个合成画像；基线每个两轮，修复版各一轮。模型仍为 DeepSeek Flash，B 索引未更换。v4.5–4.8全阶段开启思考；v4.9聊天及条件理解保留思考，证据摘取与核验使用普通模式。此为开发集回归，不是独立上线验收。','',
        '|指标|修复前（16次）|最终v4.11（8次）|','|---|---:|---:|']
    for key,label in [('success','成功终态'),('clarifications','要求澄清'),('withCards','有卡片的请求'),('cards','卡片总数'),('withReferences','有核验引用的卡片'),('references','核验引用条数'),('importable','允许导入的卡片'),('meanSeconds','平均秒数'),('maxSeconds','最慢秒数'),('calls','模型调用数（含重试）'),('failedCalls','失败的模型调用'),('inputTokens','输入 Token'),('outputTokens','输出 Token（包含思考）'),('estimatedCny','模型费用估算（元）'),('unknownCostRequests','含未知费用的请求')]:
        vals=[f'{g[key]:.6f}' if isinstance(g[key],float) else str(g[key]) for g in (groups['baseline'],groups['v411'])]
        lines.append('|'+label+'|'+'|'.join(vals)+'|')
    lines+=['','估算沿用本次对比核实的周末 Flash 单价：缓存命中输入 0.02、未命中输入 1、输出 4 元/百万 Token（[官方定价](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)）。思考已包含在输出，不重复计费。不含未返回用量的 Embedding、基础设施成本；不是供应商账单。','',
        '## 最终版逐画像结果','', '|画像|终态|结果|卡片|带引用卡片|耗时秒|估算元|','|---|---|---|---:|---:|---:|---:|']
    for r in groups['v411']['rows']:
        lines.append(f"|{r['case']}|{r['status']}|{r['resultStatus']}|{r['cardCount']}|{sum(c['referenceCount']>0 for c in r['cards'])}|{r['seconds']:.2f}|{r['estimatedCny']}|")
    lines+=['','## 最终版分阶段调用','', '|阶段|次数|失败|平均秒数|输入 Token|输出 Token|','|---|---:|---:|---:|---:|---:|']
    calls=[c for r in groups['v411']['rows'] for c in r['callsDetail']]
    for phase in sorted({c.get('phase','UNKNOWN') for c in calls}):
        cs=[c for c in calls if c.get('phase','UNKNOWN')==phase]
        lines.append(f"|{phase}|{len(cs)}|{sum(c['status']=='FAILED' for c in cs)}|{statistics.mean(c.get('durationMs',0) for c in cs)/1000:.2f}|{sum(c.get('usage',{}).get('prompt_tokens',0) for c in cs)}|{sum(c.get('usage',{}).get('completion_tokens',0) for c in cs)}|")
    lines+=['','## 引用核对材料','']
    for p in sorted((OUT/'repair-v411-live').glob('P*-*-customerB.json')):
        r=load(p);lines+=['### '+r['case'],'']
        for c in r.get('result',{}).get('projects',[]):
            lines += [f"**{c['name']}** — {c.get('matchStatus')}；可导入：{c.get('importable')}",'']
            for ref in c.get('references',[]):
                lines += [f"- {ref.get('field','说明')}：{ref.get('text')} [{ref.get('evidenceId')}]",f"  原文：{ref.get('quote')}",f"  定位：文档 {ref.get('documentId')} / 版本 {ref.get('versionId')} / 片段 {ref.get('chunkId')}",'']
    (OUT/'repair-comparison.md').write_text('\n'.join(lines),encoding='utf-8')
    print(json.dumps({k:{a:b for a,b in v.items() if a!='rows'} for k,v in groups.items()},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
