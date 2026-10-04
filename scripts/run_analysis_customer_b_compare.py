"""Paired product comparison: B intelligent analysis vs B customer.
Synthetic profiles; dedicated sessions on authorized account 1 with no confirmed profile.
Existing rows are never rerun; no account profile/history contents are read by the runner.
"""
import argparse,json,sys,time,uuid,random,hashlib
from pathlib import Path
from datetime import datetime,timezone
from customer_local_smoke import sql,configured_environment,authorized_token
from run_customer_cases import Api,TERMINAL
from run_rag_b_first_round import call
from run_trend_rag_ab import snapshot

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/customer/analysis-b-vs-customer-b-20261003'
CASES=[
 ('P01_spring_beginner',['Spring Boot'],'学习','刚学',['Java'],'60天内','只推荐 Java 项目，从能运行的小功能开始',{'language':'Java','maxDurationDays':60}),
 ('P02_rag_career',['RAG','知识库'],'求职','有一年基础',['Java'],'半年内','只推荐 Java 项目，希望做能写进简历的知识库问答作品',{'language':'Java','maxDurationDays':180}),
 ('P03_rag_python',['RAG'],'学习','有基础',['Python'],'两个月内','只推荐 Python 项目，希望中文提问检索英文技术文档，并说明部署依赖',{'language':'Python','maxDurationDays':60}),
 ('P04_agent_multi',['Agent','智能体'],'学习；赚钱/接单/创业','有两年基础',['TypeScript'],'三个月内','只推荐 TypeScript 项目，想改造成服务小团队的工具；不承诺收益',{'language':'TypeScript','maxDurationDays':90}),
 ('P05_vue_no_docker',['Vue'],'学习','刚学',['JavaScript'],'一个月内','只推荐 JavaScript 项目，不使用 Docker，优先能演示的前端小作品',{'language':'JavaScript','maxDurationDays':30,'allowDocker':False}),
 ('P06_data_career',['数据分析'],'求职','有基础',['Python'],'两个月内','只推荐 Python 项目，想展示数据处理和可视化能力',{'language':'Python','maxDurationDays':60}),
 ('P07_custom_inventory',['库存管理'],'给家里的小店做库存管理','刚学',['Java'],'四个月内','只推荐 Java 项目，不使用 Docker，先做商品和库存记录的小功能',{'language':'Java','maxDurationDays':120,'allowDocker':False}),
 ('P08_unavailable',['量子纠错芯片流片'],'探索技术兴趣','刚学',['Java'],'两天内','只推荐 Java 项目，不能使用硬件；只能推荐能核实符合条件的现有仓库，找不到请说明，不编造，也不要自动放宽条件',{'language':'Java','maxDurationDays':2}),
]
def save(name,value):
    OUT.mkdir(parents=True,exist_ok=True);(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2,default=str),encoding='utf-8')
def verify_profile_empty():
    if sql("SELECT COUNT(*) FROM persona_profile_entry WHERE account_id=1 AND status='ACTIVE'")!='0':raise RuntimeError('Account 1 acquired a real profile; stop before external calls')
def definitions():
    rows=[]
    for key,topics,goal,experience,languages,duration,custom,hard in CASES:
        message=f'请按以下本轮画像直接推荐项目：技术方向是{ "、".join(topics)}；目标是{goal}；基础是{experience}；语言是{ "、".join(languages)}；完成周期是{duration}。{custom}。条件不自动放宽；资料缺失可以展示真实探索候选，但必须标明尚未核实。请说明功能、适合原因、实践范围、环境依赖和风险。'
        profile=dict(topics=topics,purpose=goal,experience=experience,languages=languages,timeCommitment=duration,customPurpose=message,useRag=True,usePersona=False,preferBeginnerProjects='学习' in goal,preferCareerProjects='求职' in goal)
        rows.append(dict(id=key,profile=profile,message=message,hard=hard,goal=goal,experience=experience))
    return rows
def preflight():
    verify_profile_empty();env=configured_environment()
    return dict(at=datetime.now(timezone.utc).isoformat(),profileEntries=0,account=1,
        customer={k:env.get(k) for k in ['CUSTOMER_MODEL','CUSTOMER_THINKING','CUSTOMER_NATIVE_ACTIONS','AI_TEXT_ENDPOINT']},
        capabilities=Api('http://127.0.0.1:18080',authorized_token(1)).request('GET','/capabilities'),snapshot=snapshot())
def analysis(case,round_no):
    row=dict(case=case['id'],round=round_no,product='analysisB',startedAt=datetime.now(timezone.utc).isoformat());started=time.perf_counter()
    try:row['response']=call('POST','/admin/rag-lab/compare/platform-analysis',dict(variant='B',profile=case['profile'],generate=True,observe=True),timeout=210)
    except Exception as error:row['error']=str(error)[:700]
    row['clientWallMs']=(time.perf_counter()-started)*1000;return row
def customer(case,round_no):
    verify_profile_empty();api=Api('http://127.0.0.1:18080',authorized_token(1));session=api.request('POST','/sessions',{'title':f'B双链路实验 {case["id"]} 第{round_no}轮'})
    row=dict(case=case['id'],round=round_no,product='customerB',startedAt=datetime.now(timezone.utc).isoformat(),sessionId=session['id']);started=time.perf_counter();run=None
    save(f'inflight-{case["id"]}-{round_no}.json',row)
    try:
        verify_profile_empty()
        run=api.request('POST',f'/sessions/{session["id"]}/runs',dict(requestId=uuid.uuid4().hex,message=case['message']))
        row['runId']=run['id'];events=[];marker='';deadline=time.monotonic()+100
        while time.monotonic()<deadline:
            run=api.request('GET','/runs/'+run['id']);current=run.get('status','')+':'+run.get('stage','')
            if current!=marker:events.append(dict(atMs=(time.perf_counter()-started)*1000,status=run.get('status'),stage=run.get('stage')));marker=current
            if run['status'] in TERMINAL:break
            time.sleep(.5)
        if run['status'] not in TERMINAL:run=api.request('POST','/runs/'+run['id']+'/cancel',{})
        time.sleep(.25);run=api.request('GET','/runs/'+run['id'])
        row.update(run=run,result=json.loads(run.get('result_json') or '{}'),metrics=json.loads(run.get('metrics_json') or '{}'),events=events)
        # Only steps for this newly created run; no existing history.
        raw=sql("SELECT JSON_ARRAYAGG(JSON_OBJECT('tool',tool_name,'status',status,'detail',detail_json)) FROM customer_step WHERE run_id='"+run['id']+"'")
        row['steps']=json.loads(raw) if raw and raw!='NULL' else []
    except Exception as error:row['error']=str(error)[:700]
    finally:
        row['clientWallMs']=(time.perf_counter()-started)*1000
        try:
            if run and run.get('status') not in TERMINAL:api.request('POST','/runs/'+run['id']+'/cancel',{})
            api.request('DELETE',f'/sessions/{session["id"]}');row['sessionSoftDeleted']=True
        except Exception as error:row['cleanupError']=type(error).__name__
    return row
def main():
    sys.stdout.reconfigure(encoding='utf-8');parser=argparse.ArgumentParser();parser.add_argument('--preflight-only',action='store_true');parser.add_argument('--rounds',type=int,default=2);args=parser.parse_args()
    save('cases.json',dict(cases=definitions(),rounds=args.rounds,scope='Actual current product flows; same synthetic requirements; dedicated fresh sessions; no real account profile/history. Different configured generators remain a confound.'))
    if not (OUT/'before.json').exists():save('before.json',preflight())
    if args.preflight_only:print('preflight_saved; no model call');return
    rng=random.Random(20261003)
    for round_no in range(1,args.rounds+1):
        cases=definitions();rng.shuffle(cases)
        for case in cases:
            sides=['analysisB','customerB'];rng.shuffle(sides)
            for side in sides:
                filename=f'{case["id"]}-{round_no}-{side}.json'
                if (OUT/filename).exists():continue
                row=analysis(case,round_no) if side=='analysisB' else customer(case,round_no)
                save(filename,row)
                response=row.get('response',{}).get('result',{});result=response if side=='analysisB' else row.get('result',{})
                print(json.dumps(dict(case=case['id'],round=round_no,product=side,seconds=round(row['clientWallMs']/1000,2),cards=len(result.get('recommendations',result.get('projects',[]))),status=row.get('run',{}).get('status'),error=row.get('error') or result.get('error') or row.get('run',{}).get('error_code')),ensure_ascii=False),flush=True)
    save('after.json',preflight())
if __name__=='__main__':main()
