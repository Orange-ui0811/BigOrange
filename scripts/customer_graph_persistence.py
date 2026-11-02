"""Isolated MySQL/Flyway/checkpoint tests. Credentials remain in process memory, never in reports."""
import argparse, json, os, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def inspect(name):
    return json.loads(subprocess.check_output(['docker','inspect',name],text=True))[0]
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--report-dir',default='docs/customer/langgraph4j')
    options=parser.parse_args()
    folder=(ROOT/options.report_dir).resolve()
    if not folder.is_relative_to(ROOT/'docs'):raise ValueError('Report must stay in repository docs')
    mysql=inspect('nxz-bigorange-mysql-1')
    env=dict(item.split('=',1) for item in mysql['Config']['Env'] if '=' in item)
    name='customer_acceptance_graph_'+str(int(time.time()))
    subprocess.run(['docker','exec','nxz-bigorange-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE '+name+' CHARACTER SET utf8mb4"'],check=True,capture_output=True)
    empty=name+'_empty'
    subprocess.run(['docker','exec','nxz-bigorange-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE '+empty+' CHARACTER SET utf8mb4"'],check=True,capture_output=True)
    backend=inspect('nxz-bigorange-backend-1')
    network=next(iter(mysql['NetworkSettings']['Networks']))
    cache=next((m.get('Name',m['Source']) for m in backend['Mounts'] if m['Destination']=='/root/.m2'),None)
    args=['docker','run','--rm','--network',network,'-v',str(ROOT)+':/workspace','-w','/workspace']
    if cache:args+=['-v',cache+':/root/.m2']
    runenv=dict(os.environ,CUSTOMER_TEST_MYSQL_URL='jdbc:mysql://mysql:3306/'+name+'?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',CUSTOMER_TEST_MYSQL_USER='root',CUSTOMER_TEST_MYSQL_PASSWORD=env['MYSQL_ROOT_PASSWORD'])
    runenv['CUSTOMER_EMPTY_MYSQL_URL']=runenv['CUSTOMER_TEST_MYSQL_URL'].replace('/'+name+'?', '/'+empty+'?')
    for key in ('CUSTOMER_TEST_MYSQL_URL','CUSTOMER_TEST_MYSQL_USER','CUSTOMER_TEST_MYSQL_PASSWORD','CUSTOMER_EMPTY_MYSQL_URL'):args+=['-e',key]
    args+=['maven:3.9.9-eclipse-temurin-21','mvn','-B','-pl','orange-server','-am','test','-Dtest=CustomerGraphPersistenceTest,CustomerPersistenceIntegrationTest,CustomerEngineLifecycleTest','-Dsurefire.failIfNoSpecifiedTests=false']
    folder.mkdir(exist_ok=True,parents=True)
    result=subprocess.run(args,env=runenv,capture_output=True,text=True,encoding='utf-8',errors='replace')
    (folder/'persistence.log').write_text((result.stdout+result.stderr).replace(env['MYSQL_ROOT_PASSWORD'],'[REDACTED]'),encoding='utf-8')
    (folder/'persistence.json').write_text(json.dumps({'database':name,'exitCode':result.returncode,'releaseApproved':False,'scope':'Isolated V40 to current Flyway upgrade, transactions, MySQL checkpoints, two-connection pool, 100 measured turns per engine after warmup, raw access audit.'},indent=2),encoding='utf-8')
    print(json.dumps({'database':name,'exitCode':result.returncode}))
    raise SystemExit(result.returncode)
if __name__=='__main__':main()
