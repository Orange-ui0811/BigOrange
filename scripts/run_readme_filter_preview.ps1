param(
    [ValidateSet('development', 'validation', 'all')]
    [string]$Split = 'development'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$previewRoot = Join-Path $repoRoot '_experiment_staging\readme-filter-k2'
New-Item -ItemType Directory -Force -Path $previewRoot | Out-Null
$logPath = Join-Path $previewRoot "$Split-maven.log"
# Offline Maven and an isolated network: this command cannot reach any model or running database.
& docker run --rm --network none --pull never `
    -e 'MAVEN_OPTS=-Xmx512m' `
    -v "${repoRoot}:/workspace" `
    -v 'nxz-bigorange_orangejd-maven-cache:/root/.m2' `
    -w /workspace maven:3.9.9-eclipse-temurin-21 `
    mvn -o -B -pl orange-server -am `
    '-Dtest=ReadmeKnowledgeFilterTest,ReadmeKnowledgeFilterCorpusTest,RagBChunkerTest,RagBTokenChunkerTest' `
    '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx512m' `
    '-Dreadme.preview.root=/workspace' "-Dreadme.preview.split=$Split" test *> $logPath
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $logPath -Tail 85
    throw "Offline README preview failed. Log: $logPath"
}
Get-Content -LiteralPath (Join-Path $previewRoot "$Split-summary.json")
Write-Output "Preview: $(Join-Path $previewRoot "$Split-preview.md")"
Write-Output "Log: $logPath"
