"""Authorized repair validation: 8-run smoke gates an optional 80-run comparison. No secret output."""
import argparse, hashlib, json, os, re, subprocess
from pathlib import Path
from customer_local_smoke import configured_environment

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'docs/customer/langgraph4j/repair-20261007'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--phase', choices=['smoke', 'full', 'repair'], default='smoke')
    parser.add_argument('--report-root', default=BASE.relative_to(ROOT).as_posix())
    parser.add_argument('--case-file', help='Fresh frozen criteria; previous reports are never rewritten')
    parser.add_argument('--action-only', action='store_true', help='Eight single-turn empty-context ACTION requests only')
    parser.add_argument('--smoke-gate', help='Previously completed smoke execution.json; its origin is recorded, never copied as a new test')
    options = parser.parse_args()
    batch_base = (ROOT / options.report_root).resolve()
    if not batch_base.is_relative_to(ROOT / 'docs/customer/langgraph4j'):
        raise ValueError('Acceptance reports must remain in the LangGraph4j documentation directory')
    gate_file = None
    if options.phase == 'full':
        gate_file = (ROOT / options.smoke_gate).resolve() if options.smoke_gate else batch_base / 'smoke/execution.json'
        if not gate_file.is_relative_to(ROOT / 'docs/customer/langgraph4j'):
            raise ValueError('Smoke gate must remain in LangGraph4j documentation')
        gate = json.loads(gate_file.read_text(encoding='utf-8'))
        if gate['exitCode'] != 0:
            raise RuntimeError('Smoke gate failed; no full provider calls permitted.')
    report = batch_base / options.phase
    report.mkdir(parents=True, exist_ok=True)
    if (report / 'provider-acceptance.json').exists():
        raise RuntimeError('Existing batch must not be overwritten or silently repeated.')
    case_file = (ROOT / options.case_file).resolve() if options.case_file else BASE / (options.phase + '-cases.json')
    if not case_file.is_relative_to(ROOT / 'docs/customer/langgraph4j'):
        raise ValueError('Cases must remain in LangGraph4j documentation')
    cases = json.loads(case_file.read_text(encoding='utf-8'))
    if len(cases) != (2 if options.phase == 'repair' else 4 if options.phase == 'smoke' else 20):
        raise RuntimeError('Unexpected authorized case count')
    if options.action_only and (options.phase != 'repair' or any('turns' in c or 'prior' in c for c in cases)):
        raise RuntimeError('Action-only authorization requires two fresh single-turn cases')
    backend = json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'],text=True))[0]
    config = configured_environment()
    network = next(iter(backend['NetworkSettings']['Networks']))
    cache = next((m.get('Name',m['Source']) for m in backend['Mounts'] if m['Destination']=='/root/.m2'),None)
    keys = [k for k in config if k.startswith(('ORANGE_','CUSTOMER_','MYSQL_','REDIS_','AI_','RAG_','GITEE_','DEEPSEEK_','SPRING_'))]
    overrides = {'RUN_CUSTOMER_GRAPH_ACCEPTANCE':'true', 'TZ':'Asia/Shanghai',
        'CUSTOMER_GRAPH_PHASE':options.phase, 'CUSTOMER_GRAPH_REPORT_DIR':report.relative_to(ROOT).as_posix(),
        'CUSTOMER_GRAPH_CASE_FILE':case_file.relative_to(ROOT).as_posix()}
    if options.action_only:overrides['CUSTOMER_GRAPH_ACTION_ONLY']='true'
    env = dict(os.environ, **{k:config[k] for k in keys}, **overrides)
    args = ['docker','run','--rm','--name','orange-customer-graph-repair','--network',network,
        '-v',str(ROOT)+':/workspace','-w','/workspace']
    if cache: args += ['-v',cache+':/root/.m2']
    for key in keys+list(overrides): args += ['-e',key]
    args += ['maven:3.9.9-eclipse-temurin-21','mvn','-B','-pl','orange-server','-am','test',
        '-Dtest=CustomerGraphProviderAcceptanceTest','-Dsurefire.failIfNoSpecifiedTests=false']
    manifest = {'phase':options.phase, 'caseSha256':hashlib.sha256(case_file.read_bytes()).hexdigest(),
        'runs':sum(len(c.get('turns', [c])) for c in cases)*(2 if options.phase=='smoke' else 4), 'model':config.get('CUSTOMER_MODEL'),
        'scope':'ACTION prompt and frozen synthetic messages only; no profile/history/evidence' if options.action_only else 'Synthetic requirements and retrieved B evidence only; profiles/history are synthetic.',
        'embeddingProvider':config.get('RAG_B_EMBEDDING_PROVIDER'),
        'environmentTextEndpoint':config.get('AI_TEXT_ENDPOINT'),
        'endpointResolution':'Platform text configuration; active runtime settings can override environment.',
        'purposePolicy': {'thinking':config.get('CUSTOMER_PURPOSE_THINKING','false'),
            'outputTokens':config.get('CUSTOMER_PURPOSE_OUTPUT_TOKENS','2048'),
            'stageSeconds':config.get('CUSTOMER_PURPOSE_SECONDS','30'),
            'retryReserveSeconds':config.get('CUSTOMER_PURPOSE_RETRY_RESERVE_SECONDS','10'),
            'maxAttempts':2, 'truncatedThinkingFallback':'same input/schema with thinking disabled'},
        'sourceSha256': {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [
            ROOT/'orange-server/src/main/java/com/orangejd/server/customer/CustomerModel.java',
            ROOT/'orange-server/src/main/java/com/orangejd/server/customer/CustomerProperties.java',
            ROOT/'orange-server/src/main/java/com/orangejd/server/customer/CustomerPurpose.java']},
        'releaseApproved':False}
    if gate_file:
        manifest['previousSmokeGate'] = {'path':str(gate_file.relative_to(ROOT)),
            'sha256':hashlib.sha256(gate_file.read_bytes()).hexdigest(), 'newSmokeRequests':0}
    (report/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=True),flush=True)
    result = subprocess.run(args,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
    output = result.stdout+result.stderr
    for key in keys:
        if re.search(r'KEY|SECRET|PASSWORD|TOKEN',key,re.I) and len(config[key])>4:
            output=output.replace(config[key],'[REDACTED]')
    (report/'provider.log').write_text(output,encoding='utf-8')
    (report/'execution.json').write_text(json.dumps({'exitCode':result.returncode,'releaseApproved':False}),encoding='utf-8')
    print(json.dumps({'exitCode':result.returncode,'report':str(report)}))
    raise SystemExit(result.returncode)

if __name__=='__main__': main()
