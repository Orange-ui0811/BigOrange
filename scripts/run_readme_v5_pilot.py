"""V1.6/V5 frozen pilot: 16 primary plus 2 failure-only reserve, maximum 18 attempts."""
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
ROOT = Path(__file__).resolve().parents[1]
R4 = R5 = ROOT / "docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3"

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def check_freeze():
    manifest = read(ROOT / "docs/rag-b/readme-filter-v1/k3/quality-v2/v5-admission-repair-v2/artifact-manifest.json")
    if sha(R4 / 'artifact-manifest.json') != sha(ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/v5-admission-repair-v2/artifact-manifest.json'):
        raise ValueError('Pilot artifact identity differs from the offline verified phase')
    history = read(ROOT / "docs/rag-b/readme-filter-v1/k3/quality-v2/v5-admission-repair-v2/before.json")
    for name, digest in {**manifest["files"], **history["historicalFiles"]}.items():
        if sha(ROOT / name) != digest:
            raise ValueError("Frozen or historical file changed: " + name)

RUN = 'K3_V5_PILOT_V3_20261030'
STAGE = ROOT / '_experiment_staging/readme-v5-pilot-v3'
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
    proposal=read(R5/'execution-proposal.json')
    if any(proposal.get(k)!=v for k,v in {'primaryRequests':16,'reserveRequests':2,'maximumProposedAttempts':18,
            'modelTargets':78,'maxBlocks':8,'maxInputUpperBound':12000,'maxOutputTokens':2048,'automaticRetries':0}.items()):
        raise ValueError('Proposal no longer matches the explicitly authorized pilot')
    scorer_log=(STAGE/'scorer-tests.log').read_text(encoding='utf-8')
    if 'Ran 5 tests' not in scorer_log or '\nOK\n' not in scorer_log:
        raise ValueError('Scorer regression checks must pass before preparing execution')
    receipt = read(STAGE / 'offline-preflight.json')
    if receipt != {'requests': 16, 'modelTargets': 78, 'exactWireChecks': 16, 'mergedRepositories': 5,
                   'providerCalls': 0, 'indexWrites': 0, 'sealedFilesVerified': True}:
        raise ValueError('Offline exact-wire/all-batch merge evidence missing')
    suite = ROOT / 'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.K3V5PilotAcceptanceTest.xml'
    tests = ET.parse(suite).getroot().attrib
    if int(tests['tests']) != 2 or any(int(tests[k]) for k in ['errors', 'failures', 'skipped']):
        raise ValueError('Two non-skipped offline R5 Java checks required')
    files = [ROOT / n for n in ['scripts/run_readme_v5_pilot.py', 'scripts/score_readme_v5_pilot.py', 'scripts/score_readme_quality_v3.py',
             'scripts/test_readme_quality_v3.py', 'orange-server/src/test/java/com/orangejd/server/ragb/ingest/K3V5PilotAcceptanceTest.java',
             'orange-server/src/main/java/com/orangejd/server/foundation/external/ExternalAiClient.java',
             'orange-server/src/main/java/com/orangejd/server/foundation/external/CustomerSpringAiChatModel.java',
             'orange-server/src/main/java/com/orangejd/server/foundation/external/AiAcceptanceBudget.java',
             'orange-server/src/main/java/com/orangejd/server/foundation/external/AiRuntimeConfig.java']]
    files += [STAGE / 'offline-preflight.json', STAGE / 'offline-tests.log', STAGE / 'scorer-tests.log', R5 / 'baseline-evaluation.json']
    # Preserve the non-network test report outside target before later Maven runs replace it.
    saved = STAGE / 'offline-java-tests.xml'
    if not saved.exists():
        saved.write_bytes(suite.read_bytes())
    files.append(saved)
    manifest = {'runId': RUN, 'sealedBeforeProviderCalls': True,
                'files': {p.relative_to(ROOT).as_posix(): sha(p) for p in files},
                'artifactManifestSha256': sha(R4 / 'artifact-manifest.json'), 'preparedAt': now()}
    target = R5 / 'execution-manifest.json'
    if not target.exists():
        write_new(target, manifest)
    else:
        for n, value in read(target)['files'].items():
            if sha(ROOT / n) != value:
                raise ValueError('Prepared execution implementation changed')
    auth = {'authorized': True, 'runId': RUN, 'scope': 'V1.6/V5 pilot-v3 frozen requests only; no index writes',
            'userInstruction': '授权新增最多18次', 'authorizationBasis': 'Explicit user approval of 16 primary + 2 failure reserve, maximum 18 attempts, after the frozen V1.6 proposal; question call_7a79440cdb474eae8979a7a7d6759921. No previous allowance reused.',
            'maximumAttempts': 18, 'primaryRequests': 16, 'reserveRequests': 2,
            'reservePolicy': 'Only the first two transport/protocol failures in frozen request order; once per failed request; never semantic-quality resampling.',
            'automaticRetries': 0, 'failuresTimeoutsRetriesCount': True,
            'sealSha256': sha(R4 / 'artifact-manifest.json'), 'datasetSha256': sha(R4 / 'frozen-labels.json'),
            'requestsSha256': sha(R4 / 'requests.json'), 'executionManifestSha256': sha(target),
            'ledgerPath': (R5 / 'provider-calls.jsonl').relative_to(ROOT).as_posix(), 'recordedAt': now()}
    if not (R5 / 'authorization.json').exists():
        write_new(R5 / 'authorization.json', auth)
    print(json.dumps({'status': 'R5_READY', 'maximumAttempts': 18, 'primaryRequests': 16, 'reserveRequests': 2, 'providerCalls': 0}))


def verify_execution():
    check_freeze()
    auth = read(R5 / 'authorization.json')
    if not auth['authorized'] or auth['maximumAttempts'] != 18 or auth['runId'] != RUN:
        raise ValueError('R5 authorization missing')
    for name, key in [('artifact-manifest.json', 'sealSha256'), ('frozen-labels.json', 'datasetSha256'), ('requests.json', 'requestsSha256')]:
        if sha(R4 / name) != auth[key]:
            raise ValueError('Authorization identity changed')
    if sha(R5 / 'execution-manifest.json') != auth['executionManifestSha256']:
        raise ValueError('Execution manifest changed')
    for name, digest in read(R5 / 'execution-manifest.json')['files'].items():
        if sha(ROOT / name) != digest:
            raise ValueError(f'Execution implementation changed: {name}')
    count = len(rows(R5 / 'provider-calls.jsonl'))
    if count > 18:
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
    env.update(CUSTOMER_ACCEPTANCE_LEDGER='/workspace/' + (R5 / 'provider-calls.jsonl').relative_to(ROOT).as_posix(), CUSTOMER_ACCEPTANCE_LIMIT='18')
    command = ['docker', 'run', '--rm', '--pull', 'never', '-e', 'MAVEN_OPTS=-Xmx512m']
    if phase == 'replay':
        command += ['--network', 'none']
    for name in ['README_K3_ENDPOINT', 'README_K3_KEY', 'README_K3_MODEL', 'CUSTOMER_ACCEPTANCE_LEDGER', 'CUSTOMER_ACCEPTANCE_LIMIT']:
        if name in env:
            command += ['-e', name]
    command += ['-v', str(ROOT) + ':/workspace', '-v', 'nxz-bigorange_orangejd-maven-cache:/root/.m2', '-w', '/workspace', IMAGE,
                'mvn', '-o', '-B', '-pl', 'orange-server', '-am',
                '-Dtest=K3V5PilotAcceptanceTest#acquireApprovedRequestsThenReplayAllRepositories', '-Dsurefire.failIfNoSpecifiedTests=false',
                '-DargLine=-Xmx512m', '-Dreadme.v5.pilot.root=/workspace', '-Dreadme.v5.pilot.live=true', '-Dreadme.v5.pilot.phase=' + phase, 'surefire:test']
    STAGE.mkdir(parents=True, exist_ok=True)
    log = STAGE / (phase + '-' + str(time.time_ns()) + '.log')
    result_code = None
    try:
        with log.open('x', encoding='utf-8') as out:
            result_code = subprocess.run(command, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
    finally:
        after = verify_execution()
        receipt = {'phase': phase, 'runId': RUN, 'before': before, 'after': after, 'newAttempts': after - before, 'maximumAttempts': 18,
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
            print(json.dumps({'status': 'R5_INTEGRITY_VERIFIED', 'used': verify_execution(), 'limit': 18}))
        else:
            raise SystemExit(run(args.phase))
