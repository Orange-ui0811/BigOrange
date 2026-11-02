"""Score frozen pre-call labels against raw model proposals AND final source candidates."""
import hashlib
import json
from collections import Counter
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v1'
STAGE=ROOT/'_experiment_staging/readme-quality-v1'


def read(path):return json.loads(path.read_text(encoding='utf-8'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def ratio(a,b):return round(a/b,4) if b else None


def score(phase):
    data=read(DOC/'frozen-dataset.json');approval=read(DOC/'authorization.json')
    if sha(DOC/'frozen-dataset.json')!=approval['datasetSha256']:raise ValueError('Gold dataset changed')
    results={};attempts=[];mapping_errors=[];candidate_errors=[];raw_changes=[];versions=Counter()
    for sample in data['documents']:
        ident=sample['documentId'];path=STAGE/f'results/{ident}-primary.json'
        if phase=='final' and (STAGE/f'results/{ident}-retry.json').exists():path=STAGE/f'results/{ident}-retry.json'
        if not path.exists():continue
        entry=read(path)
        if entry['datasetSha256']!=approval['datasetSha256']:raise ValueError('Wrong result dataset')
        r=entry['result'];results[ident]=r;attempts.extend(r['attempts'])
        versions[(entry['policy'],entry['prompt'])]+=1
        original=next(d for d in read(ROOT/'_experiment_staging/readme-filter-v1-20261029/snapshot.json')['documents'] if str(d['documentId'])==ident)
        p=r['preview'];raw16=original['content'].encode('utf-16-le');at=0
        if p['sourceSha256']!=sample['sourceSha256'] or p['versionId']!=sample['versionId'] or p['sourceRef']!=sample['sourceRef']:raise ValueError('Cross source result')
        blocks={b['blockId']:b for b in p['blocks']}
        for b in p['blocks']:
            if b['startUtf16']!=at or raw16[at*2:b['endUtf16']*2].decode('utf-16-le')!=b['text']:mapping_errors.append(b['blockId'])
            at=b['endUtf16']
        if at*2!=len(raw16):raise ValueError('Partial source result')
        for packet in p['knowledgeCandidates']+p['pointerCandidates']:
            if packet['documentId']!=ident or packet['versionId']!=sample['versionId'] or packet['sourceRef']!=sample['sourceRef']:raise ValueError('Cross source packet')
            candidate_errors.extend(i for i in packet['primaryBlockIds']+packet['contextBlockIds'] if blocks[i]['decision']!='KEEP')
    rows=[];confusion=Counter()
    for case in data['cases']:
        ident=str(case['documentId']);gold=case['expected'];r=results.get(ident)
        selected={s['blockId']:s for s in r['selections']} if r else {}
        blocks={b['blockId']:b for b in r['preview']['blocks']} if r else {}
        s=selected.get(case['block']['blockId']);b=blocks.get(case['block']['blockId'],case['block'])
        if b['text']!=case['block']['text']:raw_changes.append(case['caseId'])
        proposed=s['decision'] if s else 'REVIEW';actual=b['decision']
        confusion[(gold['decision'],actual)]+=1
        kind_ok=s['kind'] in gold['kinds'] if s and proposed=='KEEP' and gold['decision']=='KEEP' else None
        scope_ok=s['scope'] in gold['scopes'] if s and proposed=='KEEP' and gold['decision']=='KEEP' else None
        rows.append({'caseId':case['caseId'],'documentId':ident,'sourceRef':case['sourceRef'],'cohort':case['cohort'],
                     'expected':gold['decision'],'proposed':proposed,'final':actual,'kind':s['kind'] if s else None,
                     'scope':s['scope'] if s else None,'kindCorrect':kind_ok,'scopeCorrect':scope_ok,'critical':gold['critical'],
                     'decisionCorrect':actual==gold['decision'],'expectedRationale':gold['reason'],
                     'lineRange':[case['block']['startLine'],case['block']['endLine']],'hadSelection':s is not None,'resultPresent':r is not None})
    plan=read(STAGE/'all-plans.json');baseline={d['documentId']:{b['blockId']:b for b in d['blocks']} for d in plan['documents']}
    control_errors=[]
    for c in data['controls']:
        ident=str(c['documentId']);r=results.get(ident)
        blocks={b['blockId']:b for b in r['preview']['blocks']} if r else baseline[ident]
        b=blocks[c['block']['blockId']]
        if b['decision']!=c['expectedDecision'] or b['text']!=c['block']['text']:control_errors.append(c['block']['blockId'])
    scope_cases=[r for r in rows if r['scopeCorrect'] is not None];kind_cases=[r for r in rows if r['kindCorrect'] is not None]
    expected_keep=sum(r['expected']=='KEEP' for r in rows);accepted_keep=sum(r['final']=='KEEP' for r in rows)
    valid=sum(a['status'] in ['MODEL_VALIDATED','CACHE_HIT'] for a in attempts)
    count_correct=sum(r['decisionCorrect'] for r in rows)
    critical_drop=sum(r['critical'] and r['expected']=='KEEP' and r['final']=='DROP' for r in rows)
    uncertain_keep=sum(r['expected']=='REVIEW' and r['final']=='KEEP' for r in rows)
    ledger=DOC/'provider-calls.jsonl';used=sum(bool(s.strip()) for s in ledger.read_text(encoding='utf-8').splitlines()) if ledger.exists() else 0
    receipt=read(DOC/'primary-budget-receipt.json') if (DOC/'primary-budget-receipt.json').exists() else None
    phase_used=max(read(p)['after']['entries'] for p in DOC.glob('primary-budget-receipt*.json')) if phase=='primary' and receipt else used
    journal=[json.loads(s) for s in (STAGE/'selection-attempts.jsonl').read_text(encoding='utf-8').splitlines() if s.strip()]
    cumulative=Counter(j['event'] for j in journal)
    usage=Counter()
    for j in journal:usage.update(j.get('usage',{}))
    all_present=len(results)==len(data['documents'])
    metrics={'phase':phase,'completedBatches':len(results),'plannedBatches':15,'labelledTargets':len(rows),'goldDecisionCounts':dict(Counter(r['expected'] for r in rows)),
             'attemptStatuses':dict(Counter(a['status'] for a in attempts)),'protocolValidBatches':valid,'protocolValidRate':ratio(valid,len(attempts)),
             'decisionAgreement':ratio(count_correct,len(rows)),'keepPrecision':ratio(sum(r['final']=='KEEP' and r['expected']=='KEEP' for r in rows),accepted_keep),
             'knowledgeRecovery':ratio(sum(r['final']=='KEEP' and r['expected']=='KEEP' for r in rows),expected_keep),
             'kindAccuracyOnKeep':ratio(sum(r['kindCorrect'] for r in kind_cases),len(kind_cases)),
             'scopeAccuracyOnKeep':ratio(sum(r['scopeCorrect'] for r in scope_cases),len(scope_cases)),
             'criticalWrongDrop':critical_drop,'uncertainWrongKeep':uncertain_keep,'controls':len(data['controls']),'controlErrors':len(control_errors),
             'sourceMappingErrors':len(mapping_errors),'sourceRewriteErrors':len(raw_changes),'nonKeepCandidateLeaks':len(candidate_errors),
             'budgetUsed':phase_used,'currentBudgetUsed':used,'budgetLimit':20,'allPrimaryResultsPresent':all_present,'userConfirmedGold':False,
             'wrongDropCases':sum(r['expected']=='KEEP' and r['final']=='DROP' for r in rows),
             'expectedKeepStillReview':sum(r['expected']=='KEEP' and r['final']=='REVIEW' for r in rows),
             'cumulativeJournalStatuses':dict(cumulative),'cumulativeProviderReportedUsage':dict(usage),
             'implementationVersions':[{'policy':p,'prompt':q,'batches':n} for (p,q),n in versions.items()],
             'mixedVersionAggregate':len(versions)>1,
             'proposalDecisionAgreement':ratio(sum(r['proposed']==r['expected'] for r in rows),len(rows)),
             'confusion':[{'expected':g,'final':p,'count':n} for (g,p),n in sorted(confusion.items())]}
    metrics['meetsProposedGate']=bool(all_present and metrics['protocolValidRate'] is not None and metrics['protocolValidRate']>=.95
        and metrics['decisionAgreement']>=.85 and metrics['keepPrecision'] is not None and metrics['keepPrecision']>=.95
        and not any([critical_drop,uncertain_keep,control_errors,mapping_errors,raw_changes,candidate_errors]))
    if metrics['mixedVersionAggregate']:metrics['meetsProposedGate']=False
    metrics['cohorts']={}
    for cohort in ['regression','new_repository']:
        group=[r for r in rows if r['cohort']==cohort]
        metrics['cohorts'][cohort]={'cases':len(group),'decisionAgreement':ratio(sum(r['decisionCorrect'] for r in group),len(group)),
                                  'criticalWrongDrop':sum(r['critical'] and r['expected']=='KEEP' and r['final']=='DROP' for r in group)}
    report=[f'# 扩大分类质量验收 · {phase}','','这些是助手预先审阅的原文标签，不是独立人工金标准；新仓库与复验仓库分开统计。',
            f"完成 {len(results)}/15 批，本阶段用量 {phase_used}/20，当前总用量 {used}/20。",'', '| 指标 | 实测 |','|---|---:|']
    for key in ['protocolValidRate','decisionAgreement','keepPrecision','knowledgeRecovery','kindAccuracyOnKeep','scopeAccuracyOnKeep','criticalWrongDrop','uncertainWrongKeep','controlErrors']:
        report.append(f'| {key} | {metrics[key]} |')
    report+=['',f"建议门槛是否达标：{metrics['meetsProposedGate']}。跑完不等于通过。",
             f"混合版本汇总：{metrics['mixedVersionAggregate']}；模型提案一致率 {metrics['proposalDecisionAgreement']}。",
             'final 是保留新仓库首轮、替换三个旧仓库回归后的诊断汇总，不是统一新提示词的独立验收。' if phase=='final' else '首轮结果未被回归覆盖。',
             '', '## 分歧清单','']
    for row in rows:
        if row['decisionCorrect'] and row['kindCorrect'] is not False and row['scopeCorrect'] is not False:continue
        report += [f"### {row['caseId']} · {row['sourceRef']} · L{row['lineRange'][0]}–L{row['lineRange'][1]}",'',
                   f"预期 {row['expected']}；模型 {row['proposed']}；合并后 {row['final']}；类型 {row['kind']}；范围 {row['scope']}。",
                   row['expectedRationale'],'']
    (DOC/f'{phase}-evaluation.json').write_text(json.dumps({'metrics':metrics,'cases':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (DOC/f'{phase}-evaluation.md').write_text('\n'.join(report)+'\n',encoding='utf-8')
    print(json.dumps(metrics,ensure_ascii=False))
    if used>20 or mapping_errors or raw_changes or candidate_errors:raise ValueError('Budget/source invariant failed')


if __name__=='__main__':
    import argparse
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--phase',choices=['primary','final'],default='primary')
    score(parser.parse_args().phase)
