"""Local-only M3 benchmark, isolated rebuild runner and backend configuration. No secret output."""
import argparse, concurrent.futures, hashlib, json, math, os, re, subprocess, time, urllib.request
from pathlib import Path
from customer_local_smoke import configured_environment

ROOT=Path(__file__).resolve().parents[1]
REPORT=ROOT/'docs/rag-b/local-m3-20261008'
BASE='http://127.0.0.1:11434'
MODEL='bge-m3:orangejd-v1'

def api(path,body=None):
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=60) as response:return json.load(response)

def configuration():
    tag=next(m for m in api('/api/tags')['models'] if m['name']==MODEL)
    digest='sha256:'+tag['digest'].removeprefix('sha256:')
    return {'RAG_B_EMBEDDING_PROVIDER':'OLLAMA','RAG_B_EMBEDDING_REVISION':digest,
            'RAG_B_OLLAMA_BASE_URL':'http://host.docker.internal:11434','RAG_B_OLLAMA_MODEL':MODEL,
            'RAG_B_EMBEDDING_BATCH_SIZE':'4','RAG_B_ENABLED':'true'}

def embed(texts):return api('/api/embed',{'model':MODEL,'input':texts,'truncate':False,'keep_alive':-1})['embeddings']

def percentile(values,p):
    values=sorted(values);return round(values[min(len(values)-1,math.ceil(len(values)*p)-1)],2)

def benchmark():
    config=configuration();warm=time.perf_counter();embed(['本地模型预热']);warm=(time.perf_counter()-warm)*1000
    queries=['Python RAG 求职项目','Java Spring Boot 学习项目','AI 应用开发 智能体','Vue 前端项目','Docker 部署要求',
        '项目数据库依赖','知识库问答系统','多语言项目文档','半年内完成的练手项目','不需要 Docker 的项目']*2
    def call(text):
        start=time.perf_counter();vector=embed([text])[0]
        if len(vector)!=1024 or not all(math.isfinite(v) for v in vector):raise RuntimeError('Invalid local vector')
        return (time.perf_counter()-start)*1000
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:times=list(pool.map(call,queries))
    # Compare exact stored texts to their existing cloud vectors; do not print or persist corpus text/vectors.
    from customer_local_smoke import sql
    rows=sql("SELECT gc.vector_key FROM rag_b_generation_chunk gc JOIN rag_b_index_generation g ON g.id=gc.generation_id WHERE g.id=3 ORDER BY gc.chunk_id LIMIT 12").splitlines()
    similarities=[]
    for key in rows:
        doc=json.loads(subprocess.check_output(['docker','exec','nxz-bigorange-redis-1','redis-cli','--raw','JSON.GET',key],text=True,encoding='utf-8'))
        local=embed([doc['content']])[0];old=doc['embedding']
        cosine=sum(a*b for a,b in zip(local,old))/math.sqrt(sum(a*a for a in local)*sum(b*b for b in old))
        similarities.append({'chunkId':doc['chunkId'],'cosine':cosine,'textSha256':hashlib.sha256(doc['content'].encode()).hexdigest()})
    result={'model':MODEL,'modelRevision':config['RAG_B_EMBEDDING_REVISION'],'concurrency':2,'requests':len(times),
        'warmupMs':round(warm,2),'p50Ms':percentile(times,.5),'p95Ms':percentile(times,.95),'maxMs':round(max(times),2),
        'dimensions':1024,'cloudVectorComparison':similarities,'indexDecision':'Create separate local generation; no silent vector mixing',
        'price':'No external Embedding requests; hardware/electricity costs not measured'}
    (REPORT/'benchmark.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='cloudVectorComparison'},ensure_ascii=True),flush=True)
    print(json.dumps({'comparisonCount':len(similarities),'minCosine':min(r['cosine'] for r in similarities)}),flush=True)

def rebuild(regression=False, verification=False):
    original=configured_environment();overrides=configuration()|{'RUN_RAG_B_LOCAL_ACCEPTANCE':'false' if regression else 'true','TZ':'Asia/Shanghai'}
    backend=json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],text=True))[0]
    network=next(iter(backend['NetworkSettings']['Networks']))
    cache=next(m.get('Name',m['Source']) for m in backend['Mounts'] if m['Destination']=='/root/.m2')
    selected={k:v for k,v in original.items() if k.startswith(('ORANGE_','CUSTOMER_','MYSQL_','REDIS_','AI_','RAG_','GITEE_','SPRING_'))}
    env=dict(os.environ)|selected|overrides
    args=['docker','run','--rm','--name','orange-local-m3-regression' if regression else 'orange-local-m3-acceptance','--network',network,
        '-v',str(ROOT)+':/workspace','-v',cache+':/root/.m2','-w','/workspace']
    for key in selected.keys()|overrides.keys():args+=['-e',key]
    args+=['maven:3.9.9-eclipse-temurin-21','mvn','-B','-pl','orange-server','-am','test',
        '-Dtest=Customer*Test,RagB*Test,ArchitectureGovernanceTest' if regression else '-Dtest=RagBLocalModelAcceptanceTest',
        '-Dsurefire.failIfNoSpecifiedTests=false', '-Dlocal.m3.report-dir='+REPORT.relative_to(ROOT).as_posix()]
    if verification:args+=['-Dlocal.m3.verify-only=true']
    result=subprocess.run(args,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
    log=result.stdout+result.stderr
    for key,value in selected.items():
        if any(w in key for w in ('KEY','SECRET','PASSWORD','TOKEN')) and len(value)>4:log=log.replace(value,'[REDACTED]')
    phase='verification' if verification else ('regression' if regression else 'integration')
    (REPORT/(phase+'.log')).write_text(log,encoding='utf-8')
    (REPORT/(phase+'-execution.json')).write_text(json.dumps({'exitCode':result.returncode,'externalModelCalls':0},indent=2),encoding='utf-8')
    print(json.dumps({'exitCode':result.returncode,'report':str(REPORT)}),flush=True)
    raise SystemExit(result.returncode)

def start_backend():
    config=configuration();original=configured_environment()
    from customer_local_smoke import sql
    active=sql('SELECT embedding_provider,embedding_revision,status FROM rag_b_index_generation WHERE active=1').split('\t')
    if active != ['OLLAMA',config['RAG_B_EMBEDDING_REVISION'],'ACTIVE']:
        raise RuntimeError('Matching local generation must finish and activate before backend configuration changes')
    filename=ROOT/'.env.local';lines=filename.read_text(encoding='utf-8-sig').splitlines() if filename.exists() else []
    lines=[line for line in lines if line.split('=',1)[0] not in config]
    filename.write_text('\n'.join(lines+['# Local M3 provider; old generations retain their bound provider.']+[k+'='+v for k,v in config.items()])+'\n',encoding='utf-8')
    compose_keys=set(re.findall(r'\$\{([A-Z][A-Z0-9_]*)', (ROOT/'compose.local.yml').read_text(encoding='utf-8')))
    preserved={k:v for k,v in original.items() if k in compose_keys}
    env=dict(os.environ)|preserved|config
    result=subprocess.run(['docker','compose','--env-file','.env.local','-f','compose.local.yml','up','-d','--no-deps','backend'],cwd=ROOT,env=env,capture_output=True,text=True)
    if result.returncode:raise RuntimeError('Backend startup failed; credentials withheld')
    print('Backend recreated with local B provider; original model keys and customer engine preserved.',flush=True)

def main():
    global REPORT
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['benchmark','rebuild','verify','regression','start-backend'])
    parser.add_argument('--report-dir',default=str(REPORT.relative_to(ROOT)));args=parser.parse_args()
    REPORT=(ROOT/args.report_dir).resolve()
    if not REPORT.is_relative_to(ROOT/'docs/rag-b'):raise ValueError('Reports must remain in docs/rag-b')
    REPORT.mkdir(parents=True,exist_ok=True)
    {'benchmark':benchmark,'rebuild':rebuild,'verify':lambda:rebuild(verification=True),'regression':lambda:rebuild(True),'start-backend':start_backend}[args.phase]()

if __name__=='__main__':main()
