"""Recommendation-evidence benchmark on the frozen three-arm corpus, not final Agent success."""
import argparse
import collections
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import readme_three_arm as base
import readme_three_arm_repair as previous

ROOT=base.ROOT
OLD=base.OUT
OUT=ROOT/'docs/rag-b/readme-filter-v1/recommendation-retrieval-v1'
STAGE=ROOT/'_experiment_staging/readme-recommendation-v1'
JAVA='orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeRecommendationRetrievalTest.java'
read=base.read
write=base.write
sha=base.sha

def history():previous.verify()

def prepare():
    history();assert not (OUT/'manifest.json').exists(),'Inputs are frozen'
    sources={s['documentId']:s for s in read(OLD/'sources.json')['real']}
    cases=[]
    def add(cid,category,mode,query,requirements,projects,notes=''):
        candidates=[]
        for doc,verdict,markers,limits in projects:
            candidates.append({'documentId':doc,'expectedVerdict':verdict,
               'evidenceGroups':[base.refs(sources[doc],m) for m in markers],
               'limitGroups':[base.refs(sources[doc],m) for m in limits]})
        cases.append({'id':cid,'category':category,'useMode':mode,'query':query,
          'requirements':[{'text':text,'strength':strength} for text,strength in requirements],
          'candidates':candidates,'notes':notes,'verdictAuthority':'ASSISTANT_SOURCE_ANNOTATION_NOT_EXECUTED_AGENT_DECISION'})
    H='HARD';S='SOFT';OK='SUPPORTED';NO='CONFLICT';UNKNOWN='UNKNOWN'
    add('D01','DISCOVER','DIRECT_DEPLOY','想找支持与文档对话、多人使用的AI应用，先推荐有明确文档依据的项目。',[('文档对话',H),('多用户',H)],
        [('240',OK,['与您的文档对话'],[])])
    add('D02','DISCOVER','SECONDARY_DEV','必须能用于搭建企业内部管理后台和仪表盘，最好是低代码平台，找主要用途贴近的项目。',[('低代码',S),('管理后台及仪表盘',H)],
        [('522',OK,['Organizations build custom applications'],[])])
    add('D03','DISCOVER','SECONDARY_DEV','知识库入库前要静态扫描隐藏内容，要求Go编写、不依赖第三方包，并可接入CI，推荐什么项目？',[('Go',H),('无第三方包依赖',H),('入库前扫描及CI',H)],
        [('546',OK,['A concurrent Go CLI','Zero external dependencies','is a single static binary'],['This is a heuristic tripwire'])])
    add('D04','DISCOVER','SECONDARY_DEV','希望可视化编排AI工作流，还能写JavaScript和Python自定义代码，有什么项目有这些明确说明？',[('可视化工作流',H),('JavaScript及Python扩展',H)],
        [('427',OK,['这是一个公平代码','将可视化构建与 JavaScript'],[])])
    add('D05','DISCOVER','SECONDARY_DEV','要修改一个React前端、Node后端的AI工具，并研究第三方节点集成，找README明确描述这些模块的项目。',[('React前端及Node后端',H),('第三方节点集成',H)],
        [('244',OK,['`server`：Node 后端','`ui`：React 前端','`components`：第三方节点集成'],[])])
    add('D06','DISCOVER','STUDY_SOURCE','想读Go命令行工具的源码，重点研究goroutine worker-pool与channel收集结果，推荐有明确实现说明的项目。',[('Go源码',H),('并发池及channel',H)],
        [('546',OK,['A concurrent Go CLI','walks the target directory'],[])])
    add('D07','DISCOVER','SECONDARY_DEV','给文档CI增加扫描，需要能输出JSON并按告警级别让构建失败，推荐有明确接入示例的工具。',[('JSON输出',H),('按严重级别阻断CI',H)],
        [('546',OK,['docguard --json','docguard --fail-on medium','Exit code is `1`'],['This is a heuristic tripwire'])])
    add('D08','DISCOVER','DIRECT_DEPLOY','想自托管可视化工作流，要求README给出Docker数据持久化的运行示例，请找有依据的项目。',[('自托管工作流',H),('Docker持久化示例',H)],
        [('427',OK,['这是一个公平代码','docker volume create n8n_data','docker run -it --rm'],[])])
    add('V01','VERIFY','DIRECT_DEPLOY','我只能使用Node.js 18而且不能升级，按当前README直接安装Flowise，版本条件是否冲突？',[('Node.js固定18，不升级',H)],
        [('244',NO,['版本 >= 20.0.0'],[])])
    add('V02','VERIFY','STUDY_SOURCE','我只能使用Go 1.21且不能升级，按当前README构建Docguard，Go版本是否符合？',[('Go固定1.21',H)],
        [('546',NO,['Requires Go 1.22+'],[])])
    add('V03','VERIFY','SECONDARY_DEV','我只接受Go标准库、不允许增加第三方Go包，Docguard的依赖说明能否支持这一条件？',[('仅Go标准库',H)],
        [('546',OK,['Zero external dependencies'],[])])
    add('V04','VERIFY','DIRECT_DEPLOY','我的要求是保证检测出所有提示注入攻击，Docguard的README是否承诺了这种保证？',[('保证发现所有攻击',H)],
        [('546',NO,['This is a heuristic tripwire'],['This is a heuristic tripwire'])])
    add('V05','VERIFY','SECONDARY_DEV','筛选条件是README标明Apache License 2.0，Appsmith的许可证名称是否符合？只核对名称。',[('许可名称为Apache License 2.0',H)],
        [('522',OK,['Appsmith is licensed under'],[])],'仅比较原文许可名称，不解释商业权利或法律义务。')
    add('V06','VERIFY','SECONDARY_DEV','我要二次开发React前端和Node后端，Flowise已有模块说明是否支持这两个技术条件？',[('React及Node',H)],
        [('244',OK,['`server`：Node 后端','`ui`：React 前端'],[])])
    add('V07','VERIFY','DIRECT_DEPLOY','我不接受只有云服务入口的方案，n8n当前README有没有自托管部署的明确依据？',[('可以自托管',H)],
        [('427',OK,['支持自托管','docker run -it --rm'],[])])
    add('V08','VERIFY','DIRECT_DEPLOY','要求默认扫描扩展名就包含pdf且不改配置，Docguard的README默认列表是否满足？',[('默认扩展名含pdf且不改配置',H)],
        [('546',NO,['default: txt,md,mdx'],[])],'只核对默认列表，不推断PDF解析能力或自定义扩展名支持范围。')
    add('C01','COMPARE','SECONDARY_DEV','想做管理后台和仪表盘，比较Appsmith与Flowise README的主要定位，哪些原文直接支持这个选择？',[('管理后台及仪表盘',H)],
        [('522',OK,['Organizations build custom applications'],[]),('244',UNKNOWN,['可视化构建 AI 智能体'],[])],'Flowise缺少该用途证据不等于不能通过二次开发实现。')
    add('C02','COMPARE','STUDY_SOURCE','想学习Go源码中的worker-pool，比较Docguard和Flowise的语言及实现说明，分别有哪些支持或缺失的依据？',[('Go worker-pool源码',H)],
        [('546',OK,['A concurrent Go CLI','walks the target directory'],[]),('244',UNKNOWN,['`server`：Node 后端'],[])])
    add('C03','COMPARE','SECONDARY_DEV','我想在文档入库前增加静态扫描，比较Docguard和AnythingLLM各自的主要用途，哪个有直接扫描依据？',[('入库前静态扫描',H)],
        [('546',OK,['is a single static binary'],['This is a heuristic tripwire']),('240',UNKNOWN,['与您的文档对话'],[])])
    add('C04','COMPARE','SECONDARY_DEV','需要可视化工作流和Python自定义代码，比较n8n与Flowise README对扩展方式的说明，哪些条件仍缺证据？',[('可视化工作流',H),('Python自定义代码',H)],
        [('427',OK,['将可视化构建与 JavaScript'],[]),('244',UNKNOWN,['`components`：第三方节点集成'],[])])
    add('O01','OBSERVATION','DIRECT_DEPLOY','只有4GB内存的服务器能保证生产运行Flowise吗？README里的4096堆参数是否足够证明？',[('4GB生产运行保证',H)],
        [('244',UNKNOWN,['错误代码 134','$env:NODE_OPTIONS='],[])],'这些是排障线索，不能证明最低机器内存。')
    add('O02','OBSERVATION','DIRECT_DEPLOY','要求免费社区版开箱即用支持单点登录，当前Appsmith README足以核实吗？',[('免费社区版现成SSO',H)],
        [('522',UNKNOWN,[],[])],'README中没有充分的功能版别及收费证据；返回候选不算回答正确。')
    add('O03','OBSERVATION','STUDY_SOURCE','我是Java初学者，每天一小时，能保证两周吃透AnythingLLM源码吗？',[('两周且每天一小时',H)],
        [('240',UNKNOWN,['与您的文档对话'],[])],'产品定位不能证明学习工期和个人适配。')
    add('O04','OBSERVATION','DIRECT_DEPLOY','我要求任何商业场景都可免费使用，n8n README里列出的许可链接能直接证明吗？',[('所有商业场景均免费',H)],
        [('427',UNKNOWN,['依据[可持续使用许可证]'],[])],'只出现许可名称和链接，未读取完整条款，不得推出商业许可结论。')
    add('O05','OBSERVATION','DIRECT_DEPLOY','Appsmith Core免费版必须内置README里Appsmith Agents介绍的能力，当前原文能证明吗？',[('Core免费版内置Agents能力',H)],
        [('522',UNKNOWN,['Introducing a new agentic AI platform'],[])],'关联产品介绍不能作为Core版别能力的证据。')
    add('O06','OBSERVATION','DIRECT_DEPLOY','要求工具运行时完全不联网，Docguard所说零外部依赖足以证明这一条件吗？',[('运行时完全离线',H)],
        [('546',UNKNOWN,['Zero external dependencies'],[])],'第三方包依赖与运行时网络行为不同；不推断不支持离线。')
    assert collections.Counter(c['category'] for c in cases)=={'DISCOVER':8,'VERIFY':8,'COMPARE':4,'OBSERVATION':6}
    write(OUT/'cases.json',{'annotation':'Assistant source-labelled diagnostic on five previously studied README snapshots, not blind or independent. Verifier/Agent decisions are not executed.',
                           'source':'../retrieval-three-arm-v1/sources.json','cases':cases})
    lines=['# 推荐专项冻结问题集','','20道主检索题（8找候选、8条件核验、4对比）及6道不足观察题。标签从完整原文确定，不以已准入内容为准。','',
       '| 编号 | 类型 | 使用方式 | 问题 | 原文标注的候选状态 |','|---|---|---|---|---|']
    for c in cases:lines.append(f"| {c['id']} | {c['category']} | {c['useMode']} | {c['query']} | "+'; '.join(sources[p['documentId']]['sourceRef']+': '+p['expectedVerdict'] for p in c['candidates'])+' |')
    (OUT/'questions.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    print(json.dumps({'status':'RECOMMENDATION_GOLD_PREPARED','mainCases':20,'observations':6}))

def validate():
    cs=read(OUT/'cases.json')['cases'];sources={s['documentId']:s for s in read(OLD/'sources.json')['real']}
    assert len(cs)==26 and len({c['id'] for c in cs})==26
    for c in cs:
        assert c['requirements'] and c['useMode'] in ['DIRECT_DEPLOY','SECONDARY_DEV','STUDY_SOURCE']
        for p in c['candidates']:
            assert p['expectedVerdict'] in ['SUPPORTED','CONFLICT','UNKNOWN']
            if c['category']!='OBSERVATION':assert p['evidenceGroups']
            for group in p['evidenceGroups']+p['limitGroups']:
                assert group
                for span in group:
                    text=sources[span['documentId']]['text'].encode('utf-16-le')
                    assert text[span['startUtf16']*2:span['endUtf16']*2].decode('utf-16-le')==span['text']

def freeze():
    history();validate();assert not (OUT/'manifest.json').exists()
    corpus=read(OLD/'corpus.json');cached={x['textSha256'] for x in read(OLD/'vectors.json')}
    queries=list(dict.fromkeys(c['query'] for c in read(OUT/'cases.json')['cases']))
    missing=[q for q in queries if base.hashlib.sha256(q.encode()).hexdigest() not in cached]
    batches=[missing[i:i+8] for i in range(0,len(missing),8)]
    write(OUT/'requests.json',{'localProvider':'OLLAMA','revision':base.REVISION,'newQueryTexts':missing,'batchSize':8,'maximumRequests':len(batches),'documentEmbeddingRequests':0,'cloudRequests':0,'retries':0})
    files=[ROOT/'scripts/readme_recommendation_eval.py',ROOT/'scripts/test_readme_recommendation_eval.py',ROOT/JAVA,OUT/'cases.json',OUT/'questions.md',OUT/'requests.json',OUT/'plan.md',
           OLD/'corpus.json',OLD/'vectors.json',OLD/'admission.json',OLD/'completion-seal.json',OLD/'repair-manifest.json']
    write(OUT/'manifest.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in files}})
    print(json.dumps({'status':'RECOMMENDATION_INPUTS_FROZEN','newQueryTexts':len(missing),'localRequests':len(batches),'oldCorpusChunks':dict(collections.Counter(c['variant'] for c in corpus if c['scope']=='REAL'))}))

def check():
    for name,digest in read(OUT/'manifest.json')['files'].items():assert sha(ROOT/name)==digest,name

def run():
    history();check();assert not (OUT/'live-started.json').exists(),'One-shot run; preserve partial requests'
    args=['docker','run','--rm','--pull','never','--network','container:'+base.CONTAINER,'-e','MAVEN_OPTS=-Xmx512m',
          '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
          'mvn','-o','-B','-pl','orange-server','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m',
          '-Drecommendation.root=/workspace','-Dorange.acceptance.limit='+str(read(OUT/'requests.json')['maximumRequests']),
          '-Dorange.acceptance.ledger=/workspace/docs/rag-b/readme-filter-v1/recommendation-retrieval-v1/local-embedding-calls.jsonl',
          '-Dtest=ReadmeRecommendationRetrievalTest#queryFrozenThreeArmIndexes','test']
    STAGE.mkdir(parents=True,exist_ok=True)
    with (STAGE/'live.log').open('w',encoding='utf8') as log:r=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    assert r.returncode==0,'Java retrieval failed: '+str(STAGE/'live.log')
    report=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.retrieval.ReadmeRecommendationRetrievalTest.xml'
    attrs=ET.parse(report).getroot().attrib;assert int(attrs['tests'])==1 and all(int(attrs[k])==0 for k in ['failures','errors','skipped'])
    shutil.copyfile(report,STAGE/report.name);evaluate()

def candidate_score(candidate,spans):
    support=[any(base.covered(g,spans) for g in group) for group in candidate['evidenceGroups']]
    limits=[any(base.covered(g,spans) for g in group) for group in candidate['limitGroups']]
    return {'factsFound':sum(support),'factsTotal':len(support),'limitsFound':sum(limits),'limitsTotal':len(limits),
            'proofComplete':bool(support) and all(support),'allEvidenceComplete':bool(support) and all(support) and all(limits)}

def evaluate():
    check();history();assert not (OUT/'completion-seal.json').exists()
    cases={c['id']:c for c in read(OUT/'cases.json')['cases']};corpus={c['id']:c for c in read(OLD/'corpus.json')}
    results=read(OUT/'retrieval-results.json');assert len(results)==26;details=[]
    common=[{'documentId':a['documentId'],'startUtf16':x,'endUtf16':y} for a in read(OLD/'admission.json') for x,y in a['commonApprovedIntervals']]
    for r in results:
        case=cases[r['id']]
        for variant,reps in r['variants'].items():
            assert len(reps)==3
            stable=all([h['id'] for h in x['hits']]==[h['id'] for h in reps[0]['hits']] for x in reps)
            for k in [1,3,5,10]:
                for mode,expand,budget in [('DIRECT',False,None),('EXPANDED_2048',True,2048)]:
                    selected,spans,tokens,deferred=base.selection(reps[0]['hits'],corpus,k,expand,budget)
                    candidates=[]
                    for p in case['candidates']:
                        score=candidate_score(p,spans);allspans=[s for c in corpus.values() if c['variant']==variant for s in c['expandedSpans']]
                        available=candidate_score(p,allspans);admitted=candidate_score(p,common)
                        candidates.append({'documentId':p['documentId'],'expectedVerdict':p['expectedVerdict'],**score,
                          'evidencePurpose':'RELATED_CLUE_ONLY' if case['category']=='OBSERVATION' else 'FACTS_FOR_ADJUDICATION',
                          'availability':available,'admission':admitted,'missingReason':'NONE' if score['allEvidenceComplete'] else 'ADMISSION_GAP' if variant!='A' and not admitted['allEvidenceComplete'] else 'SUPPLY_GAP' if not available['allEvidenceComplete'] else 'RANKING_OR_BUDGET'})
                    supported=[p['documentId'] for p in case['candidates'] if p['expectedVerdict']=='SUPPORTED']
                    _,unique=base.span_chars(spans);total=sum(s['endUtf16']-s['startUtf16'] for s in spans)
                    details.append({'id':case['id'],'category':case['category'],'useMode':case['useMode'],'variant':variant,'K':k,'mode':mode,
                      'proofComplete':case['category']!='OBSERVATION' and all(p['proofComplete'] for p in candidates),
                      'allEvidenceComplete':case['category']!='OBSERVATION' and all(p['allEvidenceComplete'] for p in candidates),
                      'qualifiedCandidateHit':bool(supported) and any(h['documentId'] in supported for h in reps[0]['hits'][:k]),
                      'candidateHitApplicable':bool(supported),
                      'tokens':tokens,'duplicateSourceChars':total-unique,'sourceChars':total,'deferred':deferred,'stable':stable,'candidates':candidates,
                      'agentDecisionEvaluated':False})
    metrics=[]
    for variant in ['A','B','C']:
      for k in [1,3,5,10]:
       for mode in ['DIRECT','EXPANDED_2048']:
        for category in ['MAIN','DISCOVER','VERIFY','COMPARE']:
            rows=[d for d in details if d['variant']==variant and d['K']==k and d['mode']==mode and (d['category']!='OBSERVATION' if category=='MAIN' else d['category']==category)]
            metrics.append({'variant':variant,'K':k,'mode':mode,'category':category,'cases':len(rows),'proofComplete':sum(d['proofComplete'] for d in rows),'allEvidenceComplete':sum(d['allEvidenceComplete'] for d in rows),
              'candidateHit':sum(d['qualifiedCandidateHit'] for d in rows),'candidateHitEligibleCases':sum(d['candidateHitApplicable'] for d in rows),'meanTokens':round(sum(d['tokens'] for d in rows)/max(1,len(rows)),1),
              'factsFound':sum(p['factsFound'] for d in rows for p in d['candidates']),'factsTotal':sum(p['factsTotal'] for d in rows for p in d['candidates']),
              'limitsFound':sum(p['limitsFound'] for d in rows for p in d['candidates']),'limitsTotal':sum(p['limitsTotal'] for d in rows for p in d['candidates'])})
    write(OUT/'evaluation.json',{'metrics':metrics,'cases':details,'annotation':'Evidence retrieval only. Expected verdicts are assistant annotations; no Agent/model decisions executed. Observation clue hits never count as sufficient evidence.'})
    lines=['# 推荐专项：A/B/C纯向量检索结果','','20道主检索题、6道不足观察题；原文、分类及三组分块完全沿用前轮。仅新增查询，三组共用查询向量。无关键词、名称加分、生成或判断模型。','',
       '| 组别 | 找到候选 /8 | 找候选证据完整 /8 | 条件核验证据完整 /8 | 比较证据完整 /4 | 主集完整 /20 | 加必要限制 /20 |','|---|---:|---:|---:|---:|---:|---:|']
    for v in ['A','B','C']:
        ms={m['category']:m for m in metrics if m['variant']==v and m['K']==5 and m['mode']=='EXPANDED_2048'}
        lines.append(f"| {v} | {ms['DISCOVER']['candidateHit']}/8 | {ms['DISCOVER']['proofComplete']}/8 | {ms['VERIFY']['proofComplete']}/8 | {ms['COMPARE']['proofComplete']}/4 | {ms['MAIN']['proofComplete']}/20 | {ms['MAIN']['allEvidenceComplete']}/20 |")
    lines+=['','“证据完整”只说明找齐了标注事实；不能解释为模型已正确判定满足、冲突或未知。限制列额外要求找齐必要风险。比较题需要双方证据，不因命中一方就算成功。','',
      '## 逐题Top5、2048预算','','| 题目 | 原文预期 | A | B | C |','|---|---|---|---|---|']
    for c in cases.values():
        rows=[next(d for d in details if d['id']==c['id'] and d['variant']==v and d['K']==5 and d['mode']=='EXPANDED_2048') for v in ['A','B','C']]
        def label(d):
            if c['category']=='OBSERVATION':return '线索'+str(sum(p['factsFound'] for p in d['candidates']))+'/'+str(sum(p['factsTotal'] for p in d['candidates']))+'；不足，未测决策'
            return '完整' if d['allEvidenceComplete'] else '; '.join(p['documentId']+': '+p['missingReason'] for p in d['candidates'] if not p['allEvidenceComplete'])
        lines.append(f"| {c['id']} {c['query']} | "+', '.join(p['documentId']+': '+p['expectedVerdict'] for p in c['candidates'])+' | '+' | '.join(label(d) for d in rows)+' |')
    cs=[d for d in details if d['category']!='OBSERVATION' and d['K']==5 and d['mode']=='EXPANDED_2048']
    pairs=[]
    for a,b in [('A','B'),('B','C'),('A','C')]:
        aa={d['id']:d for d in cs if d['variant']==a};bb={d['id']:d for d in cs if d['variant']==b}
        pairs.append({'comparison':a+'→'+b,'wins':[cid for cid in aa if bb[cid]['allEvidenceComplete'] and not aa[cid]['allEvidenceComplete']],
                      'losses':[cid for cid in aa if aa[cid]['allEvidenceComplete'] and not bb[cid]['allEvidenceComplete']]})
    write(OUT/'paired-comparison.json',pairs)
    lines+=['','## 对照结论','']
    for p in pairs:lines.append(p['comparison']+'：改善 '+(', '.join(p['wins']) or '无')+'；退步 '+(', '.join(p['losses']) or '无')+'。')
    lines+=['','本轮使用5个已研究过的仓库、助手标注，不是独立人工评测；多个任务可能使用相同项目事实，不能把它们当成互相独立的统计样本。只测检索证据，不测最终推荐、拒答、追问、导入资格或全库效果。',
      '', 'B组仍含前轮连续区间切分造成的纯标题和短块；本轮不改分块以保持输入不变，A/B差值包含这种表示变化，不全部归因于内容删除。观察题中的线索不证明目标条件，未调用模型就不报告正确拒答率。',
      '', '复验：python scripts/readme_recommendation_eval.py verify。完整问题、要求强弱、使用方式和原文标签见questions.md及cases.json。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    ledger=OUT/'local-embedding-calls.jsonl';calls=len(ledger.read_text().splitlines()) if ledger.exists() else 0
    assert calls==read(OUT/'requests.json')['maximumRequests']
    check();history()
    paths=[p for p in OUT.rglob('*') if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT/'completion-seal.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths},'newLocalEmbeddingRequests':calls,'cloudCalls':0,'indexWrites':0,'mainCases':20,'observations':6})
    verify()

def verify():
    history();check()
    for name,digest in read(OUT/'completion-seal.json')['files'].items():assert sha(ROOT/name)==digest,name
    print(json.dumps({'status':'RECOMMENDATION_RETRIEVAL_VERIFIED','mainCases':20,'observations':6,'newLocalRequests':read(OUT/'completion-seal.json')['newLocalEmbeddingRequests'],'cloudCalls':0,'indexWrites':0}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['prepare','freeze','run','evaluate','verify']);a=p.parse_args()
    try:globals()[a.phase]()
    except Exception as ex:print(str(ex),file=sys.stderr);sys.exit(1)
