"""Verify K3 evidence locally. No provider, database, or index access."""
import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'_experiment_staging/readme-filter-k3'
OUT=ROOT/'docs/rag-b/readme-filter-v1/k3'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition,message):
    if not condition: raise ValueError(message)


def main():
    tests=read(OUT/'offline-tests.json')
    require(sum(x['tests'] for x in tests)==42,'Unexpected offline test inventory')
    require(sum(x['skipped'] for x in tests)==1,'Expected only paid smoke to be skipped offline')
    require(all(x['failures']==x['errors']==0 for x in tests),'Offline test failure')
    second=ET.parse(ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.ReadmeModelReviewCorpusTest.xml').getroot()
    require(int(second.get('failures'))==0 and int(second.get('errors'))==0,'Cache replay suite failed')
    replay_case=[n for n in second.findall('testcase') if n.get('name')=='replayRealValidatedCacheWithNoProviderAccess']
    require(len(replay_case)==1 and replay_case[0].find('skipped') is None,'Cache replay was not executed')
    require('BUILD SUCCESS' in (STAGE/'cache-replay-second-jvm.log').read_text(encoding='utf-8'),'Missing successful second JVM evidence')
    receipt=read(STAGE/'live-budget-receipt.json')
    current=ROOT/'docs/customer/context-v3/live-provider-calls.jsonl'
    prefix=STAGE/'legacy40-before-k3.jsonl'
    original=ROOT/'docs/customer/workflow-v2/provider-calls.jsonl'
    require(receipt['budgetLimit']==40 and receipt['newAttempts']==2,'Wrong allowance')
    require(receipt['before']['entries']==38 and receipt['after']['entries']==40,'Wrong counts')
    require(sha(current)==receipt['after']['sha256'],'Live ledger changed after smoke')
    require(sha(prefix)==receipt['before']['sha256'] and current.read_bytes().startswith(prefix.read_bytes()),'Historical prefix changed')
    require(sha(original)=='7d9f44cb5a1fb7e210dec11add09aaf2e360f02c0f64b1f765f3a09d48301f97','Original 50-entry ledger changed')
    require(receipt['original50Unchanged'] and receipt['prior38PrefixPreserved'],'Bad receipt')
    replay=read(STAGE/'cache-replay.json')
    require(replay['cachedSelections']==4 and replay['providerCalls']==0 and replay['journalUnchanged'],'Replay did not use cache only')
    journal=[json.loads(row) for row in (STAGE/'provider-calls.jsonl').read_text(encoding='utf-8').splitlines() if row.strip()]
    require(sum(x['event']=='START' for x in journal)==2 and all(not x['simulated'] for x in journal),'Invalid real attempt journal')
    results=read(STAGE/'live-results.json')
    snapshot=read(ROOT/'_experiment_staging/readme-filter-v1-20261029/snapshot.json')
    sources={str(d['documentId']):d for d in snapshot['documents']}
    base={p['documentId']:p for p in read(ROOT/'_experiment_staging/readme-filter-k2/all-preview.json')['previews']}
    require(len(results)==2,'Wrong smoke sample count')
    statuses=Counter();decisions=Counter();adopted=Counter();all_selections=[]
    report=['# K3 两次真实验证与原文选择','','仅作接口、选择校验和失败回落验证；不是全量分类或独立质量评估。',
            '首次无效响应未保留具体字段，不能断言具体错误来源；之后已补诊断码，未追加真实重试。','']
    checked=0
    for result in results:
        p=result['preview'];source=sources[p['documentId']];raw16=source['content'].encode('utf-16-le')
        require(p['sourceRef']==source['sourceRef'] and p['versionId']==str(source['versionId']) and p['sourceSha256']==source['sha256'],'Cross source result')
        mapping={b['blockId']:b for b in p['blocks']};prior={b['blockId']:b for b in base[p['documentId']]['blocks']}
        at=0
        for b in p['blocks']:
            require(b['startUtf16']==at and raw16[at*2:b['endUtf16']*2].decode('utf-16-le')==b['text'],'Wrong UTF-16 mapping')
            at=b['endUtf16'];checked+=1
            require(b['text']==prior[b['blockId']]['text'],'Original rewritten')
            if b['reason'].startswith('MODEL_'):
                require(prior[b['blockId']]['decision']=='REVIEW','Overrode fixed rule')
                adopted[b['decision']]+=1
        require(at*2==len(raw16),'Incomplete original coverage')
        for packet in p['knowledgeCandidates']+p['pointerCandidates']:
            require(packet['documentId']==p['documentId'] and packet['versionId']==p['versionId'] and packet['sourceRef']==p['sourceRef'],'Cross source packet')
            require(all(mapping[i]['decision']=='KEEP' for i in packet['primaryBlockIds']+packet['contextBlockIds']),'Review or noise leaked to candidate packet')
        statuses.update(a['status'] for a in result['attempts']);decisions.update(s['decision'] for s in result['selections'])
        report += [f"## {p['sourceRef']}",'',f"请求状态：{dict(Counter(a['status'] for a in result['attempts']))}。",'']
        if not result['selections']:
            report += ['没有采用模型选择，复核原文保留。','']
        for selected in result['selections']:
            b=mapping[selected['blockId']];all_selections.append(selected)
            report += [f"### L{b['startLine']}–L{b['endLine']} · {selected['decision']}",'',
                       f"类型提示 {selected['kind']}；范围提示 {selected['scope']}；原因 {selected['reason']}。",
                       f"合并后决定 {b['decision']}；原文引用 UTF-16 [{b['startUtf16']}, {b['endUtf16']})。",'',
                       '~~~~~~~~~~~~text',b['text'].rstrip(),'~~~~~~~~~~~~','']
    require(statuses['MODEL_VALIDATED']==1 and decisions=={'KEEP':3,'DROP':1},'Unexpected recorded smoke outcome')
    usage=Counter()
    for event in journal:
        for key,value in event.get('usage',{}).items():usage[key]+=value
    plan=read(STAGE/'request-plan.json')
    paths=[p for p in (ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest').glob('Readme*.java')]
    paths += [ROOT/'scripts/run_readme_model_review.ps1',ROOT/'scripts/run_readme_model_review_live.py',ROOT/'scripts/verify_readme_model_review.py']
    summary={'status':'K3_IMPLEMENTED_WITH_LIMITED_LIVE_VALIDATION','checkedAt':datetime.now(timezone.utc).isoformat(),
             'offlineTestsPassed':41,'offlineLiveCaseSkipped':1,'secondJvmReplayPassed':True,
             'plannedDocuments':38,'eligibleBlocks':plan['eligibleBlocks'],'plannedTargets':plan['plannedTargets'],
             'plannedBatches':sum(len(d['batches']) for d in plan['documents']),'oversizeDeferred':plan['eligibleBlocks']-plan['plannedTargets'],
             'hardReviewBlocks':plan['hardReviewBlocks'],'liveNewAttempts':2,'liveValidatedResponses':1,'liveRejectedResponses':1,
             'selectedDecisions':dict(decisions),'finalAdoptedDecisions':dict(adopted),'providerReportedUsage':dict(usage),
             'budgetUsed':40,'budgetLimit':40,'original50LedgerUnchanged':True,'prior38PrefixPreserved':True,
             'sourceBlocksRoundtripChecked':checked,'sourceMappingErrors':0,'crossSourceReferences':0,'nonKeepCandidateLeaks':0,
             'cacheReplay':replay,'productionIndexWrites':0,'batchClassificationComplete':False,'independentQualityEvaluated':False,
             'liveFailureDetail':'Provider reply received; rejected before adoption; original generic failure record does not identify a field',
             'implementationHashes':{p.relative_to(ROOT).as_posix():sha(p) for p in paths}}
    (OUT/'verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (OUT/'live-review.md').write_text('\n'.join(report)+'\n',encoding='utf-8')
    (OUT/'live-budget-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k not in ['implementationHashes','cacheReplay']},ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
