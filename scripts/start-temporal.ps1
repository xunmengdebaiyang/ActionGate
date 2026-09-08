param(
    [string]$TemporalCommand = "temporal",
    [ValidateRange(1, 65535)][int]$Port = 7233,
    [ValidateRange(1, 65535)][int]$UiPort = 8233
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$stateDirectory = Join-Path $repoRoot '.local/temporal'
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
$database = Join-Path $stateDirectory 'temporal.db'
& $TemporalCommand server start-dev --ip 127.0.0.1 --ui-ip 127.0.0.1 --port $Port --ui-port $UiPort --db-filename $database --ui-disable-news-fetch
exit $LASTEXITCODE
