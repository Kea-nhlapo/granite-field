[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$CollectionsSubscriptionKey,

    [Parameter(Mandatory = $true)]
    [string]$DisbursementsSubscriptionKey,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^http://')]
    [string]$CallbackHost,

    [string]$BaseUrl = 'https://sandbox.momodeveloper.mtn.com'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function New-MomoSandboxCredential {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Product,

        [Parameter(Mandatory = $true)]
        [string]$SubscriptionKey
    )

    $apiUser = [guid]::NewGuid().ToString()
    $headers = @{
        'Ocp-Apim-Subscription-Key' = $SubscriptionKey
        'X-Reference-Id' = $apiUser
    }
    $body = @{ providerCallbackHost = $CallbackHost } | ConvertTo-Json -Compress
    $createArgs = @{
        Method = 'Post'
        Uri = "$BaseUrl/v1_0/apiuser"
        Headers = $headers
        ContentType = 'application/json'
        Body = $body
    }
    Invoke-RestMethod @createArgs | Out-Null

    $keyArgs = @{
        Method = 'Post'
        Uri = "$BaseUrl/v1_0/apiuser/$apiUser/apikey"
        Headers = @{ 'Ocp-Apim-Subscription-Key' = $SubscriptionKey }
    }
    $keyResponse = Invoke-RestMethod @keyArgs

    [pscustomobject]@{
        product = $Product
        apiUser = $apiUser
        apiKey = $keyResponse.apiKey
    }
}

$credentials = @(
    New-MomoSandboxCredential -Product 'collections' -SubscriptionKey $CollectionsSubscriptionKey
    New-MomoSandboxCredential -Product 'disbursements' -SubscriptionKey $DisbursementsSubscriptionKey
)

$credentials | ConvertTo-Json
