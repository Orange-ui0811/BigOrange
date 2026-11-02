$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskStage = Join-Path $taskRoot '_experiment_staging\readme-recommendation-v3'
New-Item -ItemType Directory -Force -Path $taskStage | Out-Null
$taskLog = Join-Path $taskStage 'acceptance-tests.log'
Push-Location $taskRoot
try {
    & python scripts/verify_readme_recommendation.py --prepare
    if ($LASTEXITCODE -ne 0) { throw 'Could not freeze offline test inputs' }
    & docker run --rm --network none --pull never -e 'MAVEN_OPTS=-Xmx512m' `
        -v "${taskRoot}:/workspace" -v 'nxz-bigorange_orangejd-maven-cache:/root/.m2' `
        -w /workspace maven:3.9.9-eclipse-temurin-21 `
        mvn -o -B -pl orange-server -am `
        '-Dtest=ReadmeRecommendationProjectionTest,ReadmeDependencyClosureTest,ReadmeKnowledgeFilterTest,ReadmeModelReviewTest,ReadmeSpringAiReviewTransportTest,RagBTokenChunkerTest' `
        '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx512m' `
        '-Dreadme.recommendation.root=/workspace' '-Dreadme.dependency.root=/workspace' `
        '-Dreadme.dependency.output=/workspace/_experiment_staging/readme-recommendation-v3/dependency' test *> $taskLog
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $taskLog -Tail 45; throw 'Recommendation admission checks failed' }
    & python scripts/verify_readme_recommendation.py
    if ($LASTEXITCODE -ne 0) { throw 'Recommendation evidence check failed' }
} finally { Pop-Location }
