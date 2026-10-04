"""Offline metrics and explicitly human-reviewed findings; no scoring model calls."""
import hashlib
import json
import math
import statistics
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/customer/free-topics-v412'
definitions=json.loads((OUT/'cases.json').read_text(encoding='utf-8'))
rows=[json.loads((OUT/(c['id']+'.json')).read_text(encoding='utf-8')) for c in definitions]
turns=[t for r in rows for t in r['turns']]
calls=[c for t in turns for c in t['metrics'].get('modelCalls',[])]
pairs=[]
for family in ['data','office','browser','web','frontend','inventory']:
    members={r['case']['kind']:r['turns'][0] for r in rows if r['case']['family']==family}
    counts={k:len(v['result'].get('projects',[])) for k,v in members.items()}
    pairs.append({'family':family,'projectCounts':counts,'finding':'lexical_false_negative' if counts['free']==0 and counts['specific']>0 else 'normalization_worked' if counts['free']>0 else 'unresolved_catalog_or_retrieval_gap'})

findings=[
    {'id':'F1','severity':'high','title':'宽泛主题和任务语义被词面门槛排除',
     'cases':['data_free','office_free','web_free','frontend_free'],
     'evidence':'对应具体主题均有返回；data_free 的检索步骤已召回数据可视化、网页抓取与展示资料，后续项目筛选仍为空。',
     'code':'CustomerTopics.matches / aliases; CustomerProjects.topicMatch'},
    {'id':'F2','severity':'high','title':'弱相关依赖命中可成为正式主题匹配',
     'cases':['data_specific'],
     'evidence':'3D-printed-mirror-array 的功能证据是3D打印反射镜阵列，依赖Jupyter/NumPy；数据分析别名把依赖命中当作主题正相关。三个返回项目semanticMatched均为false。nbdev为文档/开发工具，单靠Jupyter标签不足以认定为数据分析应用。aidizhi/455555.xyz有图表渲染证据，可作为可视化工具候选。',
     'code':'CustomerTopics.aliases(数据分析); CustomerProjects.ranking'},
    {'id':'F3','severity':'high','title':'无结果建议存在目标偏移',
     'cases':['frontend_free','inventory_free','inventory_specific','unknown_quantum'],
     'evidence':'前端界面、库存管理和量子芯片请求均被建议改为智能体；虽然没有自动修改状态，也不再显示候选数，但建议与原目标缺少相关性依据。',
     'code':'CustomerDiagnostics.evaluate: 固定主题清单、按confirmed数量排序、同字段只保留一个方案'},
    {'id':'F4','severity':'medium','title':'资料未知的同义表达引起不必要澄清',
     'cases':['hard_java_browser'],
     'evidence':'Java和allowDocker=false正确保存；verificationMode被记录为INVALID_VERIFICATION_MODE。原话“资料未知可以明确标记未知”不包含当前校验接受的核实/核验/确认/探索/资料缺失/资料不足，因而进入待确认。',
     'code':'CustomerState.apply verificationMode'},
    {'id':'F5','severity':'medium','title':'有卡片不等于说明完整或相关性已验证',
     'cases':['data_specific','web_specific'],
     'evidence':'nbdev和WindowsCleaner卡片缺少本轮description与supportedExplanation；WindowsCleaner对Next.js的实际适用性仅凭本轮输出不能确认，不能判为推荐正确。',
     'code':'CustomerProjects.search; CustomerConversation.enrichWithEvidence'},
]
latencies=sorted(t['wallMs'] for t in turns)
metrics={
    'sessions':len(rows),'turns':len(turns),'completed':sum(t['status']=='SUCCEEDED' for t in turns),
    'clarificationTurns':sum(t['result'].get('status')=='NEEDS_CLARIFICATION' for t in turns),
    'turnsWithProjects':sum(bool(t['result'].get('projects')) for t in turns),
    'cards':sum(len(t['result'].get('projects',[])) for t in turns),
    'cardsWithAdvice':sum(bool(c.get('practiceAdvice')) for t in turns for c in t['result'].get('projects',[])),
    'meanMs':round(statistics.mean(latencies)),'p95Ms':latencies[math.ceil(.95*len(latencies))-1],'maxMs':max(latencies),
    'modelCalls':len(calls),'retryCalls':sum(c.get('attempt',1)>1 for c in calls),
    'inputTokens':sum(c.get('usage',{}).get('prompt_tokens',0) for c in calls),
    'outputTokens':sum(c.get('usage',{}).get('completion_tokens',0) for c in calls),
    'monetaryCost':'UNKNOWN','embeddingUsage':'NOT_REPORTED',
    'promptVersions':sorted(set(t['metrics'].get('promptVersion','unknown') for t in turns)),
    'generations':sorted(set(t['result']['generation'] for t in turns if t['result'].get('generation'))),
}
dialogue=next(r for r in rows if r['case']['id']=='dialogue_scope')
states=[t['result'].get('state',{}) for t in dialogue['turns']]
checks={
    'allSyntheticSessionsDeleted':all(r.get('sessionDeleted') for r in rows),
    'durationStateSequence':[s.get('maxDurationDays') for s in states],
    'topicStateSequence':[s.get('topic') for s in states],
    'careerGoalRetained':all(s.get('goal')=='求职' for s in states),
    'noneOfTestRepliesExposeCandidateCounts':all('候选共' not in t['result'].get('answer','') and '个项目通过条件检查' not in t['result'].get('answer','') for t in turns),
}
sourceFiles=['CustomerTopics.java','CustomerProjects.java','CustomerDiagnostics.java','CustomerState.java']
summary={'metrics':metrics,'pairedResults':pairs,'checks':checks,'manualFindings':findings,
    'sourceSha256':{name:hashlib.sha256((ROOT/'orange-server/src/main/java/com/orangejd/server/customer'/name).read_bytes()).hexdigest() for name in sourceFiles},
    'limitations':['每个输入仅执行一轮，不代表稳定成功率','服务成功与推荐质量分开判断，没有把17次SUCCEEDED当作17次业务验收通过','尚无全库人工相关性标注，不能计算可靠Recall或语义准确率','库存管理两种表达均无结果，不能据此认定全库不存在相关项目','金额未估算，报告仅记录供应商实际返回Token；Embedding用量未知']}
(OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'metrics':metrics,'pairs':pairs,'checks':checks},ensure_ascii=False,indent=2))
