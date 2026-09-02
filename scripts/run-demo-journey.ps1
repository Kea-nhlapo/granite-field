$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repositoryRoot 'apps\backend'

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker is not running. Start Docker Desktop, then run this script again.'
}

Push-Location $backendRoot
try {
    & .\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=MvpJourneyIntegrationTest' test
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Write-Host 'Two consecutive backend demo journeys completed successfully.'
}
finally {
    Pop-Location
}
