"""Compare frozen synthetic customer runs; no external requests or model judging."""
import json, math, statistics, hashlib, argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def load(version):
    folder=ROOT/f'docs/customer/free-topics-{version}'
    cases=json.loads((folder/'cases.json').read_text(encoding='utf-8'))
    rows=[json.loads((folder/(c['id']+'.json')).read_text(encoding='utf-8')) for c in cases]
    return cases,rows

def metrics(rows):
    turns=[t for r in rows for t in r['turns']]
    calls=[c for t in turns for c in t['metrics'].get('modelCalls',[])]
    times=sorted(t['wallMs'] for t in turns)
    return dict(turns=len(turns),completed=sum(t['status']=='SUCCEEDED' for t in turns),
        clarificationTurns=sum(t['result'].get('status')=='NEEDS_CLARIFICATION' for t in turns),
        turnsWithProjects=sum(bool(t['result'].get('projects')) for t in turns),
        cards=sum(len(t['result'].get('projects',[])) for t in turns),
        cardsWithAdvice=sum(bool(p.get('practiceAdvice')) for t in turns for p in t['result'].get('projects',[])),
        meanMs=round(statistics.mean(times)),p95Ms=times[math.ceil(.95*len(times))-1],maxMs=max(times),
        modelCalls=len(calls),retries=sum(c.get('attempt',1)>1 for c in calls),
        inputTokens=sum(c.get('usage',{}).get('prompt_tokens',0) for c in calls),
        outputTokens=sum(c.get('usage',{}).get('completion_tokens',0) for c in calls),
        monetaryCost='UNKNOWN',embeddingUsage='NOT_REPORTED',
        phases={phase:dict(calls=len(items),durationMs=sum(c.get('durationMs',0) for c in items),
            inputTokens=sum(c.get('usage',{}).get('prompt_tokens',0) for c in items),
            outputTokens=sum(c.get('usage',{}).get('completion_tokens',0) for c in items))
            for phase in sorted(set(c.get('phase','UNKNOWN') for c in calls))
            for items in [[c for c in calls if c.get('phase','UNKNOWN')==phase]]},
        promptVersions=sorted(set(t['metrics'].get('promptVersion','unknown') for t in turns)),
        generations=sorted(set(t['result']['generation'] for t in turns if t['result'].get('generation'))))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--after',default='v413');args=parser.parse_args()
    old_cases,old=load('v412');cases,new=load(args.after);assert old_cases==cases
    compared=[]
    for before,after in zip(old,new):
        for i,(a,b) in enumerate(zip(before['turns'],after['turns'])):
            result=b['result'];state=result.get('state',{})
            compared.append(dict(case=after['case']['id'],turn=i+1,
                beforeStatus=a['result'].get('status'),afterStatus=result.get('status'),
                beforeProjects=[p['name'] for p in a['result'].get('projects',[])],
                afterProjects=[p['name'] for p in result.get('projects',[])],
                beforeMs=a['wallMs'],afterMs=b['wallMs'],
                state={k:state[k] for k in ['topic','goal','language','maxDurationDays','allowDocker','verificationMode','pendingConditions'] if k in state},
                answer=result.get('answer',''),
                cards=[dict(name=p['name'],language=p.get('language'),duration=p.get('duration'),matchStatus=p.get('matchStatus'),
                    description=p.get('description'),evidence=p.get('supportedExplanation'),advice=bool(p.get('practiceAdvice')),importable=p.get('importable')) for p in result.get('projects',[])]))
    report=dict(before=metrics(old),after=metrics(new),cases=compared,
        allSyntheticSessionsDeleted=all(r.get('sessionDeleted') for r in new),
        sourceSha256={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/f'docs/customer/free-topics-{args.after}/source').glob('*.java')},
        limitations=['Single execution per frozen input; not a stable success rate','No full-catalog relevance labels: cannot infer Recall from card counts','Purpose judge is model-based; literal quotes validate provenance, not semantic correctness','API monetary prices and embedding usage not measured'])
    (ROOT/f'docs/customer/free-topics-{args.after}/comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:report[k] for k in ['before','after','allSyntheticSessionsDeleted']},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
