# Design-artifact checks only. Does not execute OrangeJD or evaluate a model.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$designRoot = $PSScriptRoot
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $designRoot '../../..'))
$checks = New-Object 'System.Collections.Generic.List[object]'
function Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $checks.Add([pscustomobject]@{name=$Name; passed=$Passed; detail=$Detail})
}
function Prop($Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}
function Clone($Object) { return ($Object | ConvertTo-Json -Depth 60 | ConvertFrom-Json) }
function Merge($Target, $Patch) {
    foreach ($property in $Patch.PSObject.Properties) {
        $name = $property.Name
        $value = $property.Value
        if ($null -eq $value) { $Target.PSObject.Properties.Remove($name); continue }
        if ($value -is [pscustomobject]) {
            $prior = Prop $Target $name
            if ($prior -isnot [pscustomobject]) {
                $prior = [pscustomobject]@{}
                $Target | Add-Member -NotePropertyName $name -NotePropertyValue $prior -Force
            }
            Merge $prior $value
        } else {
            $Target | Add-Member -NotePropertyName $name -NotePropertyValue $value -Force
        }
    }
}
$docs = @('01-routing-spec.md','02-state-contract.md','03-clarification-policy.md','conversation-cases.json','04-implementation-map.md','acceptance.md')
foreach ($name in $docs) { Check "file:$name" (Test-Path -LiteralPath (Join-Path $designRoot $name) -PathType Leaf) 'Required deliverable' }
$routes = @{CHAT='DIRECT'; UNKNOWN='DIRECT'; KNOWLEDGE_QA='KNOWLEDGE'; FIND_PROJECT='RECOMMEND'; PROJECT_DETAIL='PROJECT_REVIEW'; COMPARE_PROJECTS='PROJECT_REVIEW'; TASK_STATUS='INSPECT'; PROJECT_ACTION='ACTION'}
$taskStates = @('DISCOVERING','WAITING_USER','READY_TO_SEARCH','EVALUATING','PRESENTED','PAUSED','COMPLETED','CANCELLED')
$questionStates = @('OPEN','ANSWERED','SKIPPED','SUPERSEDED','CANCELLED')
$outcomes = @('ANSWERED','NEEDS_CLARIFICATION','RECOMMENDATIONS','NO_MATCH','COVERAGE_GAP','EVIDENCE_INCOMPLETE','SERVICE_UNAVAILABLE','LIMIT_REACHED','STATE_RECORDED','ACTION_SUCCEEDED','ACTION_FAILED')
$actions = @('ASK_REQUIREMENT','ANSWER','SEARCH_LOCAL','FETCH_EVIDENCE','COMPARE','SELECT','ASK_TRADEOFF','REEVALUATE','CHECK_ACTION_ELIGIBILITY','ASSESS_READINESS','CLARIFY_REFERENCE','RECORD_ONLY','PAUSE','CANCEL','SEARCH_GITHUB','EXPLAIN_NO_MATCH','REPORT_FAILURE','RETURN_PREVIOUS','REJECT','SEARCH_KNOWLEDGE','EXPLAIN_STATE','REPORT_LIMIT')
$caseCount = 0; $turnCount = 0
$caseIds = @{}; $turnIds = @{}; $coveredAc = @{}; $seenRoutes = @{}
try {
    $fixture = Get-Content -LiteralPath (Join-Path $designRoot 'conversation-cases.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Check 'fixture:design-status' ($fixture.status -eq 'DESIGN_FIXTURES_NOT_RUNTIME_RESULTS') 'Must not claim observed runtime results'
    foreach ($scenario in $fixture.scenarios) {
        $caseCount++
        Check "case-id:$($scenario.id)" (-not $caseIds.ContainsKey($scenario.id)) 'Unique scenario ID'
        $caseIds[$scenario.id] = $true
        foreach ($ac in $scenario.ac) { Check "ac:$($scenario.id):$ac" ($ac -match '^AC-0[1-8]$') 'Known acceptance ID'; $coveredAc[$ac]=$true }
        Check "case-turns:$($scenario.id)" (@($scenario.turns).Count -gt 0) 'Scenario has turns'
        $state = Clone $scenario.initialState
        foreach ($turn in $scenario.turns) {
            $turnCount++
            $id = $turn.id
            Check "turn-id:$id" (-not $turnIds.ContainsKey($id)) 'Unique message ID'
            $turnIds[$id] = $true
            foreach ($field in @('message','serviceFixture')) { Check "field:${id}:$field" (-not [string]::IsNullOrWhiteSpace((Prop $turn $field))) 'Input and fixture must be explicit' }
            $expected = $turn.expect
            foreach ($field in @('route','reference','nextAction','replyType')) { Check "expected:${id}:$field" (-not [string]::IsNullOrWhiteSpace((Prop $expected $field))) 'Explicit expected behavior' }
            Check "forbidden:$id" (@($expected.forbidden).Count -gt 0) 'Negative assertion required'
            Check "patch:$id" ($expected.patch -is [pscustomobject]) 'Merge patch object required'
            Check "outcome:$id" ($outcomes -contains $expected.replyType) 'Known response type'
            Check "action:$id" ($actions -contains $expected.nextAction) 'Known planned action'
            $route = $expected.route; $intent = $expected.intent
            $seenRoutes[$route] = $true
            if ($route -in @('REPLAY','REJECT')) {
                Check "preflight:$id" ($null -eq $intent) 'No model interpretation after a preflight exit'
            } else {
                $known = $null -ne $intent -and $routes.ContainsKey($intent)
                Check "intent:$id" $known 'Known intent'
                if ($known) {
                    $guard = $route -eq 'DIRECT' -and $expected.nextAction -in @('CLARIFY_REFERENCE','RECORD_ONLY','PAUSE','CANCEL')
                    Check "route:$id" ($routes[$intent] -eq $route -or $guard) 'Intent route or documented preflight/control override'
                }
            }
            $before = Clone $state
            Merge $state $expected.patch
            Check "task-state:$id" ($taskStates -contains $state.taskStatus) 'Known task state'
            if ((Prop $before 'task') -eq (Prop $state 'task')) {
                $oldRequirements = $before.requirements | ConvertTo-Json -Compress -Depth 30
                $newRequirements = $state.requirements | ConvertTo-Json -Compress -Depth 30
                Check "version-order:$id" ($state.version -ge $before.version) 'Same-task version cannot decrease'
                if ($oldRequirements -ne $newRequirements) { Check "version-change:$id" ($state.version -gt $before.version) 'Changed effective requirements advance version' }
            }
            $question = Prop $state 'question'
            if ($null -ne $question) { Check "question-state:$id" ($questionStates -contains $question.status) 'Known question lifecycle state' }
            if ($state.taskStatus -eq 'WAITING_USER') { Check "waiting-question:$id" ((Prop $question 'status') -eq 'OPEN') 'Waiting requires an open main question in this projection' }
            $list = Prop $state 'list'
            if ($null -ne $list -and $state.taskStatus -eq 'PRESENTED') {
                Check "list-version:$id" ($list.version -eq $state.version) 'Presented list evaluated at current requirements version'
                Check "list-nonempty:$id" (@($list.projects).Count -gt 0) 'Presented candidates are nonempty'
            }
            if ($route -in @('REPLAY','REJECT','INSPECT') -or $intent -eq 'CHAT') {
                Check "read-only:$id" (($before | ConvertTo-Json -Compress -Depth 40) -eq ($state | ConvertTo-Json -Compress -Depth 40)) 'This read-only fixture must not mutate task state'
            }
        }
    }
    foreach ($ac in @('AC-01','AC-02','AC-03','AC-04','AC-05','AC-06','AC-08')) { Check "coverage:$ac" ($coveredAc.ContainsKey($ac)) 'Covered by conversation fixtures; AC-07 uses implementation map' }
    foreach ($route in @('DIRECT','KNOWLEDGE','RECOMMEND','PROJECT_REVIEW','INSPECT','ACTION')) { Check "route-coverage:$route" ($seenRoutes.ContainsKey($route)) 'Six workflow routes represented' }
} catch { Check 'fixture:parse-or-structure' $false $_.Exception.Message }
foreach ($name in $docs | Where-Object { $_ -like '*.md' }) {
    $content = Get-Content -LiteralPath (Join-Path $designRoot $name) -Raw -Encoding UTF8
    foreach ($match in [regex]::Matches($content, '\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Split('#')[0]
        if ($target -and $target -notmatch '^(https?:|/)') {
            Check "link:${name}:$target" (Test-Path -LiteralPath (Join-Path $designRoot $target)) 'Local document link exists'
        }
    }
}
$mapping = Get-Content -LiteralPath (Join-Path $designRoot '04-implementation-map.md') -Raw -Encoding UTF8
foreach ($match in [regex]::Matches($mapping, '(?:orange-server|orange-web)/[^\s|`]+\.(?:java|vue)')) {
    $source = $match.Value
    Check "source:$source" (Test-Path -LiteralPath (Join-Path $repoRoot $source) -PathType Leaf) 'Mapped existing source path exists'
}
$acceptance = Get-Content -LiteralPath (Join-Path $designRoot 'acceptance.md') -Raw -Encoding UTF8
foreach ($n in 1..8) { $ac = 'AC-{0:D2}' -f $n; Check "matrix:$ac" ($acceptance.Contains($ac)) 'Acceptance matrix has stable ID' }
$failed = @($checks | Where-Object { -not $_.passed })
$report = [ordered]@{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    validationKind = 'DESIGN_STRUCTURE_AND_EXPECTED_STATE_CONSISTENCY'
    scenarioCount = $caseCount; turnCount = $turnCount
    checkCount = $checks.Count; passed = ($failed.Count -eq 0)
    failureCount = $failed.Count
    limitations = @('No application implementation was executed','Expected-state replay is not observed business behavior','No model, database, GitHub or retrieval evaluation','Semantic cases require human review')
    checks = @($checks.ToArray())
}
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $designRoot 'validation-report.json') -Encoding UTF8
[pscustomobject]@{scenarios=$caseCount; turns=$turnCount; checks=$checks.Count; failures=$failed.Count} | Format-List
if ($failed.Count -gt 0) { $failed | Format-Table -AutoSize; exit 1 }
