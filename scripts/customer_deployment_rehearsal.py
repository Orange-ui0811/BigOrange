"""Local immutable-JAR deployment rehearsal. Separate DB/Redis, internal network, no provider keys.
Does not modify the live Compose environment, acquire auth tokens, restore business data or deploy remotely.
"""
import argparse, hashlib, json, os, pathlib, secrets, subprocess, time

ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE=ROOT/'experiment-staging/customer-final-20261008'
REPORT=ROOT/'docs/customer/langgraph4j/final-verification-20261008/deployment'
NETWORK='orange-customer-deployment-rehearsal'
MYSQL='orange-customer-deployment-mysql'
REDIS='orange-customer-deployment-redis'

def run(args, **kwargs):
    return subprocess.run(args,capture_output=True,text=True,encoding='utf-8',errors='replace',**kwargs)

def save(name,value):
    (REPORT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def build():
    backend=json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],text=True))[0]
    cache=next(m.get('Name',m['Source']) for m in backend['Mounts'] if m['Destination']=='/root/.m2')
    artifacts={}
    for version in ('previous','candidate'):
        folder=BASE/version
        result=run(['docker','run','--rm','--network','none','-v',str(folder)+':/workspace',
            '-v',cache+':/root/.m2','-w','/workspace','maven:3.9.9-eclipse-temurin-21',
            'mvn','-o','-B','clean','package','-DskipTests'])
        (REPORT/(version+'-build.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
        if result.returncode:raise RuntimeError(version.upper()+'_BUILD_FAILED')
        jar=folder/'orange-server/target/orange-server-1.0.0-SNAPSHOT.jar'
        if not jar.exists():raise RuntimeError('NO_DEPLOYABLE_JAR')
        artifacts[version]={'path':jar.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'bytes':jar.stat().st_size}
        print(json.dumps({'built':version,**artifacts[version]}),flush=True)
    save('artifacts.json',artifacts)

def frontend_build():
    manifests={}
    for version in ('previous','candidate'):
        webroot=BASE/version/'web-dist';webroot.mkdir(exist_ok=True)
        for service,module,sub in [('web','orange-web',''),('admin','orange-admin','admin')]:
            inspected=json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-'+service+'-1'],text=True))[0]
            modules=next(m.get('Name',m['Source']) for m in inspected['Mounts'] if m['Destination']=='/app/node_modules')
            folder=BASE/version/module
            # Existing lockfile and installed dependency tree must match before reusing cached modules.
            if (folder/'package-lock.json').read_bytes()!=(ROOT/module/'package-lock.json').read_bytes():
                raise RuntimeError('FRONTEND_LOCKFILE_CHANGED')
            result=run(['docker','run','--rm','--network','none','-v',str(folder)+':/app',
                '-v',modules+':/app/node_modules:ro','-w','/app','-e','VITE_API_BASE_URL=/api',
                'node:22-alpine','npm','run','build','--','--configLoader','runner'])
            log=REPORT/(version+'-'+service+'-build.log')
            if log.exists():log.rename(log.with_suffix('.initial.log'))
            log.write_text(result.stdout+result.stderr,encoding='utf-8')
            result.check_returncode()
            import shutil
            destination=webroot/sub;destination.mkdir(parents=True,exist_ok=True)
            shutil.copytree(folder/'dist',destination,dirs_exist_ok=True)
        manifests[version]={p.relative_to(webroot).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in webroot.rglob('*') if p.is_file()}
        print(json.dumps({'frontendBuilt':version,'files':len(manifests[version])}),flush=True)
    save('frontend-artifacts.json',manifests)

def rehearse():
    if (REPORT/'deployment.json').exists():raise RuntimeError('Prior rehearsal must not be overwritten')
    artifacts=json.loads((REPORT/'artifacts.json').read_text(encoding='utf-8'))
    for info in artifacts.values():
        if hashlib.sha256((ROOT/info['path']).read_bytes()).hexdigest()!=info['sha256']:raise RuntimeError('ARTIFACT_CHANGED')
    # Refuse to adopt or destroy containers/network belonging to another run.
    for name in (MYSQL,REDIS):
        if run(['docker','inspect',name]).returncode==0:raise RuntimeError('REHEARSAL_CONTAINER_EXISTS')
    if run(['docker','network','inspect',NETWORK]).returncode==0:raise RuntimeError('REHEARSAL_NETWORK_EXISTS')
    password=secrets.token_hex(24);env=dict(os.environ,MYSQL_ROOT_PASSWORD=password)
    result=run(['docker','network','create','--internal',NETWORK]);result.check_returncode()
    report={'target':'isolated local Docker, not production server','liveServicesModified':False,
        'providerCredentialsForwarded':False,'providerEgress':'Docker internal network',
        'externalMailHealthEnabled':False,'ragEnabled':False,'steps':[],'releaseApproved':False}
    try:
        result=run(['docker','run','-d','--name',MYSQL,'--network',NETWORK,'--network-alias','rehearsal-mysql',
            '-e','MYSQL_ROOT_PASSWORD','-e','MYSQL_DATABASE=customer_rehearsal','-e','TZ=Asia/Shanghai','mysql:8.4'],env=env);result.check_returncode()
        result=run(['docker','run','-d','--name',REDIS,'--network',NETWORK,'--network-alias','rehearsal-redis','redis/redis-stack-server:latest']);result.check_returncode()
        ready=False
        for _ in range(90):
            check=run(['docker','exec',MYSQL,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "SELECT 1"'])
            if check.returncode==0:ready=True;break
            time.sleep(1)
        if not ready:raise RuntimeError('MYSQL_NOT_READY')
        probe=BASE/'probe';probe.mkdir(exist_ok=True)
        compiled=run(['docker','run','--rm','--network','none','-v',str(ROOT/'scripts')+':/source:ro',
            '-v',str(probe)+':/probe','maven:3.9.9-eclipse-temurin-21','javac','-d','/probe','/source/CustomerDeploymentProbe.java'])
        # File/class naming is intentionally checked by javac, not hidden with runtime generation.
        (REPORT/'probe-build.log').write_text(compiled.stdout+compiled.stderr,encoding='utf-8')
        compiled.check_returncode()
        for step,version,engine in [('previous','previous','LEGACY'),('graph','candidate','LANGGRAPH4J'),
                                   ('config-rollback','candidate','LEGACY'),('binary-rollback','previous','LEGACY')]:
            launch(step,version,engine,password,artifacts,report)
            if step=='previous':
                dumped=subprocess.run(['docker','exec',MYSQL,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces customer_rehearsal'],capture_output=True)
                if dumped.returncode:raise RuntimeError('SYNTHETIC_BACKUP_FAILED')
                backup=BASE/'synthetic-before-upgrade.sql';backup.write_bytes(dumped.stdout)
                report['backup']={'scope':'Synthetic isolated DB only','path':backup.relative_to(ROOT).as_posix(),
                    'sha256':hashlib.sha256(dumped.stdout).hexdigest(),'restoreDuringRollback':False}
        # Bad configuration must fail before any business request. Reapply immutable old artifact.
        launch('bad-release','candidate','INVALID',password,artifacts,report,expect_failure=True)
        launch('automatic-rollback','previous','LEGACY',password,artifacts,report)
        first=report['steps'][0]['probe'];last=report['steps'][-1]['probe']
        if first['oldRequestId']!=last['oldRequestId'] or last['schemaVersion']<45 or last['checkpointCount']==0:
            raise RuntimeError('ROLLBACK_DATA_NOT_PRESERVED')
        report['passed']=True
    finally:
        save('deployment.json',report)
        # These exact containers were created above, contain only synthetic data, and have no persistent mounts.
        for name in (MYSQL,REDIS):run(['docker','rm','-f',name])
        run(['docker','rm','-f','orange-customer-deployment-app'])
        run(['docker','network','rm',NETWORK])
    print(json.dumps(report,ensure_ascii=True),flush=True)

def launch(step,version,engine,password,artifacts,report,expect_failure=False):
    started=time.monotonic();name='orange-customer-deployment-app'
    env=dict(os.environ,MYSQL_PASSWORD=password)
    args=['docker','run','--rm','--name',name,'--network',NETWORK,
        '-v',str(ROOT/artifacts[version]['path'])+':/app.jar:ro',
        '-v',str(BASE/'probe')+':/probe:ro','-v',str(REPORT)+':/report',
        '-e','MYSQL_PASSWORD','-e','MYSQL_USER=root',
        '-e','MYSQL_URL=jdbc:mysql://rehearsal-mysql:3306/customer_rehearsal?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',
        '-e','REDIS_HOST=rehearsal-redis','-e','REDIS_PORT=6379','-e','TZ=Asia/Shanghai',
        '-e','CUSTOMER_ENABLED=true','-e','CUSTOMER_ALL_AUTHENTICATED_USERS=true',
        '-e','CUSTOMER_EXECUTION_ENGINE='+engine,'-e','CUSTOMER_RAW_ACCESS_ENABLED=false',
        '-e','RAG_B_ENABLED=false','-e','RAG_B_GITHUB_SYNC_ENABLED=false',
        '-e','PROBE_EXPECT_ENGINE='+engine,'-e','PROBE_STEP='+step,'-e','PROBE_REPORT=/report/'+step+'.json',
        'maven:3.9.9-eclipse-temurin-21','java','-Xmx640m','-Dloader.main=com.orangejd.rehearsal.CustomerDeploymentProbe',
        '-Dloader.path=/probe','-cp','/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher',
        '--spring.profiles.active=dev','--mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl',
        '--management.health.mail.enabled=false',
        '--orange.open-source.auto-refresh-enabled=false','--orange.customer.fact-refresh.enabled=false']
    result=run(args,env=env,timeout=180)
    (REPORT/(step+'.log')).write_text((result.stdout+result.stderr).replace(password,'[REDACTED]'),encoding='utf-8')
    row={'step':step,'artifact':version,'artifactSha256':artifacts[version]['sha256'],'engine':engine,
        'exitCode':result.returncode,'elapsedSeconds':round(time.monotonic()-started,3),'expectedFailure':expect_failure}
    f=REPORT/(step+'.json')
    if f.exists():row['probe']=json.loads(f.read_text(encoding='utf-8'))
    report['steps'].append(row);save('progress.json',report)
    if expect_failure:
        if result.returncode==0:raise RuntimeError('BAD_RELEASE_ACCEPTED')
    elif result.returncode or not row.get('probe',{}).get('passed'):raise RuntimeError(step.upper()+'_FAILED')
    if not expect_failure:
        web=run(['docker','run','--rm','--network','none','-v',str(BASE/version/'web-dist')+':/webroot:ro',
            '-v',str(ROOT/'scripts/customer_release_web_probe.mjs')+':/probe.mjs:ro','node:22-alpine','node','/probe.mjs'])
        (REPORT/(step+'-web.log')).write_text(web.stdout+web.stderr,encoding='utf-8')
        web.check_returncode();row['web']=json.loads(web.stdout)
        save('progress.json',report)
    print(json.dumps(row,ensure_ascii=True),flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--phase',choices=['build','frontend-build','rehearse'],required=True);opts=parser.parse_args()
    REPORT.mkdir(parents=True,exist_ok=True)
    if opts.phase=='build':build()
    elif opts.phase=='frontend-build':frontend_build()
    else:rehearse()
if __name__=='__main__':main()
