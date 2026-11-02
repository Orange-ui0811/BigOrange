"""R5 bounded execution against the sealed R4 requests; never uses the v1 allowance."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from score_readme_quality_v2 import check_freeze, read, sha, ROOT, R4, R5

RUN = 'K3_R5_FROZEN_V2_20261030'
STAGE = ROOT / '_experiment_staging/readme-quality-v2/r5'
IMAGE = 'maven:3.9.9-eclipse-temurin-21'


def now():
    return datetime.now(timezone.utc).isoformat()


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as out:
        out.write(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
        out.flush()
        os.fsync(out.fileno())


def rows(path):
    return [json.loads(s) for s in path.read_text(encoding='utf-8').splitlines() if s.strip()] if path.exists() else []


@contextmanager
def exclusive_run():
    R5.mkdir(parents=True, exist_ok=True)
    path = R5 / 'execution.lock'
    with path.open('a+b') as lock:
        if path.stat().st_size == 0:
            lock.write(b'0')
            lock.flush()
        lock.seek(0)
        if os.name == 'nt':
            import msvcrt
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            lock.seek(0)
            if os.name == 'nt':
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def prepare():
    check_freeze()
    if (R5 / 'provider-calls.jsonl').exists():
        raise ValueError('R5 already started; authorization cannot be reset')
    receipt = read(STAGE / 'offline-preflight.json')
    if receipt != {'requests': 34, 'modelTargets': 123, 'exactWireChecks': 34, 'mergedRepositories': 12,
                   'providerCalls': 0, 'indexWrites': 0, 'sealedFilesVerified': True}:
        raise ValueError('Offline exact-wire/all-batch merge evidence missing')
    suite = ROOT / 'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.K3R5AcceptanceTest.xml'
    tests = ET.parse(suite).getroot().attrib
    if int(tests['tests']) != 2 or any(int(tests[k]) for k in ['errors', 'failures', 'skipped']):
        raise ValueError('Two non-skipped offline R5 Java checks required')
    files = [ROOT / n for n in ['scripts/run_readme_quality_v2.py', 'scripts/score_readme_quality_v2.py',
             'scripts/test_readme_quality_v2.py', 'orange-server/src/test/java/com/orangejd/server/ragb/ingest/K3R5AcceptanceTest.java',
             'orange-server/src/main/java/com/orangejd/server/foundation/external/ExternalAiClient.java']]
    files += [STAGE / 'offline-preflight.json', STAGE / 'offline-tests.log', STAGE / 'scorer-tests.log']
    # Preserve the non-network test report outside target before later Maven runs replace it.
    saved = STAGE / 'offline-java-tests.xml'
    if not saved.exists():
        saved.write_bytes(suite.read_bytes())
    files.append(saved)
    manifest = {'runId': RUN, 'sealedBeforeProviderCalls': True,
                'files': {p.relative_to(ROOT).as_posix(): sha(p) for p in files},
                'r4SealSha256': sha(R4 / 'r4-seal.json'), 'preparedAt': now()}
    target = R5 / 'execution-manifest.json'
    if not target.exists():
        write_new(target, manifest)
    else:
        for n, value in read(target)['files'].items():
            if sha(ROOT / n) != value:
                raise ValueError('Prepared execution implementation changed')
    auth = {'authorized': True, 'runId': RUN, 'scope': 'K3-R5 quality-v2 sealed R4 requests only',
            'userInstruction': '进行R5吧', 'authorizationBasis': 'Direct instruction to execute R5 following the concrete 34 primary + 6 reserve, maximum 40 proposal; no previous allowance reused.',
            'maximumAttempts': 40, 'primaryRequests': 34, 'reserveRequests': 6,
            'reservePolicy': 'Only the first six transport/protocol failures in frozen request order; once per failed request; never semantic-quality resampling.',
            'automaticRetries': 0, 'failuresTimeoutsRetriesCount': True,
            'sealSha256': sha(R4 / 'r4-seal.json'), 'datasetSha256': sha(R4 / 'frozen-dataset.json'),
            'requestsSha256': sha(R4 / 'requests.json'), 'executionManifestSha256': sha(target),
            'ledgerPath': (R5 / 'provider-calls.jsonl').relative_to(ROOT).as_posix(), 'recordedAt': now()}
    if not (R5 / 'authorization.json').exists():
        write_new(R5 / 'authorization.json', auth)
    print(json.dumps({'status': 'R5_READY', 'maximumAttempts': 40, 'primaryRequests': 34, 'reserveRequests': 6, 'providerCalls': 0}))


def verify_execution():
    check_freeze()
    auth = read(R5 / 'authorization.json')
    if not auth['authorized'] or auth['maximumAttempts'] != 40 or auth['runId'] != RUN:
        raise ValueError('R5 authorization missing')
    for name, key in [('r4-seal.json', 'sealSha256'), ('frozen-dataset.json', 'datasetSha256'), ('requests.json', 'requestsSha256')]:
        if sha(R4 / name) != auth[key]:
            raise ValueError('Authorization identity changed')
    if sha(R5 / 'execution-manifest.json') != auth['executionManifestSha256']:
        raise ValueError('Execution manifest changed')
    for name, digest in read(R5 / 'execution-manifest.json')['files'].items():
        if sha(ROOT / name) != digest:
            raise ValueError(f'Execution implementation changed: {name}')
    count = len(rows(R5 / 'provider-calls.jsonl'))
    if count > 40:
        raise ValueError('R5 allowance exceeded')
    return count


def run(phase):
    before = verify_execution()
    frozen = read(R4 / 'provider-config.json')
    env = dict(os.environ)
    if phase != 'replay':
        # Read credentials in memory; do not write or print inspect/config output.
        config = json.loads(subprocess.check_output(['docker', 'inspect', 'nxz-bigorange-backend-1'], encoding='utf-8'))[0]
        values = dict(v.split('=', 1) for v in config['Config']['Env'] if '=' in v)
        endpoint, key = values.get('AI_TREND_ENDPOINT', ''), values.get('AI_TREND_KEY', '')
        model = values.get('CUSTOMER_MODEL') or values.get('AI_TREND_MODEL', '')
        if not endpoint or not key or not model:
            raise ValueError('Existing backend model configuration missing; values withheld')
        identity = model + ':' + hashlib.sha256((endpoint + '\0' + key + '\0' + model).encode()).hexdigest()
        if identity != frozen['modelIdentity']:
            raise ValueError('Runtime configuration does not match frozen model fingerprint; values withheld')
        env.update(README_K3_ENDPOINT=endpoint, README_K3_KEY=key, README_K3_MODEL=model)
    env.update(CUSTOMER_ACCEPTANCE_LEDGER='/workspace/' + (R5 / 'provider-calls.jsonl').relative_to(ROOT).as_posix(), CUSTOMER_ACCEPTANCE_LIMIT='40')
    command = ['docker', 'run', '--rm', '--pull', 'never', '-e', 'MAVEN_OPTS=-Xmx512m']
    if phase == 'replay':
        command += ['--network', 'none']
    for name in ['README_K3_ENDPOINT', 'README_K3_KEY', 'README_K3_MODEL', 'CUSTOMER_ACCEPTANCE_LEDGER', 'CUSTOMER_ACCEPTANCE_LIMIT']:
        if name in env:
            command += ['-e', name]
    command += ['-v', str(ROOT) + ':/workspace', '-v', 'nxz-bigorange_orangejd-maven-cache:/root/.m2', '-w', '/workspace', IMAGE,
                'mvn', '-o', '-B', '-pl', 'orange-server', '-am',
                '-Dtest=K3R5AcceptanceTest#acquireApprovedRequestsThenReplayAllRepositories', '-Dsurefire.failIfNoSpecifiedTests=false',
                '-DargLine=-Xmx512m', '-Dreadme.r5.root=/workspace', '-Dreadme.r5.live=true', '-Dreadme.r5.phase=' + phase, 'surefire:test']
    STAGE.mkdir(parents=True, exist_ok=True)
    log = STAGE / (phase + '-' + str(time.time_ns()) + '.log')
    result_code = None
    try:
        with log.open('x', encoding='utf-8') as out:
            result_code = subprocess.run(command, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
    finally:
        after = verify_execution()
        receipt = {'phase': phase, 'runId': RUN, 'before': before, 'after': after, 'newAttempts': after - before, 'maximumAttempts': 40,
                   'exitCode': result_code, 'historicalAndFrozenFilesUnchanged': True, 'at': now(), 'logPath': log.relative_to(ROOT).as_posix(), 'logSha256': sha(log)}
        write_new(R5 / ('receipts/' + phase + '-' + str(time.time_ns()) + '.json'), receipt)
    print(json.dumps(receipt, ensure_ascii=False))
    return result_code


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true', help='Bind already requested R5 scope and validated execution files before any HTTP call')
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--phase', choices=['primary', 'reserve', 'replay'], default='primary')
    args = parser.parse_args()
    with exclusive_run():
        if args.prepare:
            prepare()
        elif args.verify:
            print(json.dumps({'status': 'R5_INTEGRITY_VERIFIED', 'used': verify_execution(), 'limit': 40}))
        else:
            raise SystemExit(run(args.phase))
