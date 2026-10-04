"""Local dev lab: stage tracing, frozen prompt repeats and knowledge ablation.
All inputs are synthetic. No account history/quota, no global configuration mutation.
"""
import json,sys,time,hashlib,random,argparse
from pathlib import Path
from datetime import datetime,timezone
from run_rag_b_first_round import call
from run_trend_rag_ab import CASES,snapshot

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-cause-20261003'
KEYS=['spring_beginner','rag_python','data_analysis','unavailable']
def digest(body):return hashlib.sha256(json.dumps(body,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def save(name,value): (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def invoke(name,path,body,meta):
    target=OUT/(name+'.json')
    if target.exists():return json.loads(target.read_text(encoding='utf-8'))
    row={**meta,'startedAt':datetime.now(timezone.utc).isoformat()};started=time.perf_counter()
    try:
        row['response']=call('POST',path,body,timeout=210)
        if row['response'].get('modelRequest'):row['requestHash']=digest(row['response']['modelRequest'])
    except Exception as error:row['error']=str(error)[:700]
    row['clientWallMs']=(time.perf_counter()-started)*1000
    save(target.name,row)
    metrics=row.get('response',{}).get('modelMetrics') or row.get('response',{}).get('result',{}).get('modelMetrics',{})
    print(json.dumps({'name':name,'seconds':round(row['clientWallMs']/1000,2),'model':metrics.get('model'),'usage':metrics.get('usage'),'error':row.get('error') or row.get('response',{}).get('result',{}).get('error')},ensure_ascii=False),flush=True)
    return row
def main():
    sys.stdout.reconfigure(encoding='utf-8');OUT.mkdir(parents=True,exist_ok=True)
    parser=argparse.ArgumentParser();parser.add_argument('--retrieval-only',action='store_true');args=parser.parse_args()
    if not (OUT/'before.json').exists():save('before.json',snapshot())
    profiles={}
    for key,topics,purpose,experience,languages,duration,custom in CASES:
        if key not in KEYS:continue
        profiles[key]=dict(topics=topics,purpose=purpose,experience=experience,languages=languages,timeCommitment=duration,customPurpose=custom,useRag=True,usePersona=False,preferBeginnerProjects=purpose=='学习',preferCareerProjects=purpose=='求职')
    rng=random.Random(20261003)
    if args.retrieval_only:
        for repeat in range(3):
            pairs=[(key,side) for key in KEYS for side in ['A','B']];rng.shuffle(pairs)
            for key,side in pairs:invoke(f'dry-{key}-{repeat}-{side}','/admin/rag-lab/compare/platform-analysis',dict(variant=side,profile=profiles[key],generate=False,observe=True),dict(case=key,variant=side,phase='retrieval',repeat=repeat))
        return
    captures={}
    pairs=[(key,side) for key in KEYS for side in ['A','B']];rng.shuffle(pairs)
    for key,side in pairs:
        row=invoke(f'full-{key}-0-{side}','/admin/rag-lab/compare/platform-analysis',dict(variant=side,profile=profiles[key],generate=True,observe=True),dict(case=key,variant=side,phase='full',repeat=0))
        if not row.get('response',{}).get('snapshotId'):raise RuntimeError('Capture missing: '+key+side)
        captures[key,side]=row
    schedule=[]
    for repeat in [1,2]:
        pairs=[(key,side,False) for key in KEYS for side in ['A','B']]+[(key,'A',True) for key in KEYS]
        rng.shuffle(pairs)
        for key,side,removed in pairs:schedule.append(dict(case=key,variant=side,removeKnowledge=removed,repeat=repeat))
    save('schedule.json',schedule)
    for entry in schedule:
        key=entry['case'];side=entry['variant'];removed=entry['removeKnowledge'];repeat=entry['repeat']
        capture=captures[key,side]['response']
        row=invoke(f'{"no-knowledge" if removed else "replay"}-{key}-{repeat}-{side}','/admin/rag-lab/compare/platform-analysis/replay',dict(snapshotId=capture['snapshotId'],removeKnowledge=removed),dict(**entry,phase='no-knowledge' if removed else 'replay'))
        if not removed and row.get('response') and row.get('requestHash')!=captures[key,side]['requestHash']:raise RuntimeError('Replay payload changed')
    save('after.json',snapshot())
if __name__=='__main__':main()
