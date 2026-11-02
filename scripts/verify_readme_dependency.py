"""Verify R1/R2 offline evidence without contacting models or modifying prior acceptance results."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/readme-dependency-v2'
OUT = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    before = read(STAGE / 'before.json')
    for name, value in before['files'].items():
        assert sha(ROOT / name) == value, f'Historical file changed: {name}'
    corpus = read(STAGE / 'corpus-check.json')
    assert corpus['documents'] == 303
    for key in ['sourceMappingErrors', 'missingDependencies', 'idempotenceErrors', 'providerCalls', 'indexWrites']:
        assert corpus[key] == 0, key
    regression = read(OUT / 'dependency-regression/all-evaluation.json')['summary']
    assert regression['documents'] == 38 and regression['annotatedCases'] == 175
    for key in ['criticalWrongDropCases', 'reviewApprovalCases', 'sourceMappingErrors', 'nonKeepCandidateLeaks']:
        assert regression[key] == 0, key
    baseline = ET.parse(STAGE / 'baseline-tests.xml').getroot()
    assert int(baseline.attrib['tests']) == 7 and int(baseline.attrib['failures']) == 5
    classes = ['ReadmeDependencyClosureTest', 'ReadmeKnowledgeFilterTest', 'ReadmeModelReviewTest',
               'ReadmeSpringAiReviewTransportTest', 'RagBTokenChunkerTest']
    suites = []
    report_times = []
    for name in classes:
        path = ROOT / f'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.{name}.xml'
        suite = ET.parse(path).getroot()
        report_times.append(path.stat().st_mtime_ns)
        for key in ['failures', 'errors', 'skipped']:
            assert int(suite.attrib[key]) == 0, f'{name}: {key}'
        assert int(suite.attrib['tests']) > 0
        suites.append({'class': name, 'tests': int(suite.attrib['tests']), 'seconds': float(suite.attrib['time']),
                       'cases': [case.attrib['name'] for case in suite.findall('testcase')]})
    assert len(suites[0]['cases']) == 8 and 'frozenCorpusKeepsMappingsClosedPacketsAndIdempotentAssembly' in suites[0]['cases']
    log = STAGE / 'acceptance-tests.log'
    assert 'BUILD SUCCESS' in log.read_text(encoding='utf-8'), 'Latest acceptance runner did not finish'
    tracked = [ROOT / f'orange-server/src/main/java/com/orangejd/server/ragb/ingest/{n}.java'
               for n in ['ReadmeKnowledgeFilter', 'ReadmeModelReview']]
    tracked += [ROOT / 'orange-server/src/test/java/com/orangejd/server/ragb/ingest/ReadmeDependencyClosureTest.java',
                ROOT / 'orange-server/src/test/resources/readme-filter/dependency-v2/sources.json',
                ROOT / 'scripts/evaluate_readme_filter_preview.py', ROOT / 'scripts/run_readme_dependency_checks.ps1',
                Path(__file__).resolve(), OUT / 'policy-and-contract.md', log]
    test_inputs = list((ROOT / 'orange-server/src/main/java/com/orangejd/server/ragb/ingest').glob('Readme*.java'))
    test_inputs += [ROOT / f'orange-server/src/test/java/com/orangejd/server/ragb/ingest/{n}.java' for n in classes]
    test_inputs += [ROOT / 'orange-server/src/test/resources/readme-filter/dependency-v2/sources.json']
    for path in test_inputs:
        assert path.stat().st_mtime_ns <= min(report_times), f'Source newer than test reports; rerun checks: {path.name}'
    quality_ledger = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v1/provider-calls.jsonl'
    used = sum(bool(line.strip()) for line in quality_ledger.read_text(encoding='utf-8').splitlines())
    assert used == 18
    receipt = {'status': 'R1_CONTRACT_AND_R2_OFFLINE_CHECKED_R3_PENDING',
               'baseline': {'tests': 7, 'failures': 5}, 'tests': sum(s['tests'] for s in suites), 'suites': suites,
               'corpus': corpus, 'frozenAnnotationRegression': regression,
               'historicalFilesVerifiedUnchanged': len(before['files']), 'additionalProviderCalls': 0,
               'previousQualityBudget': {'used': used, 'authorized': 20, 'remaining': 20-used},
               'qualityGateV2': 'NOT_RUN', 'indexWrites': 0,
               'implementationAndEvidenceHashes': {p.relative_to(ROOT).as_posix(): sha(p) for p in tracked},
               'limitations': ['Simulated selections prove dependency handling, not model classification accuracy.',
                              'Applicability/scope contract is not implemented until R3.',
                              'K4 production ingestion and retrieval evaluation are not part of this offline run.']}
    OUT.mkdir(exist_ok=True)
    (OUT / 'dependency-verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: receipt[k] for k in ['status', 'tests', 'additionalProviderCalls', 'qualityGateV2', 'indexWrites']}))


if __name__ == '__main__':
    main()
