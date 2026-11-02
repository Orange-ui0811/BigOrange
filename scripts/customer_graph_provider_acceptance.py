"""Explicit real-provider acceptance. Credentials stay in memory; reports never contain headers/keys."""
import hashlib, json, os, re, subprocess
from pathlib import Path
from customer_local_smoke import configured_environment
ROOT = Path(__file__).resolve().parents[1]

def main():
    backend = json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],text=True))[0]
    config = configured_environment()
    network = next(iter(backend['NetworkSettings']['Networks']))
    cache = next((m.get('Name',m['Source']) for m in backend['Mounts'] if m['Destination']=='/root/.m2'),None)
    # Do not carry image PATH/JAVA_HOME into a different image.
    keys = [k for k in config if k.startswith(('ORANGE_','CUSTOMER_','MYSQL_','REDIS_','AI_','RAG_','GITEE_','DEEPSEEK_','SPRING_'))]
    env = dict(os.environ, **{k:config[k] for k in keys}, RUN_CUSTOMER_GRAPH_ACCEPTANCE='true', TZ='Asia/Shanghai')
    args = ['docker','run','--rm','--name','orange-customer-graph-acceptance','--network',network,'-v',str(ROOT)+':/workspace','-w','/workspace']
    if cache: args += ['-v',cache+':/root/.m2']
    for key in keys+['RUN_CUSTOMER_GRAPH_ACCEPTANCE','TZ']: args += ['-e',key]
    args += ['maven:3.9.9-eclipse-temurin-21','mvn','-B','-pl','orange-server','-am','test','-Dtest=CustomerGraphProviderAcceptanceTest','-Dsurefire.failIfNoSpecifiedTests=false']
    folder = ROOT/'docs/customer/langgraph4j'
    manifest = {'caseSha256':hashlib.sha256((folder/'acceptance-cases.json').read_bytes()).hexdigest(),'model':config.get('CUSTOMER_MODEL'),'thinking':config.get('CUSTOMER_THINKING'),'rounds':2,'cases':20,'engines':['LEGACY','LANGGRAPH4J'],'scope':'Synthetic conditions, actual B knowledge and configured provider; no real account profile/history','releaseApproved':False}
    (folder/'provider-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Starting 80 governed runs, two concurrent; automatic criteria do not replace content review.',flush=True)
    result = subprocess.run(args,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
    output = result.stdout+result.stderr
    for key in keys:
        if re.search(r'KEY|SECRET|PASSWORD|TOKEN',key,re.I) and len(config[key])>4: output=output.replace(config[key],'[REDACTED]')
    (folder/'provider-acceptance.log').write_text(output,encoding='utf-8')
    print(json.dumps({'exitCode':result.returncode,'releaseApproved':False}))
    raise SystemExit(result.returncode)
if __name__=='__main__':main()
