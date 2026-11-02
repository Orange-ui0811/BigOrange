"""Native B versus Gitee reranking of the same frozen twenty-candidate pool; no website switch."""
import argparse
import collections
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/reranker-v1'
STAGE=ROOT/'_experiment_staging/rag-b-reranker-v1'
DATA=ROOT/'docs/rag-b/model-chunk-experiment/hybrid-translation/dataset.json'
RULES=ROOT/'orange-server/src/main/resources/rag-b/evaluation-benchmark-v1.json'
JAVA=ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/service/RagBRerankerSnapshotTest.java'
MODEL='bge-reranker-v2-m3'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def digest(t):return hashlib.sha256(t.encode()).hexdigest()
def seal(paths):return {p.relative_to(ROOT).as_posix():sha(p) for p in paths}
def check(path):
    for p,h in read(path)['files'].items():assert sha(ROOT/p)==h,p
def norm(t):return re.sub(r'[^\w]+','',t.lower())
def ref(h):return (h.get('sourceRef') or '').split('|',1)[0]
def source_key(h):return h['sourceType']+':'+ref(h)
def diversity(question):
    q=question.lower()
    return any(x in q for x in ['推荐','找几个','项目清单','recommend','suggest projects']) or bool(re.fullmatch(r'.*列出.{0,12}项目.*',q))
def select(pool,question,scores=None,k=8):
    # Candidate list is already source-state/constraint filtered and deduplicated by native B.
    ranked=sorted(pool,key=lambda h:-(h['score'] if scores is None else scores[str(h['chunkId'])]))
    if not diversity(question):return ranked[:k]
    selected=[];seen=set()
    for h in ranked:
        if source_key(h) not in seen:selected.append(h);seen.add(source_key(h))
        if len(selected)==k:return selected
    ids={str(h['chunkId']) for h in selected}
    return (selected+[h for h in ranked if str(h['chunkId']) not in ids])[:k]
def gate(question,hits):
    q=question.lower();price=['价格','费用','收费','多少钱','price','pricing','cost'];lic=['商业','授权','许可证','许可','commercial','license','licensing']
    prices=['价格','费用','收费','免费','付费','人民币','美元','元/月','元/年','$','¥','price','pricing','cost','free','paid']
    if any(x in q for x in price) and any(x in q for x in lic):
        if not any(any(x in (h['title']+'\n'+h['content']).lower() for x in prices) and any(x in (h['title']+'\n'+h['content']).lower() for x in lic) for h in hits):return []
    return hits
def policy(hits):
    if not hits:return 'REFUSE_NO_MATCH'
    return 'ANSWER_WITH_CITATIONS' if len({(h['documentId'],h.get('sectionPath')) for h in hits})>=2 or sum(len(h['content'].encode('utf-16-le'))//2 for h in hits)>=500 else 'REFUSE_INSUFFICIENT_EVIDENCE'
def document(h):return 'Title: '+(h.get('title') or '')+'\nSource: '+(h.get('sourceRef') or '')+'\nSection: '+(h.get('sectionPath') or '')+'\n\n'+h['content']
def parse_scores(data,count):
    rows=data.get('results',data.get('data',[]));assert isinstance(rows,list) and len(rows)==count,'Incomplete rerank response'
    scores={}
    for r in rows:
        i=r.get('index');score=r.get('relevance_score',r.get('score'))
        assert type(i) is int and 0<=i<count and i not in scores,'Invalid rerank index'
        assert isinstance(score,(int,float)) and not isinstance(score,bool) and math.isfinite(score),'Invalid rerank score'
        scores[i]=float(score)
    return scores

def prepare():
    assert not (OUT/'seed-manifest.json').exists();allcases=read(DATA)['cases'];cases=[]
    groups=[('business_zh',[0,2,4,6]),('readme_cross_language',[0,1,2,4]),('fresh_business_zh',[8,12,16,19]),('readme_english',[3,5,7,9])]
    for cat,indices in groups:
        values=[c for c in allcases if c['category']==cat]
        for i in indices:cases.append({**values[i],'stratum':'SEMANTIC','expectedPolicy':None})
    for key in ['beginner-glyph','career-neko','github-langgraph','github-nanobot']:
        c=next(c for c in allcases if c['key']==key);cases.append({**c,'stratum':'NAMED','expectedPolicy':None})
    for key in ['vue-no-docker','refuse-java-beginner','refuse-ruby-career','refuse-commercial-price']:
        c=next(c for c in read(RULES)['cases'] if c['key']==key)
        cases.append({'key':key,'question':c['question'],'refs':c.get('expectedSourceRefs',[]),'anchors':[],'category':'BUSINESS_GUARD','stratum':'GUARD','rule':c,'expectedPolicy':c.get('expectedAnswerPolicy','ANSWER_WITH_CITATIONS')})
    assert len(cases)==24
    write(OUT/'cases.json',{'cases':cases,'annotation':'Reused assistant-labelled diagnostics, not independent/blind; anchors are literal clues, not full fact coverage.'})
    files=[DATA,RULES,JAVA,Path(__file__),OUT/'cases.json',OUT/'plan.md',ROOT/'scripts/test_rag_b_reranker_eval.py',
           ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/service/RagBSearchService.java',ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/service/RagBRelevanceGuard.java']
    write(OUT/'seed-manifest.json',{'files':seal(files),'newLocalQueryLimit':48,'cloudRerankLimit':24,'chatCalls':0,'documentEmbeddingCalls':0})
    print(json.dumps({'status':'QUESTIONS_FROZEN','cases':24,'semantic':16,'named':4,'businessGuards':4,'cloudCallsSoFar':0}))

def capture():
    check(OUT/'seed-manifest.json');assert not (OUT/'capture-started.json').exists()
    # Compose resolves values in memory; credentials never go into command arguments, files or logs.
    value=subprocess.run(['docker','compose','--env-file','.env.local','-f','compose.local.yml','config','--format','json'],cwd=ROOT,capture_output=True,text=True,encoding='utf8',check=True)
    env={**os.environ,**json.loads(value.stdout)['services']['backend']['environment']}
    env.update({'RUN_RERANK_SNAPSHOT':'true','CUSTOMER_ENABLED':'false','RAG_B_GITHUB_SYNC_ENABLED':'false','INGESTION_PIPELINE_V2_ENABLED':'false','AI_TOOL_POOL_ENABLED':'false','SPRING_PROFILES_ACTIVE':'dev','MAVEN_OPTS':'-Xmx768m'})
    args=['docker','run','--rm','--pull','never','--network','nxz-bigorange_default']
    for name in json.loads(value.stdout)['services']['backend']['environment']:args+=['-e',name]
    for name in ['RUN_RERANK_SNAPSHOT','SPRING_PROFILES_ACTIVE','MAVEN_OPTS']:args+=['-e',name]
    args+=['-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21','mvn','-o','-B','-pl','orange-server','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx768m','-Drerank.root=/workspace','-Dorange.acceptance.limit=48','-Dorange.acceptance.ledger=/workspace/docs/rag-b/reranker-v1/local-embedding-calls.jsonl','-Dtest=RagBRerankerSnapshotTest','test']
    STAGE.mkdir(parents=True,exist_ok=True)
    with (STAGE/'capture.log').open('w',encoding='utf8') as f:r=subprocess.run(args,cwd=ROOT,env=env,stdout=f,stderr=subprocess.STDOUT)
    assert r.returncode==0,'Snapshot capture failed; inspect '+str(STAGE/'capture.log')
    p=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.service.RagBRerankerSnapshotTest.xml';a=ET.parse(p).getroot().attrib
    assert int(a['tests'])==1 and all(int(a[x])==0 for x in ['failures','errors','skipped']);shutil.copyfile(p,STAGE/'capture.xml')
    freeze()

def freeze():
    check(OUT/'seed-manifest.json');assert not (OUT/'request-manifest.json').exists()
    cases={c['key']:c for c in read(OUT/'cases.json')['cases']};rows=read(OUT/'candidate-snapshot.json');assert len(rows)==24
    requests=[]
    for row in rows:
        c=cases[row['key']];pool=row['pool']['hits'];base=gate(c['question'],select(pool,c['question']))
        assert [h['chunkId'] for h in base]==[h['chunkId'] for h in row['baseline']['hits']],row['key']+' baseline replay mismatch'
        assert policy(base)==row['baseline']['answerPolicy'],row['key']+' policy mismatch'
        assert len({h['chunkId'] for h in pool})==len(pool) and len(pool)<=20
        if pool:requests.append({'key':row['key'],'model':MODEL,'query':c['question'],'documents':[document(h) for h in pool],'top_n':len(pool),'return_documents':False})
    write(OUT/'requests.json',{'requests':requests,'maximumRequests':len(requests),'retries':0,'model':MODEL,'endpoint':'https://ai.gitee.com/v1/rerank','maxPool':20,'mode':'Post-native-top20 additional rerank; not pre-diversity full80 rerank.'})
    write(OUT/'request-manifest.json',{'files':seal([OUT/'seed-manifest.json',OUT/'cases.json',OUT/'candidate-snapshot.json',OUT/'capture-completion.json',OUT/'requests.json'])})
    print(json.dumps({'status':'CANDIDATES_AND_REQUESTS_FROZEN','cases':24,'cloudRerankRequests':len(requests),'sameCandidatePool':True,'nativeBaselineReplay':True}))

def run():
    check(OUT/'seed-manifest.json');check(OUT/'request-manifest.json');assert not (OUT/'rerank-started.json').exists()
    values={}
    for line in (ROOT/'.env.local').read_text(encoding='utf-8-sig').splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            k,v=line.split('=',1);values[k.strip()]=v.strip().strip('"').strip("'")
    assert (values.get('AI_EMBEDDING_ENDPOINT') or 'https://ai.gitee.com/v1').rstrip('/')=='https://ai.gitee.com/v1'
    key=values.get('AI_EMBEDDING_KEY');assert key,'Configured embedding credential missing'
    request_list=read(OUT/'requests.json');maximum=request_list['maximumRequests'];assert maximum<=24
    write(OUT/'rerank-started.json',{'model':MODEL,'maximumRequests':maximum,'retries':0,'provider':'GITEE','credentialSource':'AI_EMBEDDING_KEY'})
    results=[];ledger=OUT/'rerank-calls.jsonl';assert not ledger.exists()
    for number,item in enumerate(request_list['requests'],1):
        body={k:v for k,v in item.items() if k!='key'}
        with ledger.open('a',encoding='utf8') as f:f.write(json.dumps({'attempt':number,'key':item['key'],'documents':len(item['documents']),'requestSha256':digest(json.dumps(body,ensure_ascii=False,sort_keys=True)),'at':time.time()})+'\n')
        req=urllib.request.Request('https://ai.gitee.com/v1/rerank',data=json.dumps(body,ensure_ascii=False).encode(),headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'})
        started=time.perf_counter()
        try:
            with urllib.request.urlopen(req,timeout=60) as response:reply=json.loads(response.read().decode())
            scores=parse_scores(reply,len(item['documents']))
        except Exception as ex:
            write(OUT/'failure.json',{'key':item['key'],'attempt':number,'type':type(ex).__name__,'httpStatus':getattr(ex,'code',None),'retryPerformed':False});raise RuntimeError('Rerank failed; counted attempt preserved, no retry') from None
        results.append({'key':item['key'],'scores':[scores[i] for i in range(len(item['documents']))],'rerankMs':(time.perf_counter()-started)*1000,'usage':reply.get('usage'),'model':reply.get('model',MODEL)})
        write(OUT/'rerank-results.json',results);print(json.dumps({'reranked':number,'of':maximum,'key':item['key']}),flush=True)
    report()

def rank(case,hits,evidence=False):
    for i,h in enumerate(hits,1):
        if ref(h) in case['refs'] and (not evidence or not case['anchors'] or any(norm(a) in norm(h['content']) for a in case['anchors'])):return i
    return 0
def percentile(values,p):
    return sorted(values)[max(0,math.ceil(len(values)*p)-1)] if values else 0
def report():
    check(OUT/'seed-manifest.json');check(OUT/'request-manifest.json');assert not (OUT/'completion-seal.json').exists()
    cases={c['key']:c for c in read(OUT/'cases.json')['cases']};scores={r['key']:r for r in read(OUT/'rerank-results.json')};details=[]
    for row in read(OUT/'candidate-snapshot.json'):
        c=cases[row['key']];pool=row['pool']['hits'];rr=scores.get(row['key']);mapping={str(h['chunkId']):s for h,s in zip(pool,rr['scores'])} if rr else None
        after=gate(c['question'],select(pool,c['question'],mapping)) if pool else []
        current={'key':row['key'],'question':c['question'],'stratum':c['stratum'],'category':c['category'],'candidateCount':len(pool),'sourceAvailableIn20':rank(c,pool)>0,'evidenceAvailableIn20':rank(c,pool,True)>0,'rerankMs':rr['rerankMs'] if rr else None,'bCandidateMs':row['poolMs'],'expectedPolicy':c['expectedPolicy']}
        for side,hits in [('B',row['baseline']['hits']),('B_RERANK',after)]:
            current[side]={'sourceRank':rank(c,hits),'evidenceRank':rank(c,hits,True),'answerPolicy':policy(hits),'chunkIds':[h['chunkId'] for h in hits],'sources':[ref(h) for h in hits]}
        details.append(current)
    summary={}
    for group in ['SEMANTIC','NAMED']:
        rows=[r for r in details if r['stratum']==group];g={'cases':len(rows),'sourceCandidateCeiling20':sum(r['sourceAvailableIn20'] for r in rows),'evidenceCandidateCeiling20':sum(r['evidenceAvailableIn20'] for r in rows)}
        for side in ['B','B_RERANK']:
            g[side]={}
            for target in ['source','evidence']:
                ranks=[r[side][target+'Rank'] for r in rows]
                g[side][target]={**{'hitAt'+str(k):sum(0<x<=k for x in ranks) for k in [1,3,5,8]},'mrr8':sum(1/x for x in ranks if x)/len(rows)}
        summary[group]=g
    guards=[r for r in details if r['stratum']=='GUARD'];summary['GUARD']={'cases':len(guards),**{s:sum(r[s]['answerPolicy']==r['expectedPolicy'] for r in guards) for s in ['B','B_RERANK']}}
    ms=[r['rerankMs'] for r in details if r['rerankMs'] is not None];summary['latency']={'additionalRerankP50Ms':percentile(ms,.5),'additionalRerankP95Ms':percentile(ms,.95),'samples':len(ms),'basis':'One sequential real rerank per nonempty query; includes network; not a sustained-load or end-to-end website benchmark.'}
    pairs={}
    for target in ['sourceRank','evidenceRank']:
        def rr(n):return 1/n if n else 0
        rows=[r for r in details if r['stratum']!='GUARD']
        pairs[target]={'wins':[r['key'] for r in rows if rr(r['B_RERANK'][target])>rr(r['B'][target])],'losses':[r['key'] for r in rows if rr(r['B_RERANK'][target])<rr(r['B'][target])]}
    write(OUT/'evaluation.json',{'summary':summary,'paired':pairs,'cases':details})
    lines=['# B版追加模型 Reranker：24题有界实验','','本轮使用当前活动B版索引与原生混合检索。16道语义题、4道点名题、4道业务规则题；使用已保存的助手标注诊断题，不是盲测。','',
      '每题先实际取得原生Top8和Top20，冻结Top20为两组共同候选；程序复现无重排的筛选结果，并逐题与真实Top8核对。Reranker仅重排同一候选，保留原有项目多样性和价格证据检查。','',
      '| 题组与指标 | B无模型重排 | B加模型重排 |','|---|---:|---:|']
    for group,label in [('SEMANTIC','语义题'),('NAMED','点名题')]:
        g=summary[group]
        for target,title in [('source','目标项目'),('evidence','目标关键原文')]:
            for k in [1,3,5,8]:lines.append(f"| {label}：{title}命中@{k} /{g['cases']} | {g['B'][target]['hitAt'+str(k)]} | {g['B_RERANK'][target]['hitAt'+str(k)]} |")
            lines.append(f"| {label}：{title}MRR@8 | {g['B'][target]['mrr8']:.3f} | {g['B_RERANK'][target]['mrr8']:.3f} |")
    lines+=['',f"4道业务规则题策略匹配：B {summary['GUARD']['B']}/4，B+Reranker {summary['GUARD']['B_RERANK']}/4。只评检索策略，不等于最终客服拒答正确率。",'',
      f"新增重排耗时（含网络）：P50 {summary['latency']['additionalRerankP50Ms']:.0f} ms，P95 {summary['latency']['additionalRerankP95Ms']:.0f} ms。单次顺序采样，未做持续压测，不将它与历史不同运行的P95相加。",'',
      '## 候选上限与逐题变化','','语义题Top20中有目标项目 '+str(summary['SEMANTIC']['sourceCandidateCeiling20'])+'/16，有标注关键原文 '+str(summary['SEMANTIC']['evidenceCandidateCeiling20'])+'/16。候选中缺失的知识不能靠本次重排补回。','',
      '| 题号 | B目标原文排名 | 重排后原文排名 | Top20有目标原文 |','|---|---:|---:|---|']
    for r in details:lines.append(f"| {r['key']} | {r['B']['evidenceRank'] or '未命中'} | {r['B_RERANK']['evidenceRank'] or '未命中'} | {r['evidenceAvailableIn20']} |")
    lines+=['','## 结论范围','','- 这是在原生Top20之后追加重排的小实验，候选已做过B版去重和项目多样性选择；不是在完整80条融合候选上接入模型的最终效果。',
      '- 模型输入为标题、来源、章节和原文正文，不发送答案标签；原RRF分数与模型分数分开保存。没有使用模型分数替换向量阈值或证明硬条件满足。',
      '- 原文命中沿用已有字面锚点，不代表所有必要事实和限制齐全。其他符合需求的项目可能未被标注，因此不报告通用Precision或完整相关性nDCG。',
      '- 分块、Embedding、索引、业务条件和生成逻辑均未修改；未调用聊天模型、未重新入库、未切换网站。',
      '- 保存所有改善和退步，查看evaluation.json的paired与逐题明细；下一轮是否接入完整候选阶段，应依据收益、回退及耗时决定。',
      '', '复验：python scripts/rag_b_reranker_eval.py verify。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    count=len((OUT/'rerank-calls.jsonl').read_text(encoding='utf8').splitlines());assert count==read(OUT/'requests.json')['maximumRequests']
    paths=[p for p in OUT.rglob('*') if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT/'completion-seal.json',{'files':seal(paths),'rerankRequests':count,'chatCalls':0,'documentEmbeddingCalls':0,'websiteSwitch':False});verify()

def verify():
    check(OUT/'seed-manifest.json');check(OUT/'request-manifest.json');check(OUT/'completion-seal.json')
    print(json.dumps({'status':'B_RERANK_COMPARISON_VERIFIED',**{k:v for k,v in read(OUT/'completion-seal.json').items() if k!='files'}}))
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['prepare','capture','freeze','run','report','verify']);a=p.parse_args()
    try:globals()[a.phase]()
    except Exception as ex:print(str(ex),file=sys.stderr);sys.exit(1)
