"""Disposable MySQL acceptance for source memory. Never calls an AI provider."""
import json
import os
import re
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def inspect(name):
    return json.loads(subprocess.check_output(['docker', 'inspect', name], text=True))[0]

def main():
    mysql = inspect('nxz-bigorange-mysql-1')
    values = dict(value.split('=', 1) for value in mysql['Config']['Env'] if '=' in value)
    network = next(iter(mysql['NetworkSettings']['Networks']))
    database = 'customer_context_acceptance_' + uuid.uuid4().hex[:12]
    assert re.fullmatch(r'customer_context_acceptance_[a-f0-9]{12}', database)
    env = dict(os.environ, MYSQL_PWD=values['MYSQL_ROOT_PASSWORD'])

    def sql(statement):
        result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'nxz-bigorange-mysql-1',
            'mysql', '-uroot', '-N', '-B'], input=statement, text=True, capture_output=True, env=env)
        if result.returncode:
            raise RuntimeError('Disposable database operation failed; credentials withheld')

    sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4;')
    env.update(CUSTOMER_CONTEXT_TEST_MYSQL_URL='jdbc:mysql://nxz-bigorange-mysql-1:3306/' + database + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',
        CUSTOMER_CONTEXT_TEST_MYSQL_USER='root', CUSTOMER_CONTEXT_TEST_MYSQL_PASSWORD=values['MYSQL_ROOT_PASSWORD'])
    log = ROOT / '_staging/context-p3-mysql.log'
    try:
        args = ['docker', 'run', '--rm', '--name', 'orangejd-p3-mysql-tests', '--network', network,
            '-e', 'MAVEN_OPTS=-Xmx512m', '-e', 'CUSTOMER_CONTEXT_TEST_MYSQL_URL',
            '-e', 'CUSTOMER_CONTEXT_TEST_MYSQL_USER', '-e', 'CUSTOMER_CONTEXT_TEST_MYSQL_PASSWORD',
            '-v', str(ROOT) + ':/workspace', '-v', 'nxz-bigorange_orangejd-maven-cache:/root/.m2',
            '-w', '/workspace', 'maven:3.9.9-eclipse-temurin-21', 'mvn', '-o', '-B', '-pl', 'orange-server', '-am',
            '-Dtest=CustomerContextMysqlIntegrationTest', '-Dsurefire.failIfNoSpecifiedTests=false', '-DargLine=-Xmx512m', 'test']
        with log.open('w', encoding='utf-8') as output:
            completed = subprocess.run(args, env=env, stdout=output, stderr=subprocess.STDOUT)
        print(json.dumps({'exitCode': completed.returncode, 'log': str(log), 'modelRequests': 0}))
        return completed.returncode
    finally:
        sql('DROP DATABASE `' + database + '`;')

if __name__ == '__main__':
    raise SystemExit(main())
