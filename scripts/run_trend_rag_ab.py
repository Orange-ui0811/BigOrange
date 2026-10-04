"""Paired full-generation A/B, synthetic profiles, dev-only admin endpoint; no account history/quota."""
import argparse,json,sys,time,subprocess,re,hashlib
from pathlib import Path
from datetime import datetime,timezone
from run_rag_b_first_round import call
from customer_local_smoke import sql

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-ab-20261003'
CASES=[
    ('spring_beginner',['Spring Boot'],'学习','刚学',['Java'],'60天内','只推荐 Java 项目，从能运行的小功能开始'),
    ('rag_career',['RAG','知识库'],'求职','有一年基础',['Java'],'半年内','希望做一个能写进简历的知识库问答项目'),
    ('rag_python',['RAG'],'学习','有基础',['Python'],'两个月内','中文提问，检索英文技术文档，说明部署依赖'),
    ('agent_business',['Agent','智能体'],'赚钱/接单/创业','有两年基础',['TypeScript'],'三个月内','想改造成服务小团队的工具，商业价值是建议，不能承诺收益'),
    ('vue_learning',['Vue'],'学习','刚学',['JavaScript'],'一个月内','做一个能演示的前端项目，优先容易运行的例子'),
    ('data_analysis',['数据分析'],'求职','有基础',['Python'],'两个月内','想展示数据处理和可视化能力'),
    ('printing_explore',['3D打印'],'探索技术兴趣','有基础',[],'三个月内','想了解开源切片或打印控制项目，说明硬件依赖'),
    ('unavailable',['量子纠错芯片流片'],'探索技术兴趣','刚学',['Java'],'两天内','只能推荐能核实符合条件的现有仓库，找不到请说明，不编造'),
]

def snapshot():
    status=call('GET','/admin/rag-lab/status')
    models=call('GET','/admin/ai-config')
    safe={k:{field:v.get(field) for field in ['endpoint','model','version','source']} for k,v in models.items() if isinstance(v,dict)}
    stats={}
    for side,index in [('A','default-index'),('B','orange-rag-b-'+status['activeIndex']['generation'])]:
        raw=subprocess.check_output(['docker','exec','nxz-bigorange-redis-1','redis-cli','--json','FT.INFO',index],text=True)
        data=json.loads(re.sub(r'(?<=:)\s*(?:nan|-?inf)(?=\s*[,}])','null',raw))
        fields=data if isinstance(data,dict) else dict(zip(data[::2],data[1::2]))
        stats[side]={'index':index,**{k:fields.get(k) for k in ['num_docs','vector_index_sz_mb','total_index_memory_sz_mb','hash_indexing_failures','indexing']}}
    return dict(at=datetime.now(timezone.utc).isoformat(),rag=status,models=safe,indexes=stats,
        documentsA=sql('SELECT source,vector_status,COUNT(*) FROM knowledge_document GROUP BY source,vector_status'),
        documentsB=sql('SELECT source_type,status,COUNT(*) FROM rag_b_document WHERE deleted_at IS NULL GROUP BY source_type,status'),
        generation=sql('SELECT generation_key,document_count,chunk_count,embedding_model,chunk_policy FROM rag_b_index_generation WHERE active=1'))

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser();parser.add_argument('--rounds',type=int,default=2);parser.add_argument('--case',default='');parser.add_argument('--snapshot-only',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    if not (OUT/'before.json').exists():
        (OUT/'before.json').write_text(json.dumps(snapshot(),ensure_ascii=False,indent=2),encoding='utf-8')
    if args.snapshot_only:return
    profiles=[]
    for key,topics,purpose,experience,languages,duration,custom in CASES:
        if args.case and key!=args.case:continue
        profiles.append((key,dict(topics=topics,purpose=purpose,experience=experience,languages=languages,timeCommitment=duration,
            customPurpose=custom,useRag=True,usePersona=False,preferBeginnerProjects=purpose=='学习',preferCareerProjects=purpose=='求职')))
    (OUT/'cases.json').write_text(json.dumps(dict(cases=profiles,rounds=args.rounds,
        scope='Same generator, prompt, graph/business services, 3 evidence projects and 1100 chars. B adapter uses native top8 per query. AB/BA alternated. No external judge.'),ensure_ascii=False,indent=2),encoding='utf-8')
    for round_no in range(1,args.rounds+1):
        for index,(key,profile) in enumerate(profiles):
            variants=['A','B'] if (index+round_no)%2 else ['B','A']
            for variant in variants:
                path=OUT/f'{key}-{round_no}-{variant}.json'
                if path.exists():continue
                row=dict(case=key,round=round_no,variant=variant,profile=profile,startedAt=datetime.now(timezone.utc).isoformat())
                started=time.perf_counter()
                try:
                    row['response']=call('POST','/admin/rag-lab/compare/platform-analysis',dict(variant=variant,profile=profile,generate=True),timeout=210)
                except Exception as error:
                    row['error']=str(error)[:500]
                row['clientWallMs']=(time.perf_counter()-started)*1000
                path.write_text(json.dumps(row,ensure_ascii=False,indent=2),encoding='utf-8')
                result=row.get('response',{}).get('result',{})
                print(json.dumps(dict(case=key,round=round_no,variant=variant,seconds=round(row['clientWallMs']/1000,2),
                    recommendations=len(result.get('recommendations',[])),error=row.get('error') or result.get('error'),
                    metrics=result.get('modelMetrics',{})),ensure_ascii=False),flush=True)
    (OUT/'after.json').write_text(json.dumps(snapshot(),ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':main()
