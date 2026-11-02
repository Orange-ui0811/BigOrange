"""Summarize only the synthetic acceptance run IDs; never export raw model payloads."""
import collections, hashlib, json, math, re
from pathlib import Path
from customer_local_smoke import sql
ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'docs/customer/langgraph4j'

def main():
    data=json.loads((FOLDER/'provider-acceptance.json').read_text(encoding='utf-8'));rows=data['rows']
    ids=[r['runId'] for r in rows]
    if not all(re.fullmatch(r'[a-f0-9-]{36}',x) for x in ids):raise ValueError('Invalid run ID')
    selected=','.join("'"+x+"'" for x in ids)
    query="SELECT JSON_OBJECT('runId',run_id,'tool',tool_name,'status',status,'errorCode',JSON_UNQUOTE(JSON_EXTRACT(detail_json,'$.errorCode')),'errorType',JSON_UNQUOTE(JSON_EXTRACT(detail_json,'$.errorType'))) FROM customer_step WHERE run_id IN ("+selected+") AND status IN ('FAILED','DEGRADED','RETRY_SUPPRESSED')"
    steps=[json.loads(line) for line in sql(query).splitlines() if line.strip()]
    outcomes={}
    for engine in ['LEGACY','LANGGRAPH4J']:
        own=[r for r in rows if r['engine']==engine];durations=sorted(r['durationMs'] for r in own)
        attempts=[a for r in own for a in r['usage'].get('modelCalls',[])]
        ownids={r['runId'] for r in own};bad=[s for s in steps if s['runId'] in ownids]
        retrieval={'searchKnowledge','knowledge','recommendationRecall','projectEvidence'}
        outcomes[engine]={'runs':len(own),'automaticPasses':sum(r['passed'] for r in own),'terminalStatuses':dict(collections.Counter(r['runStatus'] for r in own)),
          'p95Ms':durations[math.ceil(.95*len(durations))-1],'maxMs':max(durations),'modelAttempts':len(attempts),
          'failedAttempts':sum(a['status']=='FAILED' for a in attempts),'retryAttempts':sum(a.get('attempt',1)>1 for a in attempts),
          'reportedTotalTokens':sum(r['usage'].get('tokenUsage',{}).get('totalTokens',0) for r in own),
          'attemptsWithoutUsage':sum(r['usage'].get('tokenUsage',{}).get('callsWithoutUsage',0) for r in own),
          'runsWithRetrievalFailures':len({s['runId'] for s in bad if s['tool'] in retrieval}),
          'degradedStepCounts':dict(collections.Counter(s['tool'] for s in bad)),
          'costUsd':'UNKNOWN','embeddingUsage':'NOT_REPORTED'}
    summary={'releaseApproved':False,'automaticCriteriaFrozen':True,'results':outcomes,'limitations':['Embedding 429/timeouts mean successful recommendations may be degraded catalog results, not successful B retrieval.','G14 goal-vs-topic criterion needs human review; raw automatic score is unchanged.','No price configured; unknown costs are not zero.','True administrator browser/raw-access flow and server rollout/24h observation remain pending.'],'failures':[{k:r.get(k) for k in ['caseId','engine','round','status','intent','state','projectCount','durationMs','error']} for r in rows if not r['passed']],'degradedSteps':steps}
    (FOLDER/'provider-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    classes=ROOT/'orange-server/target/classes/com/orangejd/server/customer'
    (FOLDER/'provider-compiled-manifest.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in classes.glob('*.class')},indent=2),encoding='utf-8')
    print(json.dumps(outcomes,ensure_ascii=True))
if __name__=='__main__':main()
