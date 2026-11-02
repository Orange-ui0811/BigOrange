"""Offline graph/governance checks: no production credentials or external model calls."""
import argparse, json, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTS = ','.join([
    'CustomerGraphTest', 'CustomerGraphConversationRegressionTest', 'CustomerFrozenConversationRegressionTest',
    'CustomerGraphEvidenceRoutingTest', 'CustomerRunsTest', 'CustomerToolsTest',
    'CustomerStoreTest', 'CustomerModelTest', 'RagBOllamaEmbeddingModelTest',
    'CustomerPurposeTest', 'CustomerPurposeCatalogTest',
    'CustomerTopicCoverageTest', 'CustomerGoalCoverageTest', 'CustomerConversationTest', 'CustomerStateTest',
    'CustomerAdminDiagnosticsTest', 'CustomerEvidenceTest',
    'CustomerEngineLifecycleTest', 'CustomerOrchestrationTest', 'CustomerProjectsTest',
])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report-dir', required=True)
    options = parser.parse_args()
    folder = (ROOT / options.report_dir).resolve()
    if not folder.is_relative_to(ROOT / 'docs/customer/langgraph4j'):
        raise ValueError('Reports must remain in LangGraph4j documentation')
    folder.mkdir(parents=True, exist_ok=True)
    if (folder / 'execution.json').exists():
        raise ValueError('Use a fresh report directory; previous checks are preserved')
    backend = json.loads(subprocess.check_output(['docker', 'inspect', 'nxz-bigorange-backend-1'], text=True))[0]
    cache = next(m.get('Name', m['Source']) for m in backend['Mounts'] if m['Destination'] == '/root/.m2')
    args = ['docker', 'run', '--rm', '--name', 'orange-customer-graph-checks', '--network', 'none',
            '-v', str(ROOT) + ':/workspace', '-v', cache + ':/root/.m2', '-w', '/workspace',
            'maven:3.9.9-eclipse-temurin-21', 'mvn', '-o', '-B', '-pl', 'orange-server', '-am', 'test',
            '-Dtest=' + TESTS, '-Dsurefire.failIfNoSpecifiedTests=false']
    result = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    (folder / 'checks.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    report = {'exitCode': result.returncode, 'externalModelCalls': 0, 'network': 'none',
              'productionCredentialsForwarded': False, 'tests': TESTS,
              'scope': 'Mocked model/tools, graph routing, late cancellation, queue/replay, tool deadlines and local adapter protocol'}
    (folder / 'execution.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report), flush=True)
    raise SystemExit(result.returncode)

if __name__ == '__main__':
    main()
