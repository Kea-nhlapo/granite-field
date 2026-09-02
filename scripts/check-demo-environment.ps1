[CmdletBinding()]
param(
    [ValidateSet('Local', 'Live')]
    [string]$Mode = 'Local'
)

$ErrorActionPreference = 'Stop'

function Read-Setting([string]$Name) {
    return [Environment]::GetEnvironmentVariable($Name)
}

function Require-Setting([System.Collections.Generic.List[string]]$Failures, [string]$Name) {
    $value = Read-Setting $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        $Failures.Add("$Name is missing")
    }
}

function Require-LiveProvider(
    [System.Collections.Generic.List[string]]$Failures,
    [string]$Name
) {
    $value = Read-Setting $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        $Failures.Add("$Name is missing")
        return
    }

    if ($value.ToLowerInvariant() -in @('local', 'mock', 'unconfigured')) {
        $Failures.Add("$Name is set to '$value', which is not a live provider")
    }
}

if ($Mode -eq 'Local') {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker is not running. Start Docker Desktop, then try again.'
    }

    Write-Host 'Local demo dependencies are ready.'
    exit 0
}

$failures = [System.Collections.Generic.List[string]]::new()

@(
    'AUTH_JWT_SECRET',
    'NOTIFICATION_DATA_ENCRYPTION_KEY',
    'HANDOVER_QR_SIGNING_SECRET',
    'CLOUDFLARE_TURNSTILE_SECRET_KEY',
    'CLOUDFLARE_TURNSTILE_EXPECTED_HOSTNAME',
    'GOOGLE_MAPS_API_KEY',
    'MOMO_COLLECTIONS_SUBSCRIPTION_KEY',
    'MOMO_COLLECTIONS_API_USER',
    'MOMO_COLLECTIONS_API_KEY',
    'MOMO_DISBURSEMENTS_SUBSCRIPTION_KEY',
    'MOMO_DISBURSEMENTS_API_USER',
    'MOMO_DISBURSEMENTS_API_KEY'
) | ForEach-Object { Require-Setting $failures $_ }

@(
    'TURNSTILE_PROVIDER',
    'OTP_PROVIDER',
    'MOMO_PROVIDER',
    'ROUTING_PROVIDER',
    'MOBILE_NOTIFICATION_PROVIDER'
) | ForEach-Object { Require-LiveProvider $failures $_ }

if ($failures.Count -gt 0) {
    $details = $failures | ForEach-Object { " - $_" }
    throw "Live demo environment is not ready:`n$($details -join "`n")"
}

Write-Host 'Required live provider selections and secrets are present.'
Write-Host 'Run the manual provider checks in docs/operations/demo-readiness.md next.'
