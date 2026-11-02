"""Three-arm, body-only vector retrieval. Old experiments are read-only; no business services run."""
import argparse
import ast
import collections
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/readme-filter-v1/retrieval-three-arm-v1'
STAGE=ROOT/'_experiment_staging/readme-three-arm-v1'
BASE=ROOT/'docs/rag-b/readme-filter-v1'
PILOT=BASE/'k3/quality-v2/v5-pilot-v3'
CONTAINER='orangejd-readme-three-arm-v1'
REVISION='sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab'

def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def history():
    subprocess.run([sys.executable,str(ROOT/'scripts/readme_content_query_replay.py'),'verify'],cwd=ROOT,check=True)
    subprocess.run([sys.executable,str(ROOT/'scripts/check_customer_evidence_gap_scope.py'),'--verify'],cwd=ROOT,check=True)
def utf16(s):return len(s.encode('utf-16-le'))//2
def refs(source,marker):
    result=[];at=0
    for line in source['text'].splitlines(keepends=True):
        if marker in line:
            text=line.rstrip('\r\n')
            result.append({'documentId':source['documentId'],'startUtf16':utf16(source['text'][:at]),
                           'endUtf16':utf16(source['text'][:at])+utf16(text),'text':text})
        at+=len(line)
    assert result,(source['documentId'],marker)
    return result

def fixtures():
    intro=lambda name:f'# {name}\nThis is an application for managing inventory records.\n\n'
    prose='Inventory entries retain their owner, warehouse, revision and export history. '
    rows=lambda count:''.join(f'| region-{i:02} | Linux | Java 21 | {i+2} GB | Enterprise only |\n' for i in range(count))
    return [
      {'documentId':'S1','versionId':'1','sourceRef':'synthetic/long-prose','text':intro('Long prose')+'## Features\n'+prose*60+'Export requires an administrator. Windows is not supported.\n',
       'questions':[('导出仓库记录需要什么权限？',['Export requires an administrator.']),('该应用支持Windows吗？',['Windows is not supported.'])]},
      {'documentId':'S2','versionId':'1','sourceRef':'synthetic/dependencies','text':intro('Dependent deployment')+'## Installation\nRequires Java 21 and Linux. Windows is not supported.\n\n1. Deployment:\n\n    - '+prose*45+'Set INVENTORY_MODE=isolated before starting.\n\n    ```sh\n    ./inventory start --isolated\n    ```\n',
       'questions':[('部署前要设置什么隔离变量，启动命令是什么？',['Set INVENTORY_MODE=isolated','./inventory start --isolated']),('隔离服务的Java版本和操作系统要求是什么？',['Requires Java 21 and Linux.'])]},
      {'documentId':'S3','versionId':'1','sourceRef':'synthetic/long-table','text':intro('Long resource table')+'## Enterprise requirements\n| Region | OS | Runtime | Memory | Edition |\n|---|---|---|---|---|\n'+rows(65),
       'questions':[('region-02所需内存和适用版别是什么？',['| Region | OS | Runtime | Memory | Edition |','| region-02 |']),('region-60所需内存、系统与运行时是什么？',['| Region | OS | Runtime | Memory | Edition |','| region-60 |'])]},
      {'documentId':'S4','versionId':'1','sourceRef':'synthetic/edition-table','text':intro('Edition separation')+'## Community\nCommunity supports local exports only. Audit export is not included.\n\n## Enterprise\n| Region | OS | Runtime | Memory | Edition |\n|---|---|---|---|---|\n'+rows(35),
       'questions':[('社区版包含审计导出吗？',['Community supports local exports only.']),('region-30的运行要求属于哪个版别？',['| Region | OS | Runtime | Memory | Edition |','| region-30 |'])]},
      {'documentId':'S5','versionId':'1','sourceRef':'synthetic/code-step','text':intro('Bounded code')+'## Installation\nRequires Linux and Java 21. Use an isolated test database.\n\n```sh\n'+''.join(f'echo preparing-record-{i}\n' for i in range(65))+'./inventory migrate --dry-run\n./inventory start --read-only\n```\n',
       'questions':[('如何只演练迁移而不实际修改数据？',['./inventory migrate --dry-run','Requires Linux and Java 21.']),('启动只读服务的命令及前置环境是什么？',['./inventory start --read-only','Requires Linux and Java 21.'])]},
      {'documentId':'S6','versionId':'1','sourceRef':'synthetic/oversized-code','text':intro('Atomic code over limit')+'## Installation\nThe following complete script must run in order on Linux.\n\n```sh\n'+''.join(f'echo processing-inventory-record-{i}\n' for i in range(160))+'./inventory validate --strict\n./inventory publish --staging-only\n```\n',
       'questions':[('完整脚本最后如何执行严格校验？',['./inventory validate --strict']),('该脚本最终发布到哪里，用什么命令？',['./inventory publish --staging-only'])]},
    ]

def prepare():
    history();assert not (OUT/'experiment-manifest.json').exists(),'Inputs are already frozen'
    sources=[x['source'] for x in read(PILOT/'sources.json')['documents']]
    synthetic=fixtures()
    write(OUT/'sources.json',{'real':sources,'synthetic':[{k:v for k,v in s.items() if k!='questions'} for s in synthetic]})
    tree=ast.parse((ROOT/'scripts/readme_chunk_retrieval.py').read_text(encoding='utf8'))
    specs=next(ast.literal_eval(n.value) for n in ast.walk(tree) if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='specs' for t in n.targets))
    specs[5]=(specs[5][0],'244',['`server`：Node 后端','`ui`：React 前端','`components`：第三方节点集成'],'ARCHITECTURE')
    specs[7]=(specs[7][0],'244',['错误代码 134','# Windows PowerShell','$env:NODE_OPTIONS='],'CONDITION')
    specs[19]=(specs[19][0],'244',['VITE_PORT'],'SOURCE_PRESENT_WITHHELD')
    specs += [
      ('n8n用npx试运行的命令和前置依赖是什么？','427',['使用 [npx]','npx n8n'],'PROCEDURE'),
      ('n8n的Docker运行如何保存数据卷？','427',['docker volume create n8n_data','docker run -it --rm'],'PROCEDURE'),
      ('n8n本地编辑器使用哪个地址和端口？','427',['访问编辑器：http://localhost:5678'],'VERSION'),
      ('Flowise的Docker Compose启动前如何准备环境文件？','244',['复制 `.env.example`','docker compose up -d'],'PROCEDURE'),
      ('如何停止Flowise本地Docker镜像？','244',['docker stop flowise'],'PROCEDURE'),
      ('Flowise源码构建后用什么命令启动？','244',['pnpm build','pnpm start'],'PROCEDURE'),
      ('Appsmith README列出了哪两种开始使用的方式？','522',['Signup on [Appsmith Cloud]','Install Appsmith on your machine'],'DEPLOYMENT'),
      ('README中单独介绍的Appsmith Agents是什么产品？','522',['Introducing a new agentic AI platform'],'RELATED_PRODUCT'),
      ('AnythingLLM的介绍明确提到了哪些定制和多用户能力？','240',['与您的文档对话'],'FEATURE'),
      ('Docguard列出了哪些Go静态检查和并发测试命令？','546',['go test -race'],'TESTS'),
      ('Docguard如何只检查Markdown和HTML文件？','546',['docguard --ext md,html'],'PROCEDURE'),
      ('Docguard默认什么级别告警会导致退出码1，其他情况是什么退出码？','546',['Exit code is `1`'],'CONDITION'),
    ]
    lookup={s['documentId']:s for s in sources};cases=[]
    def add(cid,query,doc,markers,category,scope):
        source=lookup[doc] if scope=='REAL' and doc else next((s for s in synthetic if s['documentId']==doc),None)
        cases.append({'id':cid,'query':query,'scope':scope,'targetDocumentId':doc,'sourceAnswerable':bool(markers),
                      'category':category,'evidenceGroups':[refs(source,m) for m in markers],
                      'expectedDocumentIds':[doc] if markers else [],
                      'constraintGroups':list(range(len(markers))) if category in ['CONDITION','LICENSE','VERSION','VERSION_PROCEDURE','STRUCTURAL_BOUNDARY'] else []})
    for i,(q,d,m,c) in enumerate(specs,1):add(f'R{i:02}',q,d,m,c,'REAL')
    for s in synthetic:
        for i,(q,m) in enumerate(s['questions'],1):add(s['documentId']+f'-Q{i}',q,s['documentId'],m,'STRUCTURAL_BOUNDARY','SYNTHETIC')
    assert len(specs)==40 and len(cases)==52
    write(OUT/'cases.json',{'annotation':'Assistant-labelled, known sources, not independent or blind. Gold from full source, not READY chunks. Synthetic reported separately.',
                           'auditChanges':['R20 reclassified as source-answerable, even though admission withholds it.',
                             'Gold uses complete factual source lines, not arbitrary entire code blocks. R06/R08 explicitly include all requested components/platform context.',
                             'R21 remains unanswerable: README names licences but does not contain their full commercial-use terms.'], 'cases':cases})
    print(json.dumps({'status':'SOURCES_AND_GOLD_PREPARED','realCases':40,'syntheticCases':12}))

def maven(phase):
    STAGE.mkdir(parents=True,exist_ok=True)
    live=phase=='live'
    args=['docker','run','--rm','--pull','never','--network','container:'+CONTAINER if live else 'none','-e','MAVEN_OPTS=-Xmx512m',
          '-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
          'mvn','-o','-B','-pl','orange-server','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m',
          '-Dthree.arm.root=/workspace','-Dtest=ReadmeThreeArmAcceptanceTest#'+('runFrozenVectorExperiment' if live else 'exportAndCheckThreeArms')]
    if live:args+=['-Dthree.arm.live=true','-Dorange.acceptance.limit='+str(read(OUT/'requests.json')['maximumRequests']),'-Dorange.acceptance.ledger=/workspace/docs/rag-b/readme-filter-v1/retrieval-three-arm-v1/local-embedding-calls.jsonl','surefire:test']
    else:args+=['test']
    with (STAGE/(phase+'.log')).open('w',encoding='utf8') as f:r=subprocess.run(args,cwd=ROOT,stdout=f,stderr=subprocess.STDOUT)
    assert r.returncode==0,'Java checks failed: '+str(STAGE/(phase+'.log'))
    source=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.retrieval.ReadmeThreeArmAcceptanceTest.xml'
    attributes=ET.parse(source).getroot().attrib
    assert all(int(attributes[k])==0 for k in ['errors','failures','skipped']) and int(attributes['tests'])==1
    shutil.copyfile(source,STAGE/(phase+'-java-report.xml'))
    if not live:
        paths=[OUT/n for n in ['sources.json','cases.json','corpus.json','admission.json','units.json','offline-check.json']]
        paths += [ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeThreeArmAcceptanceTest.java',ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ThreeArmKnowledgeChunker.java']
        write(STAGE/'offline-receipt.json',{'tests':1,'failures':0,'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths}})

def freeze():
    history();assert not (OUT/'experiment-manifest.json').exists()
    for name,digest in read(STAGE/'offline-receipt.json')['files'].items():assert sha(ROOT/name)==digest,'Offline export must finish on current inputs: '+name
    corpus=read(OUT/'corpus.json');cases=read(OUT/'cases.json')['cases']
    texts=list(dict.fromkeys([c['text'] for c in corpus]+[c['query'] for c in cases]))
    requests=[{'batch':i//8+1,'inputs':[{'sha256':hashlib.sha256(t.encode()).hexdigest(),'chars':len(t)} for t in texts[i:i+8]]} for i in range(0,len(texts),8)]
    assert len(requests)<=50,'Frozen local requests exceed guard; do not truncate the dataset'
    write(OUT/'requests.json',{'provider':'LOCAL_OLLAMA','modelRevision':REVISION,'uniqueTexts':len(texts),'requests':requests,'maximumRequests':len(requests),'retries':0,'cloudCalls':0})
    paths=[ROOT/'scripts/readme_three_arm.py',ROOT/'scripts/test_readme_three_arm.py',
      ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeThreeArmAcceptanceTest.java',
      ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ThreeArmKnowledgeChunker.java',
      ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeChunker.java',
      ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest/RagBTokenChunker.java',
      ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/retrieval/RagBOllamaEmbeddingModel.java',
      ROOT/'orange-server/src/main/resources/rag-b/tokenizers/bge-m3.json',ROOT/'pom.xml',ROOT/'orange-server/pom.xml']
    paths += sorted(p for p in OUT.glob('*') if p.is_file())
    write(OUT/'experiment-manifest.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths},'previousSealSha256':sha(BASE/'ranking-v3/completion-seal.json')})
    print(json.dumps({'status':'THREE_ARMS_FROZEN','chunks':dict(collections.Counter(c['variant'] for c in corpus)),'uniqueEmbeddingTexts':len(texts),'localRequests':len(requests)}))

def check():
    for name,digest in read(OUT/'experiment-manifest.json')['files'].items():assert sha(ROOT/name)==digest,name
    assert sha(BASE/'ranking-v3/completion-seal.json')==read(OUT/'experiment-manifest.json')['previousSealSha256']

def run():
    history();check();assert not (OUT/'live-started.json').exists(),'Run is one-shot; do not overwrite partial attempts'
    inspect=subprocess.run(['docker','inspect',CONTAINER],capture_output=True,text=True)
    if inspect.returncode:
        subprocess.run(['docker','run','-d','--pull','never','--name',CONTAINER,'--label','orangejd.experiment=readme-three-arm-v1','--memory','256m','redis/redis-stack-server:latest'],check=True,capture_output=True)
    else:
        c=json.loads(inspect.stdout)[0]
        assert c['Config'].get('Labels',{}).get('orangejd.experiment')=='readme-three-arm-v1' and not c['HostConfig'].get('PortBindings')
        if not c['State']['Running']:subprocess.run(['docker','start',CONTAINER],check=True,capture_output=True)
    maven('live');report()

def covered(gold,spans):
    at=gold['startUtf16']
    intervals=[(max(at,s['startUtf16']),min(gold['endUtf16'],s['endUtf16'])) for s in spans if s['documentId']==gold['documentId']]
    for a,b in sorted((a,b) for a,b in intervals if b>a):
        if a>at:return False
        at=max(at,b)
        if at>=gold['endUtf16']:return True
    return at>=gold['endUtf16']

def selection(hits,corpus,k,expanded=False,budget=None):
    selected=[];spans=[];seen=set();used=0;deferred=[]
    for h in hits[:k]:
        c=corpus[h['id']];key=c['unitId'] if expanded and c['variant']=='C' else c['id']
        if key in seen:continue
        seen.add(key);tokens=c['expandedTokens'] if expanded else c['tokens']
        if budget is not None and used+tokens>budget:deferred.append(key);continue
        used+=tokens;selected.append(c)
        spans+=c['expandedSpans'] if expanded else c['sourceSpans']
    return selected,spans,used,deferred

def facts(case,spans):return [any(covered(g,spans) for g in group) for group in case['evidenceGroups']]

def span_chars(spans):
    total=sum(s['endUtf16']-s['startUtf16'] for s in spans);unique=0
    for doc in set(s['documentId'] for s in spans):
        end=-1
        for a,b in sorted((s['startUtf16'],s['endUtf16']) for s in spans if s['documentId']==doc):
            unique+=max(0,b-max(a,end));end=max(end,b)
    return total,unique

def report():
    check();history();assert not (OUT/'completion-seal.json').exists()
    corpus={c['id']:c for c in read(OUT/'corpus.json')};cases={c['id']:c for c in read(OUT/'cases.json')['cases']}
    results=read(OUT/'retrieval-results.json');assert len(results)==52
    common=[{'documentId':a['documentId'],'startUtf16':x,'endUtf16':y} for a in read(OUT/'admission.json') for x,y in a['commonApprovedIntervals']]
    details=[]
    for r in results:
        case=cases[r['id']]
        for variant,reps in r['variants'].items():
            assert len(reps)==3
            stable=all([h['id'] for h in rep['hits']]==[h['id'] for h in reps[0]['hits']] for rep in reps)
            for k in [1,3,5,10]:
                for mode,expand,budget in [('DIRECT',False,None),('DIRECT_BUDGET',False,2048),('EXPANDED_BUDGET',True,2048)]:
                    selected,spans,tokens,deferred=selection(reps[0]['hits'],corpus,k,expand,budget)
                    found=facts(case,spans);available=[s for c in corpus.values() if c['variant']==variant for s in c['expandedSpans']]
                    has=facts(case,available);admitted=facts(case,common)
                    _,unlimited,_,_=selection(reps[0]['hits'],corpus,k,expand,None)
                    before_budget=facts(case,unlimited)
                    gold=[g for group in case['evidenceGroups'] for g in group]
                    relevant=sum(any(covered(g,c['sourceSpans']) or any(s['documentId']==g['documentId'] and s['startUtf16']<g['endUtf16'] and s['endUtf16']>g['startUtf16'] for s in c['sourceSpans']) for g in gold) for c in selected)
                    complete=bool(found) and all(found)
                    issues=[]
                    for i,ok in enumerate(found):
                        if ok:continue
                        issues.append({'fact':i,'reason':'ADMISSION_GAP' if variant!='A' and not admitted[i] else 'CHUNKING_GAP' if not has[i] else 'RANKING_MISS' if not before_budget[i] else 'BUDGET'})
                    reason='COMPLETE' if complete else 'SOURCE_UNANSWERABLE' if not case['sourceAnswerable'] else '+'.join(sorted(set(x['reason'] for x in issues)))
                    total_chars,unique_chars=span_chars(spans)
                    details.append({'id':case['id'],'scope':case['scope'],'variant':variant,'K':k,'mode':mode,'answerable':case['sourceAnswerable'],
                       'projectHit':any(h['documentId'] in case['expectedDocumentIds'] for h in reps[0]['hits'][:k]),'complete':complete,'factsFound':sum(found),'factsTotal':len(found),
                       'tokens':tokens,'selectedChunks':len(selected),'irrelevantChunks':len(selected)-relevant,'sourceChars':total_chars,'duplicateSourceChars':total_chars-unique_chars,
                       'constraintsFound':sum(found[i] for i in case['constraintGroups']),'constraintsTotal':len(case['constraintGroups']),
                       'missingFacts':issues,'deferred':deferred,'reason':reason,'stable':stable})
    metrics=[]
    for scope in ['REAL','SYNTHETIC']:
        for variant in ['A','B','C']:
            for k in [1,3,5,10]:
                for mode in ['DIRECT','DIRECT_BUDGET','EXPANDED_BUDGET']:
                    rows=[d for d in details if d['scope']==scope and d['variant']==variant and d['K']==k and d['mode']==mode and d['answerable']]
                    metrics.append({'scope':scope,'variant':variant,'K':k,'mode':mode,'answerable':len(rows),'projectHit':sum(d['projectHit'] for d in rows),
                      'complete':sum(d['complete'] for d in rows),'factsFound':sum(d['factsFound'] for d in rows),'factsTotal':sum(d['factsTotal'] for d in rows),
                      'constraintsFound':sum(d['constraintsFound'] for d in rows),'constraintsTotal':sum(d['constraintsTotal'] for d in rows),
                      'averageTokens':round(sum(d['tokens'] for d in rows)/max(1,len(rows)),1),
                      'irrelevantChunkRatio':round(sum(d['irrelevantChunks'] for d in rows)/max(1,sum(d['selectedChunks'] for d in rows)),4),
                      'duplicateSourceCharRatio':round(sum(d['duplicateSourceChars'] for d in rows)/max(1,sum(d['sourceChars'] for d in rows)),4),
                      'failureReasons':dict(collections.Counter(d['reason'] for d in rows if not d['complete']))})
    write(OUT/'evaluation.json',{'metrics':metrics,'cases':details,'annotation':'Assistant-labelled tuned diagnostic; real and synthetic separated; no answering model.', 'sourceAnswerableReal':sum(c['scope']=='REAL' and c['sourceAnswerable'] for c in cases.values())})
    pairs=[]
    for scope in ['REAL','SYNTHETIC']:
        for left,right in [('A','B'),('B','C'),('A','C')]:
            a={d['id']:d for d in details if d['scope']==scope and d['variant']==left and d['K']==5 and d['mode']=='EXPANDED_BUDGET' and d['answerable']}
            b={d['id']:d for d in details if d['scope']==scope and d['variant']==right and d['K']==5 and d['mode']=='EXPANDED_BUDGET' and d['answerable']}
            pairs.append({'scope':scope,'comparison':left+'→'+right,'wins':[cid for cid in a if b[cid]['complete'] and not a[cid]['complete']],
                          'losses':[{'id':cid,'reason':b[cid]['reason'],'missingFacts':b[cid]['missingFacts']} for cid in a if a[cid]['complete'] and not b[cid]['complete']]})
    write(OUT/'paired-comparison.json',pairs)
    lines=['# README三组纯向量检索实验','', 'A＝完整原文＋B版结构切块；B＝共同批准原文＋同一结构切块；C＝相同批准原文＋知识单元切块。三组均无派生前缀、关键词或名称加分。', '',
      '真实样本是已有5仓库，构造样本6份独立报告；助手标注、已知来源，不是独立人工评测。原文有答案但未准入计入遗漏。C算法是封存生产代码的实验副本，仅改类名与前缀返回值，不修改正式链路。', '',
      '| 样本 | 组别 | K | 完整证据：直接 | 直接2048预算 | 完整单元恢复2048预算 |', '|---|---|---:|---:|---:|---:|']
    for scope in ['REAL','SYNTHETIC']:
      for variant in ['A','B','C']:
       for k in [1,3,5,10]:
        ms=[next(m for m in metrics if m['scope']==scope and m['variant']==variant and m['K']==k and m['mode']==mode) for mode in ['DIRECT','DIRECT_BUDGET','EXPANDED_BUDGET']]
        lines.append(f"| {scope} | {variant} | {k} | {ms[0]['complete']}/{ms[0]['answerable']} | {ms[1]['complete']}/{ms[1]['answerable']} | {ms[2]['complete']}/{ms[2]['answerable']} |")
    lines+=['','## Top5、2048预算的逐题胜负','','| 样本 | 对比 | 改善题 | 退步题 |','|---|---|---|---|']
    for pair in pairs:lines.append(f"| {pair['scope']} | {pair['comparison']} | {', '.join(pair['wins']) or '无'} | "+('; '.join(x['id']+': '+x['reason'] for x in pair['losses']) or '无')+' |')
    lines+=['','ADMISSION_GAP表示原文事实未进入共同准入集合；CHUNKING_GAP表示已准入但分块没有提供；RANKING_MISS表示已入库但未进入当前TopK；BUDGET表示本可召回但完整片段超预算。准入缺口还可能来自产品范围限制，不能仅凭本评分断定分类错误。','',
      '新方案若只在已准入题上改善、全来源可答题却退步，应优先检查知识供给；C相对B的退步则检查分块边界及原子超限。真实与构造样本的结果不合并，也不能用较少返回内容制造Token收益。',
      '', '## 逐题Top5诊断','','| 题目 | 来源可答 | A | B | C |','|---|---|---|---|---|']
    for c in cases.values():
        ds=[next(d for d in details if d['id']==c['id'] and d['variant']==v and d['K']==5 and d['mode']=='EXPANDED_BUDGET') for v in ['A','B','C']]
        lines.append(f"| {c['id']} {c['query']} | {c['sourceAnswerable']} | "+' | '.join(d['reason'] for d in ds)+' |')
    ledger=[json.loads(line) for line in (OUT/'local-embedding-calls.jsonl').read_text().splitlines()]
    calls=len(ledger)
    assert calls==read(OUT/'requests.json')['maximumRequests'] and all(x['kind']=='OLLAMA_EMBEDDING' for x in ledger)
    lines+=['',f'本轮本地Embedding实际请求{calls}次，云端分类／聊天请求0；每题共享查询向量，各组真实Redis查询3次。未连接业务MySQL或切换网站索引。',
      '', '预算按完整片段Token数之和计，同一C父单元只计一次；超限跳过并记录，不从TopK外补取。原子代码无法切块是C的供给缺口，保留在分母。',
      '', '本轮检验当前512/768策略的相对表现，不证明最优参数；没有生成回答、业务资格或全库可用性结论。完整数值与失败归因见evaluation.json；稳定性与向量精确排名核对见live-completion.json。',
      '', '复验：python scripts/readme_three_arm.py verify。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    history()
    paths=[p for p in OUT.rglob('*') if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT/'completion-seal.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths},'localEmbeddingRequests':calls,'cloudCalls':0,'productionWrites':0})
    verify()

def verify():
    history();check()
    for name,digest in read(OUT/'completion-seal.json')['files'].items():assert sha(ROOT/name)==digest,name
    print(json.dumps({'status':'THREE_ARM_EXPERIMENT_VERIFIED','realCases':40,'syntheticCases':12,'groups':3,'cloudCalls':0,'productionWrites':0}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['prepare','offline','freeze','run','report','verify']);a=p.parse_args()
    try:maven('offline') if a.phase=='offline' else globals()[a.phase]()
    except Exception as e:print(str(e),file=sys.stderr);sys.exit(1)
