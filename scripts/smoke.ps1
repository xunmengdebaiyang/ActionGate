param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [ValidateRange(1, 300)][int]$TimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$ApiBaseUrl = $ApiBaseUrl.TrimEnd('/')

function Wait-Run([string]$RunId) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        $run = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/runs/$RunId" -TimeoutSec 5
        if ($run.status -ne 'RUNNING') { return $run }
        Start-Sleep -Milliseconds 150
    } while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds)
    throw "Run $RunId did not finish within $TimeoutSeconds seconds"
}

$cases = @(
    @{ scenario = 'ORDER_STATUS'; order_id = '10001'; outcome = 'ANSWERED'; reason = 'CONSULTATION_ANSWERED' },
    @{ scenario = 'REFUND_REQUEST'; order_id = '10001'; outcome = 'MANUAL_REQUIRED'; reason = 'REFUND_REQUIRES_HUMAN' },
    @{ scenario = 'EXCHANGE_REQUEST'; order_id = '10002'; outcome = 'MANUAL_REQUIRED'; reason = 'EXCHANGE_REQUIRES_HUMAN' },
    @{ scenario = 'INSUFFICIENT_INFORMATION'; order_id = '10001'; outcome = 'MANUAL_REQUIRED'; reason = 'INSUFFICIENT_INFORMATION' },
    @{ scenario = 'ORDER_STATUS'; order_id = '99999'; outcome = 'MANUAL_REQUIRED'; reason = 'ORDER_NOT_FOUND' },
    @{ scenario = 'ORDER_STATUS'; order_id = $null; outcome = 'MANUAL_REQUIRED'; reason = 'MISSING_ORDER_ID' }
)

foreach ($case in $cases) {
    $body = @{ scenario = $case.scenario; order_id = $case.order_id } | ConvertTo-Json -Compress
    $accepted = Invoke-WebRequest -Method Post -Uri "$ApiBaseUrl/api/v1/runs" -ContentType 'application/json' -Body $body -TimeoutSec 10 -UseBasicParsing
    if ($accepted.StatusCode -ne 202) { throw 'Expected HTTP 202 Accepted' }
    $receipt = $accepted.Content | ConvertFrom-Json
    $run = Wait-Run $receipt.run_id
    if ($run.status -ne 'COMPLETED' -or $run.result.outcome -ne $case.outcome -or $run.result.reason -ne $case.reason) {
        throw "Unexpected outcome for $($case.scenario): $($run | ConvertTo-Json -Compress -Depth 6)"
    }
    [pscustomobject]@{ scenario = $case.scenario; run_id = $receipt.run_id; outcome = $run.result.outcome; reason = $run.result.reason }
}
