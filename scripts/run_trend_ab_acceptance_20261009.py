"""Run only the opt-in service-level A/B test. No token forging or account access."""
import hashlib,json,os,re,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-ab-20261009'

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    backend=json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],encoding='utf-8'))[0]
    config=dict(v.split('=',1) for v in backend['Config']['Env'] if '=' in v)
    keys=[k for k in config if k.startswith(('ORANGE_','CUSTOMER_','MYSQL_','REDIS_','AI_','RAG_','GITEE_','DEEPSEEK_','SPRING_'))]
    env=dict(os.environ,**{k:config[k] for k in keys},RUN_TREND_AB_ACCEPTANCE='true',TZ='Asia/Shanghai',SPRING_PROFILES_ACTIVE='dev')
    network=next(iter(backend['NetworkSettings']['Networks']))
    cache=next((m.get('Name',m['Source']) for m in backend['Mounts'] if m['Destination']=='/root/.m2'),None)
    args=['docker','run','--rm','--name','orange-trend-ab-20261009','--network',network,'-v',str(ROOT)+':/workspace','-w','/workspace']
    if cache:args+=['-v',cache+':/root/.m2']
    for k in sorted(set(keys+['RUN_TREND_AB_ACCEPTANCE','TZ','SPRING_PROFILES_ACTIVE'])):args+=['-e',k]
    args+=['maven:3.9.9-eclipse-temurin-21','mvn','-B','-pl','orange-server','-am','test',
           '-Dtest=TrendABProviderAcceptanceTest','-Dsurefire.failIfNoSpecifiedTests=false']
    source=ROOT/'orange-server/src/main/java/com/orangejd/server/service'
    files=['TrendV2Analyzer.java','TrendRagABPreviewService.java','TrendContextBuilder.java','TrendPromptFactory.java','TrendModelClient.java']
    manifest={'authorizedGenerationRequests':32,'endpoint':'https://ai.gitee.com/v1/chat/completions','model':'DeepSeek-V4-Pro',
      'scope':'Actual analysis retrieval/generation/parsing/card binding, synthetic profiles. No HTTP authentication, router retries, quota or history tests. Model call at most once per record.',
      'codeSha256':{f:hashlib.sha256((source/f).read_bytes()).hexdigest() for f in files if (source/f).exists()}}
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    secrets=[v for k,v in config.items() if re.search(r'KEY|SECRET|PASSWORD|TOKEN',k,re.I) and len(v)>4]
    with (OUT/'run.log').open('a',encoding='utf-8') as log:
        process=subprocess.Popen(args,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
        for line in process.stdout:
            for secret in secrets:line=line.replace(secret,'[REDACTED]')
            log.write(line);log.flush()
            if any(t in line for t in ['TREND_AB_COMPLETED','BUILD SUCCESS','BUILD FAILURE','[ERROR]']):print(line.rstrip(),flush=True)
        code=process.wait()
    print(json.dumps({'exitCode':code}),flush=True)
    sys.exit(code)

if __name__=='__main__':main()
