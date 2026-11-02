$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $repoRoot '_experiment_staging\readme-filter-k3'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$logPath = Join-Path $outputRoot 'offline-maven.log'
$cacheReplay = if (Test-Path -LiteralPath (Join-Path $outputRoot 'live-results.json')) { 'true' } else { 'false' }
& docker run --rm --network none --pull never -e 'MAVEN_OPTS=-Xmx512m' `
    -v "${repoRoot}:/workspace" -v 'nxz-bigorange_orangejd-maven-cache:/root/.m2' `
    -w /workspace maven:3.9.9-eclipse-temurin-21 `
    mvn -o -B -pl orange-server -am `
    '-Dtest=ReadmeModelReviewTest,ReadmeModelReviewCorpusTest,ReadmeSpringAiReviewTransportTest,ReadmeKnowledgeFilterTest,ReadmeKnowledgeFilterCorpusTest,RagBTokenChunkerTest' `
    '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx512m' `
    '-Dreadme.k3.root=/workspace' "-Dreadme.k3.cache-replay=$cacheReplay" `
    '-Dreadme.preview.root=/workspace' '-Dreadme.preview.split=all' test *> $logPath
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $logPath -Tail 85; throw "K3 offline checks failed: $logPath" }
Get-Content -LiteralPath $logPath -Tail 18
Write-Output "Request plan: $(Join-Path $outputRoot 'request-plan.json')"
