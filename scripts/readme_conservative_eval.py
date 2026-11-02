"""Bounded regression for conservative README admission; preserves every preceding experiment."""
import argparse
import collections
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import readme_three_arm as base
import readme_recommendation_eval as previous

ROOT=base.ROOT
OLD=base.OUT
PRIOR=previous.OUT
OUT=ROOT/'docs/rag-b/readme-filter-v1/conservative-policy-v1'
STAGE=ROOT/'_experiment_staging/readme-conservative-v1'
JAVA='orange-server/src/test/java/com/orangejd/server/ragb/retrieval/ReadmeConservativeAcceptanceTest.java'
read=base.read
write=base.write
sha=base.sha

def maven(method,live=False):
    args=['docker','run','--rm','--pull','never','--network','container:'+base.CONTAINER if live else 'none',
          '-e','MAVEN_OPTS=-Xmx512m','-v',str(ROOT)+':/workspace','-v','nxz-bigorange_orangejd-maven-cache:/root/.m2',
          '-w','/workspace','maven:3.9.9-eclipse-temurin-21','mvn','-o','-B','-pl','orange-server','-am',
          '-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m','-Dconservative.root=/workspace',
          '-Dtest=ReadmeConservativeAcceptanceTest#'+method]
    if live:args+=['-Dorange.acceptance.limit='+str(read(OUT/'requests.json')['maximumRequests']),
                  '-Dorange.acceptance.ledger=/workspace/docs/rag-b/readme-filter-v1/conservative-policy-v1/local-embedding-calls.jsonl']
    args+=['test'];STAGE.mkdir(parents=True,exist_ok=True)
    log=STAGE/(method+'.log')
    with log.open('w',encoding='utf8') as f:r=subprocess.run(args,cwd=ROOT,stdout=f,stderr=subprocess.STDOUT)
    assert r.returncode==0,'Java check failed: '+str(log)
    report=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.retrieval.ReadmeConservativeAcceptanceTest.xml'
    attrs=ET.parse(report).getroot().attrib
    assert int(attrs['tests'])==1 and all(int(attrs[k])==0 for k in ['errors','failures','skipped'])
    shutil.copyfile(report,STAGE/(method+'.xml'))

def prepare():
    previous.verify();assert not (OUT/'manifest.json').exists()
    maven('exportPreparation');validate()

def validate():
    corpus=read(OUT/'corpus.json');admission=read(OUT/'admission.json')
    sources={s['documentId']:s for s in read(OLD/'sources.json')['real']}
    original=[c for c in read(OLD/'corpus.json') if c['variant']=='A' and c['scope']=='REAL']
    assert [c for c in corpus if c['variant']=='A']==original,'Baseline must remain identical'
    approved={a['documentId']:a['approvedSpans'] for a in admission}
    assert len(admission)==5 and len({c['id'] for c in corpus})==len(corpus)
    for c in corpus:
        assert c['tokens']<=768
        for s in c['sourceSpans']+c['expandedSpans']:
            text=sources[s['documentId']]['text'].encode('utf-16-le')
            assert text[s['startUtf16']*2:s['endUtf16']*2].decode('utf-16-le')==s['text']
            assert base.hashlib.sha256(s['text'].encode()).hexdigest()==s['sha256']
            if c['variant']!='A':assert base.covered(s,approved[s['documentId']]),'Unapproved evidence'
    rows=[]
    old_corpus=read(OLD/'corpus.json')
    for doc in sources:
        row={'documentId':doc,'repository':sources[doc]['sourceRef']}
        for version,items in [('old',old_corpus),('new',corpus)]:
            for v in ['A','B','C']:
                cs=[c for c in items if c['variant']==v and c['documentId']==doc]
                row[version+v]={'chunks':len(cs),'under20Tokens':sum(c['tokens']<20 for c in cs),'meanTokens':round(sum(c['tokens'] for c in cs)/max(1,len(cs)),1)}
        rows.append(row)
    write(OUT/'representation-audit.json',rows)
    # Annotation denominator is unchanged, including facts still awaiting semantic review.
    coverage=[]
    old_admission=read(OLD/'admission.json')
    oldspans=[{'documentId':a['documentId'],'startUtf16':x,'endUtf16':y} for a in old_admission for x,y in a['commonApprovedIntervals']]
    newspans=[s for a in admission for s in a['approvedSpans']]
    for c in read(PRIOR/'cases.json')['cases']:
        coverage.append({'id':c['id'],'category':c['category'],'candidates':[
            {'documentId':p['documentId'],'old':previous.candidate_score(p,oldspans),'new':previous.candidate_score(p,newspans)} for p in c['candidates']]})
    write(OUT/'admission-coverage.json',coverage)
    return corpus

def freeze():
    previous.verify();assert not (OUT/'manifest.json').exists();corpus=validate()
    cache={r['textSha256'] for file in [OLD/'vectors.json',PRIOR/'query-vectors.json'] for r in read(file)}
    texts=list(dict.fromkeys(c['text'] for c in corpus))
    missing=[t for t in texts if base.hashlib.sha256(t.encode()).hexdigest() not in cache]
    calls=(len(missing)+7)//8;assert 0<calls<=50
    write(OUT/'requests.json',{'newDocumentTexts':missing,'maximumRequests':calls,'batchSize':8,'queryRequests':0,
         'classificationRequests':0,'chatRequests':0,'cloudRequests':0,'retries':0,'revision':base.REVISION})
    paths=[ROOT/'scripts/readme_conservative_eval.py',ROOT/JAVA,
        ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeConservativePolicyTest.java',
        *[ROOT/('orange-server/src/main/java/com/orangejd/server/ragb/ingest/'+name+'.java') for name in ['ReadmeConservativePolicy','ReadmeApprovedStructuralChunker','ReadmeKnowledgePreparation']],
        OUT/'plan.md',OUT/'corpus.json',OUT/'admission.json',OUT/'requests.json',OUT/'representation-audit.json',OUT/'admission-coverage.json',
        OLD/'vectors.json',PRIOR/'query-vectors.json',PRIOR/'cases.json',PRIOR/'completion-seal.json',
        *sorted((OUT/'previews').glob('*.json'))]
    write(OUT/'manifest.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths}})
    print(json.dumps({'status':'FROZEN','newLocalDocumentRequests':calls,'newTexts':len(missing),'queryRequests':0,'cloudCalls':0,
          'chunks':dict(collections.Counter(c['variant'] for c in corpus))}))

def check():
    for p,d in read(OUT/'manifest.json')['files'].items():assert sha(ROOT/p)==d,p

def run():
    previous.verify();check();assert not (OUT/'live-started.json').exists()
    maven('runFrozenRetrieval',True);evaluate()

def evaluate():
    check();previous.verify();assert not (OUT/'completion-seal.json').exists()
    corpus={c['id']:c for c in read(OUT/'corpus.json')};cases=read(PRIOR/'cases.json')['cases']
    results={r['id']:r for r in read(OUT/'retrieval-results.json')};assert len(results)==26
    old_results={r['id']:r for r in read(PRIOR/'retrieval-results.json')}
    oldcorpus={c['id']:c for c in read(OLD/'corpus.json')}
    oldadmitted=[{'documentId':a['documentId'],'startUtf16':x,'endUtf16':y} for a in read(OLD/'admission.json') for x,y in a['commonApprovedIntervals']]
    newadmitted=[s for a in read(OUT/'admission.json') for s in a['approvedSpans']]
    details=[]
    for case in cases:
        r=results[case['id']]
        assert [h['id'] for h in r['variants']['A'][0]['hits']]==[h['id'] for h in old_results[case['id']]['variants']['A'][0]['hits']], 'Baseline ranking drift'
        for label,variant,docs,result,admitted in [('A','A',corpus,r,newadmitted),('B_old','B',oldcorpus,old_results[case['id']],oldadmitted),('C_old','C',oldcorpus,old_results[case['id']],oldadmitted),('B_v5','B',corpus,r,newadmitted),('C_v5','C',corpus,r,newadmitted)]:
            reps=result['variants'][variant];assert len(reps)==3
            assert all([h['id'] for h in x['hits']]==[h['id'] for h in reps[0]['hits']] for x in reps)
            for k in [1,3,5,10]:
              for mode,expand,budget in [('DIRECT',False,None),('EXPANDED_2048',True,2048)]:
                selected,spans,tokens,deferred=base.selection(reps[0]['hits'],docs,k,expand,budget)
                allspans=[s for c in docs.values() if c['variant']==variant for s in c['expandedSpans']]
                ps=[]
                for p in case['candidates']:
                    score=previous.candidate_score(p,spans);available=previous.candidate_score(p,allspans);admission=previous.candidate_score(p,admitted)
                    reason='NONE' if score['allEvidenceComplete'] else 'ADMISSION_GAP' if variant!='A' and not admission['allEvidenceComplete'] else 'SUPPLY_GAP' if not available['allEvidenceComplete'] else 'RANKING_OR_BUDGET'
                    ps.append({'documentId':p['documentId'],**score,'availability':available,'admission':admission,'missingReason':reason})
                supported=[p['documentId'] for p in case['candidates'] if p['expectedVerdict']=='SUPPORTED']
                details.append({'id':case['id'],'label':label,'category':case['category'],'K':k,'mode':mode,'tokens':tokens,'budgetSkipped':deferred,
                    'proofComplete':case['category']!='OBSERVATION' and all(p['proofComplete'] for p in ps),
                    'allEvidenceComplete':case['category']!='OBSERVATION' and all(p['allEvidenceComplete'] for p in ps),
                    'candidateHit':bool(supported) and any(h['documentId'] in supported for h in reps[0]['hits'][:k]),'candidates':ps,'agentDecisionEvaluated':False})
    metrics=[]
    for label in ['A','B_old','C_old','B_v5','C_v5']:
      for k in [1,3,5,10]:
       for mode in ['DIRECT','EXPANDED_2048']:
        for cat in ['MAIN','DISCOVER','VERIFY','COMPARE']:
            rows=[d for d in details if d['label']==label and d['K']==k and d['mode']==mode and (d['category']!='OBSERVATION' if cat=='MAIN' else d['category']==cat)]
            metrics.append({'label':label,'K':k,'mode':mode,'category':cat,'cases':len(rows),'proofComplete':sum(d['proofComplete'] for d in rows),'allEvidenceComplete':sum(d['allEvidenceComplete'] for d in rows),
                'candidateHit':sum(d['candidateHit'] for d in rows),'meanTokens':round(sum(d['tokens'] for d in rows)/max(1,len(rows)),1)})
    pairs=[];top=[d for d in details if d['category']!='OBSERVATION' and d['K']==5 and d['mode']=='EXPANDED_2048']
    for a,b in [('B_old','B_v5'),('C_old','C_v5'),('A','C_v5')]:
        aa={d['id']:d for d in top if d['label']==a};bb={d['id']:d for d in top if d['label']==b}
        pairs.append({'from':a,'to':b,'wins':[x for x in aa if bb[x]['allEvidenceComplete'] and not aa[x]['allEvidenceComplete']],
            'losses':[x for x in aa if aa[x]['allEvidenceComplete'] and not bb[x]['allEvidenceComplete']]})
    write(OUT/'evaluation.json',{'metrics':metrics,'cases':details,'paired':pairs})
    lines=['# 保守准入 V5：修改与推荐检索回归','','5份原有README，20道主检索题及6道不足观察题。复用全部查询向量与已保存分类，不调用云端模型。A的正文、向量和排名均与原实验完全一致。','',
        '| 方案 | 找到候选 /8 | 找候选证据完整 /8 | 核验证据完整 /8 | 比较证据完整 /4 | 主集事实完整 /20 | 加必要限制 /20 |','|---|---:|---:|---:|---:|---:|---:|']
    for label in ['A','B_old','C_old','B_v5','C_v5']:
        ms={m['category']:m for m in metrics if m['label']==label and m['K']==5 and m['mode']=='EXPANDED_2048'}
        lines.append(f"| {label} | {ms['DISCOVER']['candidateHit']}/8 | {ms['DISCOVER']['proofComplete']}/8 | {ms['VERIFY']['proofComplete']}/8 | {ms['COMPARE']['proofComplete']}/4 | {ms['MAIN']['proofComplete']}/20 | {ms['MAIN']['allEvidenceComplete']}/20 |")
    lines+=['','以上统一为Top5、2048个BGE Token预算。仅测证据找齐；未执行最终推荐判断、拒答、追问和许可资格判断。','',
      '## 实现调整','','- 准入V5按具体事实和依赖重新检查；原始REVIEW块不自动放行。','- 许可状态单列：待核实的许可名称与链接不阻断独立功能/安装事实；明确的运行、非生产、版别限制继续绑定。许可资格本身没有因此获得批准。',
      '- 明确的本机安装声明可作为原文事实；只有链接的导航仍隔离。关联产品声明不能变成核心产品能力。','- 结构分块保留原文标题上下文，停止把纯标题当独立向量。引用以多个原文区间保存，派生分隔符没有原文位置。',
      '- B_v5与C_v5共享修改后的批准范围。B_v5同时修改了短块表示，因此旧B到新B的差值包含准入与分块两个因素，不宣称单因素因果。','',
      '## 逐题结果','','| 题号 | A | 旧B | 旧C | V5 B | V5 C |','|---|---|---|---|---|---|']
    for c in cases:
        labels=[]
        for label in ['A','B_old','C_old','B_v5','C_v5']:
            d=next(d for d in details if d['id']==c['id'] and d['label']==label and d['K']==5 and d['mode']=='EXPANDED_2048')
            labels.append('仅线索，未测决策' if c['category']=='OBSERVATION' else '完整' if d['allEvidenceComplete'] else '; '.join(p['documentId']+':'+p['missingReason'] for p in d['candidates'] if not p['allEvidenceComplete']))
        lines.append('| '+c['id']+' | '+' | '.join(labels)+' |')
    lines+=['','## 成对变化','']
    for p in pairs:lines.append(p['from']+' → '+p['to']+'：改善 '+(', '.join(p['wins']) or '无')+'；退步 '+(', '.join(p['losses']) or '无')+'。')
    lines+=['','## 适用边界','','仍是5个已研究仓库上的助手标注回归，不能证明全库效果或策略最优；同一事实可能被多道题引用。风险限制的排名问题没有通过定向查询或重排来掩盖，原本待审的n8n功能列表、Flowise定位等仍保留为遗漏。',
        '', '本轮不切换网站索引。当前入口ReadmeKnowledgePreparation负责准备预览，实际发布、最终许可核实及风险补查仍属后续工作。',
        '', '复验：python scripts/readme_conservative_eval.py verify。请求清单、独立账本、原文映射、待审项、逐题指标均保存在本目录。']
    (OUT/'report.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    calls=len((OUT/'local-embedding-calls.jsonl').read_text(encoding='utf8').splitlines());assert calls==read(OUT/'requests.json')['maximumRequests']
    check();previous.verify()
    paths=[p for p in OUT.rglob('*') if p.is_file() and p.name!='completion-seal.json']+[p for p in STAGE.rglob('*') if p.is_file()]
    write(OUT/'completion-seal.json',{'files':{p.relative_to(ROOT).as_posix():sha(p) for p in paths},'localRequests':calls,'cloudCalls':0,'productionWrites':0})
    verify()

def verify():
    previous.verify();check()
    for name,digest in read(OUT/'completion-seal.json')['files'].items():assert sha(ROOT/name)==digest,name
    print(json.dumps({'status':'CONSERVATIVE_V5_VERIFIED',**{k:v for k,v in read(OUT/'completion-seal.json').items() if k!='files'}}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['prepare','freeze','run','evaluate','verify']);a=p.parse_args()
    try:globals()[a.phase]()
    except Exception as ex:print(str(ex),file=sys.stderr);sys.exit(1)
