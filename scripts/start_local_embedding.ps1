param(
    [switch]$Warmup,
    [string]$Model = 'bge-m3:orangejd-v1',
    [string]$ExpectedRevision = ''
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDir = Join-Path $taskRoot '.local-models'
$taskExecutable = Join-Path $taskDir 'ollama\ollama.exe'
if (!(Test-Path -LiteralPath $taskExecutable)) { throw 'Download the verified Ollama Windows package first.' }
$env:OLLAMA_MODELS = Join-Path $taskDir 'models'
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_NUM_PARALLEL = '1'
$env:OLLAMA_MAX_LOADED_MODELS = '1'
$env:OLLAMA_KEEP_ALIVE = '-1'
$taskAvailable = $false
try { $null = Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 2; $taskAvailable = $true } catch {}
if (!$taskAvailable) {
    New-Item -ItemType Directory -Force -Path (Join-Path $taskDir 'logs') | Out-Null
    $taskProcess = Start-Process -FilePath $taskExecutable -ArgumentList 'serve' -WorkingDirectory (Split-Path $taskExecutable) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskDir 'logs\server.out.log') -RedirectStandardError (Join-Path $taskDir 'logs\server.err.log')
    Write-Output "Local embedding server started: PID $($taskProcess.Id)"
    for ($taskAttempt=0; $taskAttempt -lt 30; $taskAttempt++) {
        try { $null = Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 1; $taskAvailable=$true; break } catch { Start-Sleep -Milliseconds 300 }
    }
    if (!$taskAvailable) { throw 'Local embedding server did not become ready; inspect .local-models/logs.' }
}
$taskTags = Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5
$taskModel = @($taskTags.models | Where-Object { $_.name -eq $Model -or $_.model -eq $Model })
if ($taskModel.Count -ne 1) { throw "Local model '$Model' is missing or ambiguous. No automatic download will be performed." }
if ($ExpectedRevision -and (($taskModel[0].digest -replace '^sha256:', '') -ne ($ExpectedRevision -replace '^sha256:', ''))) {
    throw 'Local model revision differs from the configured B index revision. Startup stopped.'
}
if ($Warmup) {
    $taskBody = @{ model=$Model; input='Local knowledge embedding warmup'; truncate=$false; keep_alive=-1 } | ConvertTo-Json
    $taskResult = Invoke-RestMethod 'http://127.0.0.1:11434/api/embed' -Method Post -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($taskBody)) -TimeoutSec 60
    if (@($taskResult.embeddings).Count -ne 1 -or $taskResult.embeddings[0].Count -ne 1024) { throw 'Warmup did not return one 1024-dimensional BGE-M3 vector.' }
    $taskSquaredNorm = 0.0
    foreach ($taskValue in $taskResult.embeddings[0]) {
        $taskNumber = [double]$taskValue
        if ([double]::IsNaN($taskNumber) -or [double]::IsInfinity($taskNumber)) { throw 'Warmup returned a non-finite vector.' }
        $taskSquaredNorm += $taskNumber * $taskNumber
    }
    if ($taskSquaredNorm -lt 0.99 -or $taskSquaredNorm -gt 1.01) { throw 'Warmup returned a vector that is not normalized.' }
    Write-Output "Embedding dimensions: $($taskResult.embeddings[0].Count)"
}
