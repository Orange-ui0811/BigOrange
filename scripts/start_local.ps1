param(
    [switch]$SkipWait,
    [switch]$StartDocker,
    [switch]$OpenBrowser,
    [ValidateRange(1, 1800)][int]$ReadyTimeoutSeconds = 600
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskEnvFile = Join-Path $taskRoot '.env.local'
$taskComposeFile = Join-Path $taskRoot 'compose.local.yml'
if (!(Test-Path -LiteralPath $taskEnvFile)) { throw 'Create .env.local from .env.local.example and configure it before starting.' }
if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required. Install it and start its engine first.' }
function Test-LocalDocker {
    try {
        & docker info --format '{{.ServerVersion}}' *> $null
        return ($LASTEXITCODE -eq 0)
    } catch { return $false }
}
if (!(Test-LocalDocker)) {
    if (!$StartDocker) { throw 'Docker engine is unavailable. Start Docker Desktop, then run this script again.' }
    $taskDockerDesktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (!(Test-Path -LiteralPath $taskDockerDesktop)) { throw 'Docker Desktop was not found. Start Docker manually, then retry.' }
    Write-Output 'Starting Docker Desktop. Waiting for its engine...'
    Start-Process -FilePath $taskDockerDesktop -WindowStyle Hidden
    $taskDockerDeadline = [DateTime]::UtcNow.AddSeconds(180)
    $taskDockerReady = $false
    while ([DateTime]::UtcNow -lt $taskDockerDeadline) {
        if (Test-LocalDocker) { $taskDockerReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (!$taskDockerReady) { throw 'Docker is not ready. Check Docker Desktop for startup or WSL errors, then retry.' }
}
$taskComposeArgs = @('compose', '--env-file', $taskEnvFile, '-f', $taskComposeFile)

# Resolve configuration through Compose, including its environment precedence.
# Capture the result: it contains secrets and must never be printed or logged.
$taskConfigText = & docker @taskComposeArgs config --format json 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve compose.local.yml. Check the Compose installation and local configuration.' }
$taskBackend = ($taskConfigText -join "`n" | ConvertFrom-Json).services.backend
$taskEmbedding = $taskBackend.environment
if ($taskEmbedding.CUSTOMER_ENABLED -ne 'true') {
    Write-Warning 'Customer service is disabled by this configuration. Set CUSTOMER_ENABLED=true in .env.local to enable it.'
}
if ($taskEmbedding.RAG_B_ENABLED -eq 'true' -and $taskEmbedding.RAG_B_EMBEDDING_PROVIDER -eq 'OLLAMA') {
    $taskEmbeddingUri = [uri]$taskEmbedding.RAG_B_OLLAMA_BASE_URL
    if ($taskEmbeddingUri.Scheme -ne 'http' -or $taskEmbeddingUri.Host -notin @('host.docker.internal', 'localhost', '127.0.0.1') -or $taskEmbeddingUri.Port -ne 11434) {
        throw 'This launcher manages the local Ollama service on port 11434. The configured endpoint is different.'
    }
    if ($taskEmbeddingUri.Host -ne 'host.docker.internal') {
        throw 'For this Docker setup, configure RAG_B_OLLAMA_BASE_URL=http://host.docker.internal:11434 so the backend can reach the host.'
    }
    if ([string]::IsNullOrWhiteSpace($taskEmbedding.RAG_B_EMBEDDING_REVISION)) { throw 'RAG_B_EMBEDDING_REVISION must pin the local model before project startup.' }
    Write-Output '[1/3] Starting and warming the local embedding model...'
    & (Join-Path $PSScriptRoot 'start_local_embedding.ps1') -Warmup -Model $taskEmbedding.RAG_B_OLLAMA_MODEL -ExpectedRevision $taskEmbedding.RAG_B_EMBEDDING_REVISION
} else {
    Write-Output '[1/3] Local embedding startup is not required by this configuration.'
}

Write-Output '[2/3] Starting database, Redis, backend and frontends...'
& docker @taskComposeArgs up -d
if ($LASTEXITCODE -ne 0) { throw 'Project startup failed. Inspect Docker Compose service logs.' }
if ($SkipWait) {
    Write-Output 'Services started. Startup compilation may still be running; inspect Docker Compose logs.'
    return
}

Write-Output '[3/3] Waiting for backend and frontend readiness (initial compilation can take several minutes)...'
$taskBackendPort = @($taskBackend.ports | Where-Object { $_.target -eq 8080 })[0].published
if (!$taskBackendPort) { throw 'Cannot locate the published backend port.' }
$taskUrls = @(
    "http://127.0.0.1:$taskBackendPort/actuator/health",
    'http://127.0.0.1:15173/',
    'http://127.0.0.1:15174/admin/'
)
$taskDeadline = [DateTime]::UtcNow.AddSeconds($ReadyTimeoutSeconds)
$taskPending = @($taskUrls)
while ([DateTime]::UtcNow -lt $taskDeadline -and $taskPending.Count -gt 0) {
    $taskPending = @($taskPending | Where-Object {
        try {
            $taskRemaining = [int][Math]::Ceiling(($taskDeadline - [DateTime]::UtcNow).TotalSeconds)
            if ($taskRemaining -le 0) { return $true }
            $taskResponse = Invoke-WebRequest -Uri $_ -UseBasicParsing -TimeoutSec ([Math]::Min(3, $taskRemaining))
            if ($_ -like '*/actuator/health') {
                # Windows PowerShell can expose vendor JSON responses as bytes.
                $taskHealthContent = $taskResponse.Content
                if ($taskHealthContent -is [byte[]]) { $taskHealthContent = [System.Text.Encoding]::UTF8.GetString($taskHealthContent) }
                return (($taskHealthContent | ConvertFrom-Json).status -ne 'UP')
            }
            return ($taskResponse.StatusCode -ne 200)
        } catch { return $true }
    })
    if ($taskPending.Count -gt 0) { Start-Sleep -Seconds 1 }
}
if ($taskPending.Count -gt 0) { throw "Services are not ready within the startup budget: $($taskPending -join ', '). Inspect Docker Compose logs; containers have not been stopped." }
Write-Output 'Ready. Customer: http://127.0.0.1:15173/customer'
Write-Output 'Admin: http://127.0.0.1:15174/admin/'
if ($OpenBrowser) {
    Start-Process 'http://127.0.0.1:15173/'
    Start-Process 'http://127.0.0.1:15174/admin/'
}
