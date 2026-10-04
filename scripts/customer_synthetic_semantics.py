"""Explicitly authorized synthetic model calls through actual Java protocol and validators.

No login, account profile, project query, RAG or real conversation history is used.
Credentials remain in process environment and are never printed or saved.
"""
import json
import os
import subprocess
import argparse
from customer_local_smoke import ROOT, configured_environment
from customer_semantic_continuation_live import CASES

parser = argparse.ArgumentParser()
parser.add_argument('--context-only', action='store_true')
parser.add_argument('--operations-only', action='store_true')
parser.add_argument('--goals-only', action='store_true')
args = parser.parse_args()

environment = configured_environment()
endpoint = (environment.get('AI_TEXT_ENDPOINT') or environment.get('AI_TREND_ENDPOINT', '')).rstrip('/')
if endpoint not in ('https://api.deepseek.com', 'https://api.deepseek.com/v1'):
    raise SystemExit('Expected configured official DeepSeek endpoint')
key = environment.get('AI_TEXT_KEY') or environment.get('AI_TREND_KEY')
if not key:
    raise SystemExit('Configured model key missing')
cases = [dict(id=i, message=message, expected=expected) for i, message, expected in CASES]
cases += [dict(id='C01',message='你好',expected=dict(intent='CHAT')),
          dict(id='C02',message='今天不想聊项目，我有点累，随便聊聊吧',expected=dict(intent='CHAT')),
          dict(id='C03',message='用一个简单例子解释一下Java依赖注入',expected=dict(intent='CHAT')),
          dict(id='C04',message='哈哈哈😂',expected=dict(intent='CHAT'))]
cases += [dict(id='H01',message='不用满半年，六十天以内就行，其他条件不变',prior=dict(language='Java',maxDurationDays=180),expected=dict(language='Java',maxDurationDays=60)),
          dict(id='H02',message='取消Docker限制，语言还是原来的',prior=dict(language='Java',allowDocker=False),expected=dict(language='Java',absent='allowDocker')),
          dict(id='H03',message='不要比两个月短，也不要超过三个月',expected=dict(minDurationDays=60,maxDurationDays=90)),
          dict(id='H04',message='我希望用Golang，工期至多六个星期，先记录',expected=dict(language='Go',maxDurationDays=42)),
          dict(id='H05',message='数据库我已经装好了可以用，但不接受Docker，先记录',expected=dict(allowDatabase=True,allowDocker=False)),
          dict(id='H06',message='工期改成两周，其他要求都保留',prior=dict(language='Python',allowDocker=False,maxDurationDays=180),expected=dict(language='Python',allowDocker=False,maxDurationDays=14)),
          dict(id='H07',message='语言不限，保留不用Docker',prior=dict(language='Java',allowDocker=False),expected=dict(absent='language',allowDocker=False)),
          dict(id='H08',message='谢谢你，不用继续推荐了',prior=dict(language='Java',maxDurationDays=180),expected=dict(intent='CHAT',language='Java',maxDurationDays=180))]
report = 'customer-v4-synthetic'
if args.context_only:
    report = 'customer-v41-context-synthetic'
    candidates = [dict(type='BEGINNER_PROJECT', id=i, name=f'synthetic/project-{i}') for i in (1, 2, 3)]
    cases = [
        dict(id='M01',message='刚学',prior=dict(language='Java',lastQuestion=dict(text='你的基础怎么样？',runId='synthetic')),expected=dict(language='Java',experience='刚学')),
        dict(id='M02',message='半年以内就行',prior=dict(language='Java',lastQuestion=dict(text='你希望多长时间完成？',runId='synthetic')),expected=dict(language='Java',maxDurationDays=180)),
        dict(id='M03',message='比较第一个和第三个',prior=dict(lastCandidates=candidates),expected=dict(intent='COMPARE',positions=[1,3])),
        dict(id='M04',message='第二个怎么部署',prior=dict(lastCandidates=candidates),expected=dict(intent='DETAIL',positions=[2])),
        dict(id='M05',message='先推荐吧，其他条件我还没想好',prior=dict(language='Java'),expected=dict(intent='RECOMMEND',language='Java')),
        dict(id='M06',message='今天先随便聊聊，暂时不要问项目的事',prior=dict(language='Java',lastQuestion=dict(text='你的基础怎么样？',runId='synthetic')),expected=dict(intent='CHAT',language='Java')),
    ]
if args.operations_only:
    report='customer-v42-operations-synthetic'
    cases=[
        dict(id='O01',message='不再限定“刚学”对应的难度',prior=dict(language='Java',experience='刚学'),expected=dict(absent='difficulty',language='Java',experience='刚学',noPending=True)),
        dict(id='O02',message='把周期放宽到半年',prior=dict(language='Java',maxDurationDays=60),expected=dict(language='Java',maxDurationDays=180,noPending=True)),
        dict(id='O03',message='当前有什么条件',prior=dict(language='Java',maxDurationDays=180),expected=dict(intent='STATE',language='Java',maxDurationDays=180)),
        dict(id='O04',message='为什么没有可以推荐的项目？放宽哪些条件才有？',prior=dict(language='Java',maxDurationDays=7),expected=dict(intent='DIAGNOSE',language='Java',maxDurationDays=7)),
        dict(id='O05',message='只看学习项目专区',expected=dict(sourceType='BEGINNER_PROJECT',noPending=True)),
        dict(id='O06',message='想通过项目学习Java，不限来自哪个分类',expected=dict(language='Java',goal='学习',absent='sourceType',noPending=True)),
        dict(id='O07',message='项目难度不限',prior=dict(difficulty='初级',experience='刚学'),expected=dict(absent='difficulty',experience='刚学',noPending=True)),
        dict(id='O08',message='不要取消Java语言限制，只去掉难度限制',prior=dict(language='Java',difficulty='初级'),expected=dict(language='Java',absent='difficulty',noPending=True)),
    ]
if args.goals_only:
    report='customer-v44-goals-synthetic'
    cases=[
        dict(id='G01',message='既想练手学习，也想准备找工作的作品',expected=dict(goal=['学习','求职'],noPending=True)),
        dict(id='G02',message='还想挣点外快接些外包单，原来的目标也保留，只记录不用推荐',prior=dict(goal='学习',language='Java',maxDurationDays=60),expected=dict(goal=['学习','赚钱/接单/创业'],language='Java',maxDurationDays=60,noPending=True)),
        dict(id='G03',message='求职这个目标先不考虑，学习和给家里的店做库存管理这两个保留',prior=dict(goal=['学习','求职','给家里的店做库存管理'],language='Java'),expected=dict(goal=['学习','给家里的店做库存管理'],language='Java',noPending=True)),
        dict(id='G04',message='我做这个项目是为了给家里的店做库存管理',expected=dict(goal='给家里的店做库存管理',noPending=True)),
        dict(id='G05',message='我就想探索技术兴趣，同时为求职做准备，可以同时选这两个目标吗？',expected=dict(goal=['探索技术兴趣','求职'],noPending=True)),
        dict(id='G06',message='请将项目目标设置为：学习；求职；给家里的店做库存管理。其他条件保持不变，先记录目标，不用推荐。',prior=dict(language='Java',maxDurationDays=60),expected=dict(goal=['学习','求职','给家里的店做库存管理'],language='Java',maxDurationDays=60,intent='CHAT',noPending=True)),
    ]
(ROOT/f'docs/customer/acceptance/{report}-cases.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2),encoding='utf-8')
env = os.environ.copy()
env.update(RUN_CUSTOMER_SYNTHETIC='true', CUSTOMER_PROBE_ENDPOINT=endpoint, CUSTOMER_PROBE_KEY=key, CUSTOMER_SYNTHETIC_REPORT=report)
with (ROOT/f'docs/customer/acceptance/{report}.log').open('w',encoding='utf-8') as log:
    result = subprocess.run(['mvn.cmd','-B','-pl','orange-server','-am','-Dtest=CustomerSyntheticProviderIntegrationTest','-Dsurefire.failIfNoSpecifiedTests=false','test'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
print('Synthetic Java/provider test exit:',result.returncode)
raise SystemExit(result.returncode)
