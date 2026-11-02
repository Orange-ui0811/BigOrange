"""Read-only service audit and local report generation. Never invokes an AI provider."""
import json, statistics, sys
from datetime import datetime, timezone
from customer_workflow_http_acceptance import REPORT, LEDGER, api

def main():
    report=json.loads(REPORT.read_text(encoding='utf-8')); turns=report['turns'];checks=[]
    def check(name,passed,evidence):checks.append(dict(name=name,passed=bool(passed),evidence=evidence))
    job=[t for t in turns if t['session']=='job-fixed']
    first,interruption,answer=job[:3]
    q=first['result']['question']['id']
    check('首轮追问岗位而非平台分类','岗位' in first['result']['question']['text'] and not first['state'].get('pendingConditions'),first['runId'])
    check('插问保留原问题和需求版本',interruption['result']['question']['id']==q and interruption['result']['task']['requirementVersion']==first['result']['task']['requirementVersion'],interruption['runId'])
    check('回答关闭问题并保存两周条件',not answer['result']['task']['question'] and answer['state']['maxDurationDays']==14 and not answer['result']['projects'],answer['runId'])
    pause=next(t for t in reversed(job) if t['message']=='暂停这个任务' and t['status']=='SUCCEEDED')
    resume=next(t for t in reversed(job) if t['message']=='继续这个任务')
    check('暂停和恢复保留同一任务及需求',pause['result']['task']['status']=='PAUSED' and resume['result']['task']['status']!='PAUSED' and pause['result']['task']['id']==resume['result']['task']['id'] and pause['result']['task']['requirementVersion']==resume['result']['task']['requirementVersion'],[pause['runId'],resume['runId']])
    selection=next(t for t in job if t['message']=='就选第一个项目')
    check('绑定列表指代选中正确项目并完成任务',selection['result']['task']['status']=='COMPLETED' and selection['result']['task']['selectedProject']['name']=='embabel/embabel-agent-examples',selection['runId'])
    recording=next(t for t in turns if t['session']=='shop-final' and '库存管理系统' in t['message'])
    req=recording['result']['task']['requirements']
    check('库存主题与扫码离线要求保留','库存管理' in json.dumps(recording['state'].get('topic'),ensure_ascii=False) and all(any(term in r['text'] and r['strength']=='HARD' for r in req) for term in ('扫码','离线')) and not recording['state'].get('pendingConditions'),recording['runId'])
    legacy=api(report).request('GET',f"/sessions/{report['sessions']['legacy-final']}/messages")['state']
    task=legacy['workflowV2']['task'];active=[r for r in task['requirements'] if r['status']=='ACTIVE']
    check('旧会话确认仅恢复有原话的需求',len(active)==1 and active[0]['field']=='language' and legacy['language']=='Java' and 'goal' not in legacy and not task['lists'],report['sessions']['legacy-final'])
    check('已关闭问题不再显示等待状态',not any(q['status']=='OPEN' for q in task['questions']) and task['status']=='DISCOVERING',report['sessions']['legacy-final'])
    strict=next(t for t in reversed(turns) if '只要已核实满足全部必要条件' in t['message'])
    check('严格模式设置成功且不是项目能力需求',strict['state'].get('verificationMode')=='STRICT' and all(r['field']!='verificationMode' for r in strict['result']['task']['requirements']),strict['runId'])
    ledger=[json.loads(line) for line in LEDGER.read_text(encoding='utf-8').splitlines()]
    check('请求上限生效且账本未超限',len(ledger)==50 and turns[-1]['errorCode']=='ACCEPTANCE_BUDGET_EXHAUSTED',turns[-1]['runId'])
    ordinary=[t['durationMs'] for t in turns if t.get('status')=='SUCCEEDED' and t.get('providerRequests')==1]
    report['review']=dict(checkedAt=datetime.now(timezone.utc).isoformat(),checks=checks,releaseApproved=False,
        verdict='PARTIAL_PASS',strictEndToEnd='NOT_COMPLETED_BUDGET_EXHAUSTED',
        inventoryRecommendation='NOT_PASSED_PURPOSE_BATCH_INCOMPLETE',
        budget=dict(limit=50,ledgerEntries=len(ledger),localMockHttpEntries=list(range(22,33)),providerAttemptsIncludingFailed=39,remainingLedgerCapacity=0,note='11 intercepted or loopback HTTP fixture calls inherited the acceptance ledger; retained conservatively, never reset or reclaimed.'),
        latency=dict(method='Client submission to terminal polling result; includes polling overhead, not a load test',singleProviderTurnSamples=len(ordinary),singleProviderTurnMinMs=min(ordinary),singleProviderTurnMaxMs=max(ordinary),singleProviderTurnMedianMs=statistics.median(ordinary),jobPreviewMs=next(t['durationMs'] for t in job if t['message']=='先看看'),inventoryDegradedResultMs=next(t['durationMs'] for t in reversed(turns) if t['session']=='shop-final'),excludedEnvironmentInterferenceMs=90093))
    report['providerRequests']=len(ledger);REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    REPORT.with_name('live-review.json').write_text(json.dumps(report['review'],ensure_ascii=False,indent=2),encoding='utf-8')
    text=['# 本地逐轮 HTTP 验收记录','','时间：2026-10-29。合成账号；真实 HTTP、数据库、LangGraph4j、B 索引与模型。浏览器迁移确认另外记录。失败记录保留，不以 SUCCEEDED 等同业务通过。','','| 会话 | 输入 | 协议状态 | 业务状态/错误 | 耗时 ms | 本轮账本增量 |','|---|---|---|---|---:|---:|']
    for t in turns:text.append('| '+ ' | '.join(str(v).replace('|','/').replace('\n',' ') for v in (t['session'],t['message'],t.get('status'),t.get('errorCode') or t.get('result',{}).get('status'),t.get('durationMs'),t.get('providerRequests'))) +' |')
    REPORT.with_name('http-turns.md').write_text('\n'.join(text)+'\n',encoding='utf-8')
    print(json.dumps(report['review'],ensure_ascii=False))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
