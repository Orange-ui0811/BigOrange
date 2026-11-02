$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskStage = Join-Path $taskRoot '_experiment_staging\readme-dependency-v2'
New-Item -ItemType Directory -Force -Path $taskStage | Out-Null
$taskLog = Join-Path $taskStage 'acceptance-tests.log'
Push-Location $taskRoot
try {
    & docker run --rm --network none --pull never -e 'MAVEN_OPTS=-Xmx512m' `
        -v "${taskRoot}:/workspace" -v 'nxz-bigorange_orangejd-maven-cache:/root/.m2' `
        -w /workspace maven:3.9.9-eclipse-temurin-21 `
        mvn -o -B -pl orange-server -am `
        '-Dtest=ReadmeDependencyClosureTest,ReadmeKnowledgeFilterTest,ReadmeModelReviewTest,ReadmeSpringAiReviewTransportTest,RagBTokenChunkerTest' `
        '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx512m' '-Dreadme.dependency.root=/workspace' test *> $taskLog
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskLog -Tail 35; throw 'Dependency checks failed' }
    & python scripts/evaluate_readme_filter_preview.py --split all `
        --ledger-receipt '_experiment_staging/readme-filter-k3/live-budget-receipt.json' `
        --prediction-dir '_experiment_staging/readme-dependency-v2/regression' `
        --output-dir 'docs/rag-b/readme-filter-v1/k3/quality-v2/dependency-regression'
    if ($LASTEXITCODE -ne 0) { throw 'Frozen annotation comparison failed' }
    & python scripts/verify_readme_dependency.py
    if ($LASTEXITCODE -ne 0) { throw 'Dependency evidence check failed' }
} finally { Pop-Location }
