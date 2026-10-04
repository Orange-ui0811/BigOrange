"""Summarize measured stages and frozen prompt repetitions without inventing provider internals."""
import json,hashlib,statistics as st
from pathlib import Path
from collections import defaultdict,Counter
from run_trend_cause_probe import OUT,KEYS,digest

def average(values):return st.mean(values) if values else None
def measured(row):
    response=row.get('response',{});result=response.get('result',response);m=result.get('modelMetrics',{})
    def numeric(value):
        if isinstance(value,dict):return {k:numeric(v) for k,v in value.items()}
        return int(value) if isinstance(value,str) and value.isdigit() else value
    u=numeric(m.get('usage',{}))
    out=u.get('completion_tokens');reason=u.get('completion_tokens_details',{}).get('reasoning_tokens')
    return dict(case=row.get('case'),variant=row.get('variant'),phase=row.get('phase'),repeat=row.get('repeat'),
        error=row.get('error') or result.get('error'),model=m.get('model'),modelMs=m.get('durationMs'),wallMs=row.get('clientWallMs'),
        promptTokens=u.get('prompt_tokens'),completionTokens=out,reasoningTokens=reason,visibleTokens=out-reason if out is not None and reason is not None else None,
        estimatedCny=(u['prompt_tokens']*12+out*24)/1e6 if u.get('prompt_tokens') is not None and out is not None else None,
        cacheHit=u.get('prompt_cache_hit_tokens',u.get('prompt_tokens_details',{}).get('cached_tokens')),requestHash=row.get('requestHash'),requestChars=sum(len(m['content']) for m in response.get('modelRequest',{}).get('messages',[])),
        usage=u,stages=response.get('labStages',[]))
def summary(rows):
    good=[r for r in rows if not r['error'] and r['modelMs'] is not None]
    fields=['modelMs','wallMs','promptTokens','completionTokens','reasoningTokens','visibleTokens','estimatedCny','requestChars']
    known=[r['estimatedCny'] for r in rows if r['estimatedCny'] is not None]
    return dict(attempts=len(rows),successful=len(good),attemptWallMs=average([r['wallMs'] for r in rows if r['wallMs'] is not None]),knownFeeSum=sum(known),unknownFeeAttempts=len(rows)-len(known),returnedModels=dict(Counter(r['model'] for r in good)),**{f:average([r[f] for r in good if r[f] is not None]) for f in fields})
def main():
    raw=[json.loads(p.read_text(encoding='utf-8')) for p in OUT.glob('*.json') if p.name.startswith(('full-','replay-','no-knowledge-','dry-'))]
    rows=[measured(r) for r in raw if r.get('phase')!='retrieval']
    groups={side:summary([r for r in rows if r['phase'] in ['full','replay'] and r['variant']==side]) for side in ['A','B']}
    groups['noKnowledge']=summary([r for r in rows if r['phase']=='no-knowledge'])
    cases={key:{side:summary([r for r in rows if r['case']==key and r['variant']==side and r['phase']!='no-knowledge']) for side in ['A','B']} for key in KEYS}
    for key in KEYS:cases[key]['noKnowledge']=summary([r for r in rows if r['case']==key and r['phase']=='no-knowledge'])
    ranges={key:{side:{f:[min(vals),max(vals)] for f in ['modelMs','completionTokens','reasoningTokens'] if (vals:=[r[f] for r in rows if r['case']==key and r['variant']==side and r['phase']!='no-knowledge' and r[f] is not None])} for side in ['A','B']} for key in KEYS}
    dry=defaultdict(list)
    for r in raw:
        if r.get('phase')=='retrieval':
            stages=defaultdict(float)
            for s in r['response'].get('labStages',[]):stages[s['stage']]+=s['durationMs']
            dry[r['variant']].append(dict(case=r['case'],repeat=r['repeat'],stages=stages,trace=r['response']['retrieval']['trace']))
    stageMeans={side:{name:average([r['stages'].get(name,0) for r in values]) for name in sorted(set(n for r in values for n in r['stages']))} for side,values in dry.items()}
    fullStages={side:{name:average([sum(s['durationMs'] for s in r['stages'] if s['stage']==name) for r in rows if r['phase']=='full' and r['variant']==side]) for name in sorted(set(s['stage'] for r in rows if r['phase']=='full' for s in r['stages']))} for side in ['A','B']}
    ablations={}
    for key in KEYS:
        hashes={}
        for side in ['A','B']:
            capture=next((r for r in raw if r.get('phase')=='full' and r['case']==key and r['variant']==side),None)
            if not capture:continue
            body=json.loads(json.dumps(capture['response']['modelRequest']))
            knowledge=capture['response']['retrieval']['text']
            for message in body['messages']:
                if message['role']=='user' and knowledge:message['content']=message['content'].replace('\n\n## 知识库参考\n'+knowledge,'')
            hashes[side]=digest(body)
        ablations[key]=dict(hashes=hashes,common=len(hashes)==2 and len(set(hashes.values()))==1)
    matches=[]
    for key in KEYS:
        for repeat in [0,1,2]:
            a=next((r for r in rows if r['case']==key and r['repeat']==repeat and r['variant']=='A' and r['phase']!='no-knowledge'),None)
            b=next((r for r in rows if r['case']==key and r['repeat']==repeat and r['variant']=='B'),None)
            if a and b and a['model']==b['model'] and a['modelMs'] is not None and b['modelMs'] is not None and not a['error'] and not b['error']:matches.append((a,b))
    matched={side:summary([pair[index] for pair in matches]) for index,side in enumerate(['A','B'])}
    hashes=defaultdict(list)
    for row in rows:
        if row.get('requestHash') and row.get('modelMs') is not None:hashes[row['requestHash']].append(row)
    sameRequests={h:dict(n=len(values),labels=[r['phase']+'/'+r['case']+'/'+r['variant']+'/'+str(r['repeat']) for r in values],models=dict(Counter(r['model'] for r in values)),modelMsRange=[min(r['modelMs'] for r in values),max(r['modelMs'] for r in values)],reasoningRange=[min(r['reasoningTokens'] for r in values),max(r['reasoningTokens'] for r in values)],feeRange=[min(r['estimatedCny'] for r in values),max(r['estimatedCny'] for r in values)]) for h,values in hashes.items() if len(values)>1}
    good=[r for r in rows if not r['error'] and r['modelMs'] is not None and r['completionTokens'] is not None]
    correlations={field:st.correlation([r[field] for r in good],[r['modelMs'] for r in good]) if len(good)>2 and len(set(r[field] for r in good))>1 else None for field in ['completionTokens','reasoningTokens','visibleTokens','promptTokens']}
    result=dict(groups=groups,cases=cases,ranges=ranges,retrievalStageMeans=stageMeans,fullStageMeans=fullStages,ablations=ablations,matchedPairs=len(matches),matched=matched,sameRequests=sameRequests,correlations=correlations,rows=rows,dry=dry)
    (OUT/'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    lines=['# 成本与耗时归因原始统计','', '统一公开标价估算，不是账单。嵌套阶段不可相加。','', '| 分组 | 成功/尝试 | 模型秒 | 输入 Token | 输出 Token | 思考 Token | 非思考 Token | 均次估算元 |','|---|---:|---:|---:|---:|---:|---:|---:|']
    def fmt(value,digits=1):return '未知' if value is None else f'{value:.{digits}f}'
    def append(label,s):lines.append(f"|{label}|{s['successful']}/{s['attempts']}|{fmt(s['modelMs']/1000 if s['modelMs'] else None)}|{fmt(s['promptTokens'])}|{fmt(s['completionTokens'])}|{fmt(s['reasoningTokens'])}|{fmt(s['visibleTokens'])}|{fmt(s['estimatedCny'],5)}|")
    for name,s in groups.items():append(name,s)
    lines+=['','## 逐题重复均值','','| 分组 | 成功/尝试 | 模型秒 | 输入 Token | 输出 Token | 思考 Token | 非思考 Token | 均次估算元 |','|---|---:|---:|---:|---:|---:|---:|---:|']
    for key,c in cases.items():
        for side,s in c.items():append(key+' / '+side,s)
    lines+=['','## 检索阶段均值（毫秒，12 次/版）','','|阶段|A|B|','|---|---:|---:|']
    for name in sorted(set(n for v in stageMeans.values() for n in v)):lines.append(f"|{name}|{fmt(stageMeans.get('A',{}).get(name,0),3)}|{fmt(stageMeans.get('B',{}).get(name,0),3)}|")
    lines+=['','## 完整链路阶段均值（毫秒，4 次/版）','','|阶段|A|B|','|---|---:|---:|']
    for name in sorted(set(n for v in fullStages.values() for n in v)):lines.append(f"|{name}|{fmt(fullStages['A'].get(name,0),3)}|{fmt(fullStages['B'].get(name,0),3)}|")
    lines+=['','## 同返回版本配对子集', '',f'配对数：{len(matches)}，没有过滤失败后的替补调用。','','| 分组 | 成功/尝试 | 模型秒 | 输入 Token | 输出 Token | 思考 Token | 非思考 Token | 均次估算元 |','|---|---:|---:|---:|---:|---:|---:|---:|']
    for name,s in matched.items():append('matched '+name,s)
    lines+=['','## 冻结与公共无证据对照', '','```json',json.dumps(ablations,ensure_ascii=False,indent=2),'```','','## 相同请求波动范围','', '```json',json.dumps(ranges,ensure_ascii=False,indent=2),'```','','## 与模型耗时的相关系数（观察相关，不能单独证明因果）','','```json',json.dumps(correlations,indent=2),'```']
    (OUT/'statistics.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps(dict(groups=groups,matchedPairs=len(matches),ablations=ablations),ensure_ascii=False))
if __name__=='__main__':main()
