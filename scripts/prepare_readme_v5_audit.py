"""Trace unrecovered README knowledge and freeze a bounded V5 diagnostic proposal, without HTTP."""
from collections import Counter, deque
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from score_readme_quality_v2 import evaluate

ROOT=Path(__file__).resolve().parents[1]
Q=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v2'
OUT=Q/'v5-audit-v1'
STAGE=ROOT/'_experiment_staging/readme-v5-audit-v1'
PILOT=Q/'v5-pilot-v1'
PILOT_DOCS=['240','244','427','522','546']
BODY={'KNOWLEDGE_CANDIDATE','CONTEXT_ONLY'}
DEPENDENCY_REASONS={'DEPENDENCY_NOT_APPROVED','DEPENDENCY_NOT_ADMITTED','REQUIRED_CONTEXT_NOT_ADMITTED'}


def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def digest(s):return hashlib.sha256(s.encode('utf-8')).hexdigest()
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def md(p,lines):p.parent.mkdir(parents=True,exist_ok=True);p.write_text('\n'.join(lines)+'\n',encoding='utf-8')


def verify_inputs():
    for name,value in read(OUT/'before.json')['protectedFiles'].items():assert sha(ROOT/name)==value,'Historical input changed: '+name
    for name,value in read(STAGE/'plan-inputs.json').items():assert sha(ROOT/name)==value,'Planner input changed: '+name
    path=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.K3V5AuditPlanTest.xml'
    suite=ET.parse(path).getroot();assert int(suite.attrib['tests'])==1
    assert all(int(suite.attrib[k])==0 for k in ['errors','failures','skipped'])
    assert 'BUILD SUCCESS' in (STAGE/'planning-tests.log').read_text(encoding='utf-8-sig')
    assert not (STAGE/'unused-ledger.jsonl').exists()


def component(doc,bid):
    edges={b['blockId']:set() for b in doc['preview']['blocks']}
    blocks={b['blockId']:b for b in doc['preview']['blocks']}
    for key,refs in doc['structuralDependencies'].items():
        for ref in refs:
            if blocks[ref]['kind']!='HEADING':edges[key].add(ref);edges[ref].add(key)
    queue=[bid];seen=set()
    while queue:
        key=queue.pop()
        if key in seen:continue
        seen.add(key);queue.extend(edges[key]-seen)
    return seen


def coverage(doc,bid,variant):
    blocks={b['blockId']:b for b in doc['preview']['blocks']};b=blocks[bid]
    targets=[x['cacheKey'] for x in variant['batches'] if bid in x['targetIds']]
    contexts=[x['cacheKey'] for x in variant['batches'] if bid in x['contextIds']]
    reason=None;hard=[];unit=None
    if targets:reason='MODEL_TARGET'
    elif b['decision']=='REVIEW' and bid not in doc['softEligible']:reason='HARD_SOURCE_REVIEW'
    elif doc['ruleAssessments'][bid]['reason'] in {'MIXED_PRODUCT_REQUIRES_SPLIT','MIXED_EDITION_REQUIRES_SPLIT'}:reason='HARD_SEMANTIC_REVIEW'
    elif bid in doc['needsModel']:
        unit=next((u for u in variant['units'] if bid in u['targetIds']),None)
        if unit:reason=unit['reason'];assert not unit['fits']
        else:
            hard=[i for i in component(doc,bid) if blocks[i]['decision']=='REVIEW' and i not in doc['softEligible']]
            assert hard,'Missing target not explained by the production hard-dependency rule'
            reason='HARD_DEPENDENCY_COMPONENT'
    elif b['decision']=='DROP' or doc['ruleAssessments'][bid]['applicability']=='OUT_OF_SCOPE':reason='RULE_DROP_OR_OUT_OF_SCOPE'
    elif doc['ruleAssessments'][bid]['disposition']=='POINTER_METADATA_ONLY':reason='RULE_NAVIGATION'
    else:reason='RULE_OR_PROJECTION_ONLY'
    return {'status':reason,'targeted':bool(targets),'targetBatchKeys':targets,'contextBatchKeys':contexts,
            'contextOnly':bool(contexts) and not targets,'hardDependencyBlockIds':sorted(hard),
            'unitTargets':unit['targetIds'] if unit else [],'unitInputUpperBound':unit['inputUpperBound'] if unit else None}


def blockers(doc,assessed,bid):
    refs={r:'STRUCTURAL' for r in doc['structuralDependencies'][bid] if assessed[r]['disposition'] not in BODY}
    for r in doc['appliedGuardEdges'][bid]:
        if r!=bid and assessed[r]['applicability']!='OUT_OF_SCOPE' and assessed[r]['disposition'] not in BODY:
            refs[r]='APPLICABLE_GUARD' if r not in refs else 'STRUCTURAL_AND_GUARD'
    return [{'blockId':r,'edgeType':kind} for r,kind in sorted(refs.items())]


def root_blockers(doc,assessed,bid):
    queue=deque([(bid,[])]);seen=set();leaves={}
    while queue:
        current,path=queue.popleft()
        if current in seen:continue
        seen.add(current)
        a=assessed[current];next_refs=blockers(doc,assessed,current)
        if current!=bid and (a['reason'] not in DEPENDENCY_REASONS or not next_refs):
            leaves[current]={'blockId':current,'reason':a['reason'],'disposition':a['disposition'],'path':path};continue
        for ref in next_refs:
            if ref['blockId'] not in seen:queue.append((ref['blockId'],path+[{'from':current,**ref}]))
    return list(leaves.values())


def group(row):
    reason=(row['assessment'] or {}).get('reason','MISSING')
    if reason in DEPENDENCY_REASONS:return 'DEPENDENCY_BLOCKED'
    if reason=='MODEL_CLASSIFIED_SOURCE':return 'BODY_CLASSIFIED_AS_POINTER'
    if reason=='INSTRUCTION_LIKE_SOURCE':return 'INSTRUCTION_LIKE_SOURCE'
    return 'OLD_MODEL_REVIEW'


def build_labels(original,source_blocks):
    revised=deepcopy(original);revised['schemaVersion']='README_AUDIT_LABELS_V3_SUPPLEMENTAL'
    revised['annotationAuthority']='ASSISTANT_SOURCE_REVIEWED_AFTER_OBSERVING_RESULTS_NOT_BLIND'
    revised['baseDatasetSha256']=sha(Q/'r4/frozen-dataset.json')
    revised['cohort']='POST_REPAIR_REGRESSION_REVIEW_NOT_NEW_HELD_OUT_REPOSITORIES'
    revised['selection']={'basis':'The same 12 R4 repositories, already used for R3 repairs. Historical no-overlap assertions apply to their original selection only.',
                          'priorOutputsObserved':True,'independentValidation':False}
    revised['providerCallsBeforeFreeze']=35
    revised['currentV5ProviderCallsBeforeFreeze']=0
    revised['baseCaseCount']=147;revised['changes']=[]
    for c in revised['cases']:
        if c['caseId']=='Q2-240-52':
            before=deepcopy(c['expected'])
            c['expected']['rationale']='保存原句完整，旧“破折号后截断”理由不成立。定位事实与“功能齐全／无需妥协”的绝对化承诺混在整块；按不确定内容待复核规则保留 REVIEW，未证明具体完整能力。'
            revised['changes'].append({'caseId':c['caseId'],'change':'RATIONALE_CORRECTED_DECISION_UNCHANGED','before':before,'after':deepcopy(c['expected']),
                                      'basis':'Source ends with a complete sentence; no claim of a verified truncation. Whole-block unsupported absolute promise remains unresolved.'})
    raw_by_doc={d['source']['documentId']:d for d in read(Q/'r4/sources.json')['documents']}
    app=raw_by_doc['522'];by_line={b['startLine']:b for b in app['blocks']}
    nav=next(c for c in original['cases'] if c['caseId']=='Q2-522-36')['block']['blockId']
    supplements=[
        ('S1-522-34',34,{'decision':'REVIEW','contentDecision':'KEEP','applicability':'IN_SCOPE','disposition':'REVIEW_QUEUE','requiredScopes':['CORE'],
         'category':'H','knownUsefulButBlocked':True,'requiredBlockIds':[nav,by_line[39]['blockId']],
         'rationale':'有效的“两种使用方式”引导句，独立时缺少方式内容；配套信息是导航和文档入口，不能把引导句本身当独立部署知识或平台能力。暂保留为待复核的配套上下文。'}),
        ('S1-522-39',39,{'decision':'KEEP','contentDecision':'KEEP','applicability':'UNKNOWN','disposition':'POINTER_METADATA_ONLY','requiredScopes':[],
         'category':'N','knownUsefulButBlocked':False,'requiredBlockIds':[],
         'rationale':'安装文档目录表：保留 Docker、Kubernetes、AWS AMI 的原文标签和地址及 Recommended 导航提示；图片徽章不证明环境要求，未读目的页不能当作安装步骤正文。'})]
    for cid,line,expected in supplements:
        b=deepcopy(by_line[line]);assert b['blockId'] in source_blocks
        e=dict(expected,critical=False,recoveryEligible=False,headingBlockIds=b['headingIds'])
        revised['cases'].append({'caseId':cid,'documentId':'522','sourceRef':app['source']['sourceRef'],'role':'SUPPLEMENTAL_CONTROL',
                                'block':b,'expected':e,'annotationAuthority':revised['annotationAuthority']})
    assert len(revised['cases'])==149
    base={c['caseId']:c for c in original['cases']}
    for c in revised['cases']:
        if c['caseId'] not in base:continue
        expected=deepcopy(base[c['caseId']]);actual=deepcopy(c)
        if c['caseId']=='Q2-240-52':actual['expected']['rationale']=expected['expected']['rationale']
        assert actual==expected,'Original labels changed beyond the declared rationale correction'
    for c in revised['cases']:
        assert c['block']==source_blocks[c['block']['blockId']]
        assert digest(c['block']['text'])==c['block']['sha256']
    return revised


def main():
    if (OUT/'audit-seal.json').exists():
        raise RuntimeError('Audit is sealed; use its read-only verifier or prepare a new version')
    if (PILOT/'provider-calls.jsonl').exists() or (PILOT/'authorization.json').exists():
        raise RuntimeError('Execution has started or been authorized; do not replace its inputs')
    verify_inputs()
    plans=read(STAGE/'all-plans.json');assert plans['providerCalls']==plans['indexWrites']==0
    replay=read(Q/'r3-repair-v2/offline-replay-evaluation.json')
    raw_replay=read(ROOT/'_experiment_staging/readme-r3-repair-v2/replay-results.json')
    results={d['documentId']:d['result'] for d in raw_replay['documents']}
    original=read(Q/'r4/frozen-dataset.json');sources=read(Q/'r4/sources.json')['documents']
    blocks={b['blockId']:b for s in sources for b in s['blocks']}
    cases={c['block']['blockId']:c for c in original['cases']}
    docs={d['source']['documentId']:d for d in plans['documents']}
    current={b['blockId']:b for d in docs.values() for b in d['preview']['blocks']}
    assert set(blocks)==set(current)
    for bid,b in blocks.items():
        assert all(b[k]==current[bid][k] for k in ['kind','text','sha256','startUtf16','endUtf16','startLine','endLine','headingIds','sectionPath'])
    rows=[]
    for scored in replay['cases']:
        if not scored['expected']['recoveryEligible'] or scored['usableBody']:continue
        doc=docs[scored['documentId']];bid=scored['blockId'];assessed={a['blockId']:a for a in results[scored['documentId']]['recommendation']['assessments']}
        roots=root_blockers(doc,assessed,bid)
        for root in roots:
            b=current[root['blockId']];root.update({'caseId':cases.get(root['blockId'],{}).get('caseId'),'startLine':b['startLine'],
                    'raw':b['text'],'modelCoverage':{v['name']:coverage(doc,root['blockId'],v) for v in doc['variants']}})
        row={**scored,'sourceBlock':blocks[bid],'currentRuleBlock':current[bid],
             'directBlockers':blockers(doc,assessed,bid),'rootBlockers':roots,'causeGroup':group(scored),
             'newPlanCoverage':{v['name']:coverage(doc,bid,v) for v in doc['variants']}}
        rows.append(row)
    assert len(rows)==29
    assert Counter(r['causeGroup'] for r in rows)=={'DEPENDENCY_BLOCKED':16,'OLD_MODEL_REVIEW':10,'BODY_CLASSIFIED_AS_POINTER':2,'INSTRUCTION_LIKE_SOURCE':1}
    coverage_rows=[]
    for doc in docs.values():
        for bid in doc['needsModel']:
            coverage_rows.append({'documentId':doc['source']['documentId'],'blockId':bid,
                'coverage':{v['name']:coverage(doc,bid,v) for v in doc['variants']}})
    metrics={'unrecovered':29,'causeGroups':dict(Counter(r['causeGroup'] for r in rows)),
             'unrecoveredTargetCoverage':{v:dict(Counter(r['newPlanCoverage'][v]['status'] for r in rows)) for v in ['max4','max8']},
             'allNeedsModelCoverage':{v:dict(Counter(r['coverage'][v]['status'] for r in coverage_rows)) for v in ['max4','max8']}}
    write(OUT/'audit.json',{'metrics':metrics,'cases':rows,'providerCalls':0,'indexWrites':0,'versions':plans['versions']})
    write(OUT/'batch-coverage.json',{'cases':coverage_rows,'metrics':metrics,'planningEvidenceSha256':sha(STAGE/'all-plans.json')})
    lines=['# 29 条未恢复知识：原文与阻断点归因','','所有结果来自本地保存原文、R5 旧响应及当前 V5 的真实 Java 规划；本轮未调用模型。',
           'MODEL_TARGET 才能获得模型选择；仅出现在 context 不算被分类。扩大为 8 块仅是已有参数的离线对照，未修改默认配置。','',
           '| 分类 | 条数 |','|---|---:|']
    for k,n in metrics['causeGroups'].items():lines.append(f'| {k} | {n} |')
    lines+=['','| 新版请求覆盖 | max4 | max8 |','|---|---:|---:|']
    for k in sorted(set(metrics['unrecoveredTargetCoverage']['max4'])|set(metrics['unrecoveredTargetCoverage']['max8'])):
        lines.append(f"| {k} | {metrics['unrecoveredTargetCoverage']['max4'].get(k,0)} | {metrics['unrecoveredTargetCoverage']['max8'].get(k,0)} |")
    lines+=['','根因路径同时包含结构依赖和程序认定适用的 guard；这些边表示当前实现为何阻断，不是对许可或业务适用性的外部确认。','']
    for r in rows:
        b=r['sourceBlock'];a=r['assessment'] or {};proposal=r['proposal'] or {}
        lines += [f"## {r['caseId']} · {r['sourceRef']} · L{b['startLine']}–{b['endLine']}",'',
            f"主因：{r['causeGroup']}。旧模型：{proposal.get('decision','无有效选择')} / {proposal.get('kind','无')}；当前：{a.get('effectiveDecision')} / {a.get('disposition')} / {a.get('reason')}。",
            f"新版覆盖：max4={r['newPlanCoverage']['max4']['status']}；max8={r['newPlanCoverage']['max8']['status']}。",'', '~~~~~~~text',b['text'].rstrip('\n'),'~~~~~~~','',
            '预期依据：'+r['expected']['rationale'],'']
        if r['rootBlockers']:
            lines+=['根因节点：']
            for root in r['rootBlockers']:
                lines.append(f"- {root['caseId'] or root['blockId']} · L{root['startLine']} · {root['reason']}；max8={root['modelCoverage']['max8']['status']}。")
        elif r['causeGroup']=='BODY_CLASSIFIED_AS_POINTER':lines+=['处理方向：复核正文事实与导航的混合分类；SOURCE_POINTER 继续禁止直接回落为正文。']
        elif r['causeGroup']=='INSTRUCTION_LIKE_SOURCE':lines+=['处理方向：核查是否为攻击样例说明；保持所有来源内容不可信且不可执行，不按仓库名放行。']
        else:lines+=['处理方向：先确认新请求是否覆盖目标；实际 V5 判断只能通过新调用核验。']
        lines.append('')
    md(OUT/'audit.md',lines)
    labels=build_labels(original,blocks);write(OUT/'reviewed-labels-v3.json',labels)
    revised=deepcopy(labels);revised['versions']=plans['versions']
    supplemental=evaluate(revised,sources,results)
    supplemental.update({'mode':'POST_OUTPUT_ASSISTANT_ANNOTATION_EXTENSION_DIAGNOSTIC','newProviderCalls':0,
                         'baseDatasetCases':147,'supplementalCases':2,'originalResultsUntouched':True,'newPromptEvaluated':False})
    write(OUT/'supplemental-evaluation.json',supplemental)
    label_lines=['# 标签复核与新增候选','','这是观察结果后的助手复核，不是独立人工或盲测。R4 原文件及 R5 首次成绩未修改。',
        '', '## Q2-240-52：修正理由，保持 REVIEW','',labels['changes'][0]['after']['rationale'],'',
        '旧标签为 REVIEW，新标签仍为 REVIEW；理由纠正不产生任何分数提升。','', '## 两个补标候选','']
    for c in labels['cases'][-2:]:
        label_lines += [f"### {c['caseId']} · L{c['block']['startLine']}",'',c['expected']['rationale'],
            f"预期：{c['expected']['decision']} / {c['expected']['disposition']}；不加入 B 类恢复率分母。",'']
    m=supplemental['metrics'];label_lines += [f"扩展为 149 条后，正文精确率为 {m['bodyPrecision']['numerator']}/{m['bodyPrecision']['denominator']}；两个原先未计分的正文候选纳入准入口径，不能继续把 98.15% 当全候选准确率。",
        '旧 147 条口径单独保留，不以新标签覆盖旧成绩。新增错误应进入规则修复队列；真实模型试跑只能影响实际发送的目标。']
    md(OUT/'label-review.md',label_lines)
    pilot_docs=[docs[i] for i in PILOT_DOCS];batches=[]
    for doc in pilot_docs:
        variant=next(v for v in doc['variants'] if v['maxBlocks']==8)
        for ordinal,b in enumerate(variant['batches'],1):
            batches.append({'requestId':f"V5-{doc['source']['documentId']}-{ordinal:02d}",**b,'outputTokens':2048,
                            'systemSha256':digest(b['system']),'userSha256':digest(b['user'])})
    assert 3<=len(pilot_docs)<=5 and 0<len(batches)<=38
    maximum=len(batches)+2
    subset=deepcopy(labels);subset['cases']=[c for c in labels['cases'] if c['documentId'] in PILOT_DOCS]
    subset['documents']=[d for d in labels['documents'] if d['documentId'] in PILOT_DOCS]
    for d in subset['documents']:
        rr=[b for b in batches if b['documentId']==d['documentId']]
        d['historicalR4Requests']=d.pop('requests',None);d['historicalR4ModelTargets']=d.pop('modelTargets',None)
        d['currentPilotRequests']=len(rr);d['currentPilotModelTargets']=sum(len(b['targetIds']) for b in rr)
    subset['versions']=plans['versions'];subset['scope']='PURPOSIVE_REGRESSION_PILOT_NOT_INDEPENDENT_ACCEPTANCE'
    subset['roleMeaning']='Original R4 label sampling role, not the current pilot sent-target set; score actual sent targets separately.'
    assert {i for b in batches for i in b['targetIds']}<={c['block']['blockId'] for c in subset['cases']},'Unlabelled model target'
    write(PILOT/'frozen-labels.json',subset)
    write(PILOT/'requests.json',{'versions':plans['versions'],'batches':batches})
    write(PILOT/'sources.json',{'documents':[s for s in sources if s['source']['documentId'] in PILOT_DOCS]})
    provider=read(Q/'r4/provider-config.json');provider['limits']={'maxInputUpperBound':12000,'maxBlocks':8,'retries':0}
    provider['configurationSource']='Existing R4 nonsecret fingerprint; must match actual runtime again before HTTP'
    write(PILOT/'provider-config.json',provider)
    proposal={'authorized':False,'status':'BLOCKED_PENDING_DETERMINISTIC_RULE_REPAIR_AND_REPLAN',
        'readyForExecution':False,'requiresReplanBeforeAuthorization':True,
        'blockingIssues':['FLOWISE_LAYOUT_AS_CODE_PREREQUISITE','N8N_LICENSE_NAVIGATION_AS_GLOBAL_GUARD','APPSMITH_NONBODY_RULE_PASSTHROUGH'],
        'countsApplyOnlyTo':'Current V1.4/V5 planning snapshot; repair changes invalidate these exact inputs and counts.',
        'scope':'V5 diagnostic pilot only, no production/index changes','documentIds':PILOT_DOCS,
        'repositories':[d['source']['sourceRef'] for d in pilot_docs],'maxBlocks':8,'maxInputUpperBound':12000,'maxOutputTokens':2048,
        'primaryRequests':len(batches),'reserveRequests':2,'maximumProposedAttempts':maximum,'automaticRetries':0,
        'firstPassReservedUpperBound':sum(b['inputUpperBound']+2048 for b in batches),'maximumReservedUpperBound':maximum*(12000+2048),
        'reservePolicy':'At most one same-input retry for each of the first two transport/protocol failures; no semantic-quality resampling.',
        'newLedgerPath':(PILOT/'provider-calls.jsonl').relative_to(ROOT).as_posix(),
        'oldR5BudgetTransferable':False,'original29ObservationCasesInPilot':sum(r['documentId'] in PILOT_DOCS for r in rows),
        'original29ActuallyTargetedInPilot':sum(r['documentId'] in PILOT_DOCS and r['newPlanCoverage']['max8']['targeted'] for r in rows),
        'modelTargets':sum(len(b['targetIds']) for b in batches),'labelledCases':len(subset['cases']),
        'pilotPurpose':'Check reachable targets under V5 and observe projection blockers. Blocked/fixed-rule cases remain in scoped denominators; pilot is not the final quality gate.'}
    write(PILOT/'execution-proposal.json',proposal)
    pilot_lines=['# V5 小批真实分类验收草案','','状态：当前 V1.4 请求快照已准备，但存在已确认的确定性阻断，尚不具备执行条件；尚未授权、尚未调用。先修规则，随后按新代码重新生成并封存请求。本集属于回归诊断。','',
        f"建议 {len(pilot_docs)} 个仓库、{len(batches)} 次首轮＋2 次失败预留，最多 {maximum} 次；每批最多 8 块，输入上界 12000，输出 2048，超时 35 秒，无自动重试。",
        '使用已有 maxBlocks 参数，不改生产默认值；8 块对照能覆盖 4 块上限跳过的 6/5 块依赖单元。较长输出仍可能被协议拒绝，失败照常计数。','',
        f"观察原 29 条中的 {proposal['original29ObservationCasesInPilot']} 条；其中实际新模型目标 {proposal['original29ActuallyTargetedInPilot']} 条。其他条目由固定规则或依赖决定，不能声称调用即可恢复。",'',
        '| 仓库 | 首轮请求 | 模型目标 |','|---|---:|---:|']
    for doc in pilot_docs:
        rr=[r for r in batches if r['documentId']==doc['source']['documentId']]
        pilot_lines.append(f"| {doc['source']['sourceRef']} | {len(rr)} | {sum(len(r['targetIds']) for r in rr)} |")
    pilot_lines += ['', '执行前核验源文、规则、提示词、模型身份和请求哈希，单独绑定新额度与新账本；旧 35/40 剩余额度不转入。',
        '保存首轮原始响应、协议状态、usage、最终候选及失败原因；预留只用于同请求传输或协议失败，首轮成绩不覆盖。',
        '小批报告分别列出模型提案、最终准入、规划不可达项、规则直通项和补标候选；不得移除失败或未发送项以缩小分母。',
        '本轮不打开 README 链接、不执行其中命令、不写活动索引；完整质量通过仍需后续新样本验证。',
        '', '当前阻断：Flowise HTML 闭合标记被当作代码前提；n8n 许可证导航被当成全局必要条件；Appsmith 引导句和文档表仍由规则直通正文。规则修复后，缓存键、目标、上下文和批次数需要全部重核。']
    md(PILOT/'acceptance-plan.md',pilot_lines)
    evidence={p.relative_to(ROOT).as_posix():sha(p) for p in [STAGE/'all-plans.json',STAGE/'plan-inputs.json',STAGE/'planning-tests.log',
        ROOT/'orange-server/src/test/java/com/orangejd/server/ragb/ingest/K3V5AuditPlanTest.java',Path(__file__).resolve()]}
    write(OUT/'verification.json',{'status':'V5_AUDIT_AND_PILOT_INPUTS_PREPARED_OFFLINE','metrics':metrics,
        'supplementalLabelMetrics':supplemental['metrics'],'originalCases':147,'revisedCases':149,'unrecoveredCases':29,
        'repositoriesPlanned':12,'planningTests':1,'newProviderCalls':0,'indexWrites':0,'oldR5Calls':35,'historicalFilesUnchanged':True,
        'pilotProposal':proposal,'evidenceHashes':evidence})
    print(json.dumps({'metrics':metrics,'supplementalPrecision':supplemental['metrics']['bodyPrecision'],'pilotProposal':proposal},ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
