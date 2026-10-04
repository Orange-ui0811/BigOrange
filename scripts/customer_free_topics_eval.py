"""Frozen free-topic evaluation: synthetic input, fresh sessions, no production changes."""
import argparse
import json
import sys
import time
import uuid
from pathlib import Path
from customer_local_smoke import authorized_token, sql
from run_customer_cases import Api, TERMINAL
from run_analysis_customer_b_compare import verify_profile_empty

OUT = Path(__file__).resolve().parents[1] / 'docs/customer/free-topics-v412'
PAIRS = [
    ('data', '数据处理与可视化', '数据分析'),
    ('office', '办公自动化', '工作流自动化'),
    ('browser', '让程序帮我打开网页、点击按钮和填写表单', '浏览器自动化'),
    ('web', 'Web应用开发', 'Next.js'),
    ('frontend', '做一个网站的前端界面', 'Vue'),
    ('inventory', '帮小店管理进货、卖货和库存', '库存管理'),
]
CASES = [dict(id=f'{key}_{kind}', family=key, kind=kind, messages=[
    f'请推荐真实项目，想做的方向或用途是：{topic}。目标是学习，我有一年编程基础，语言和周期都不限。资料缺失可以先展示相关探索项目，但不要推荐无关项目。'
]) for key,broad,specific in PAIRS for kind,topic in [('free',broad),('specific',specific)]]
CASES += [
    dict(id='hard_java_browser',family='boundary',kind='hard',messages=['推荐浏览器自动化项目，只能使用Java，不能使用Docker。没有符合的就提出调整建议，必须等我同意才能修改条件；资料未知可以明确标记未知。']),
    dict(id='unknown_quantum',family='boundary',kind='absent',messages=['推荐量子纠错芯片流片项目。只能推荐有资料核实符合的真实项目，没有就说明，不要改成其他方向。']),
    dict(id='dialogue_scope',family='dialogue',kind='multi_turn',messages=[
        '先不要推荐，记录我的需求：我想做Web应用开发，目标是求职，有Vue基础，半年内完成，语言不限。',
        '现在给我推荐项目吧。',
        '改成Vue方向，时间缩短为两个月，其他条件保持不变，推荐项目。',
    ]),
]


def save(path, value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')


def execute(api, session, message):
    start=time.monotonic()
    run=api.request('POST',f'/sessions/{session}/runs',{'requestId':str(uuid.uuid4()),'message':message})
    while run['status'] not in TERMINAL and time.monotonic()-start<100:
        time.sleep(.8)
        run=api.request('GET','/runs/'+run['id'])
    if run['status'] not in TERMINAL:
        run=api.request('POST','/runs/'+run['id']+'/cancel',{})
    time.sleep(.15)
    run=api.request('GET','/runs/'+run['id'])
    assert uuid.UUID(run['id'])
    steps=sql("SELECT JSON_OBJECT('tool',tool_name,'status',status,'detail',CAST(detail_json AS JSON)) FROM customer_step WHERE run_id='"+run['id']+"' ORDER BY id")
    return dict(message=message,runId=run['id'],status=run['status'],error=run.get('error_code'),
                wallMs=round((time.monotonic()-start)*1000),result=json.loads(run.get('result_json') or '{}'),
                metrics=json.loads(run.get('metrics_json') or '{}'),steps=[json.loads(line) for line in steps.splitlines() if line])


def main():
    global OUT
    parser=argparse.ArgumentParser()
    parser.add_argument('--output', default=str(OUT))
    args=parser.parse_args()
    OUT=Path(args.output).resolve()
    sys.stdout.reconfigure(encoding='utf-8')
    OUT.mkdir(parents=True,exist_ok=True)
    definitions=OUT/'cases.json'
    if definitions.exists():
        assert json.loads(definitions.read_text(encoding='utf-8'))==CASES
    else:
        save(definitions,CASES)
    verify_profile_empty()
    api=Api('http://127.0.0.1:18080',authorized_token(1))
    for case in CASES:
        path=OUT/(case['id']+'.json')
        if path.exists():
            print(case['id']+': saved result retained',flush=True)
            continue
        verify_profile_empty()
        session=api.request('POST','/sessions',{'title':'自由主题合成验收 '+case['id']})
        row=dict(case=case,sessionId=session['id'],turns=[])
        try:
            for message in case['messages']:
                turn=execute(api,session['id'],message)
                row['turns'].append(turn)
                save(OUT/(case['id']+'.partial.json'),row)
                result=turn['result'];cards=result.get('projects',[])
                print(json.dumps(dict(case=case['id'],turn=len(row['turns']),status=turn['status'],result=result.get('status'),
                    topic=result.get('state',{}).get('topic'),projects=len(cards),names=[p['name'] for p in cards],ms=turn['wallMs']),ensure_ascii=False),flush=True)
        finally:
            active=api.request('GET',f"/sessions/{session['id']}/messages").get('activeRun')
            if active:api.request('POST','/runs/'+active['id']+'/cancel',{})
            api.request('DELETE',f"/sessions/{session['id']}")
            row['sessionDeleted']=True
            save(path,row)


if __name__=='__main__':
    main()
