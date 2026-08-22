[CmdletBinding()]
param(
    [string]$PrivateRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) '..\BetaSafe-private')
)

$ErrorActionPreference = 'Stop'

$resolvedPrivateRoot = [IO.Path]::GetFullPath($PrivateRoot)
$smaliRoot = Join-Path $resolvedPrivateRoot 'apktool\smali'
if (-not (Test-Path -LiteralPath $smaliRoot -PathType Container)) {
    throw "No decoded smali tree found at: $smaliRoot"
}

$rgInfo = Get-Command 'rg' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $rgInfo) {
    throw 'Required command is not available on PATH: rg'
}

$indicators = @(
    'Lcom/android/billingclient',
    'BillingClient',
    'launchBillingFlow',
    'queryPurchases',
    'ProductDetails',
    'SkuDetails',
    'purchaseToken',
    'subscriptionOfferDetails',
    'isPremium',
    'premiumEnabled',
    'hasPremium',
    'isProUser',
    'hasProAccess',
    'paywall',
    'entitlement',
    'licenseCheck',
    'verifyPurchase'
)

$findings = @()
foreach ($indicator in $indicators) {
    $matchesForIndicator = @(& $rgInfo.Source -n -i -F --glob '*.smali' $indicator $smaliRoot 2>$null)
    $rgExit = $LASTEXITCODE
    if ($rgExit -notin @(0, 1)) {
        throw "rg failed while checking '$indicator' with exit code $rgExit."
    }
    foreach ($matchLine in $matchesForIndicator) {
        $findings += [pscustomobject]@{
            Indicator = $indicator
            Evidence = $matchLine
        }
    }
}

if ($findings.Count -gt 0) {
    $summary = @($findings | Group-Object Indicator | ForEach-Object {
        "$($_.Name)=$($_.Count)"
    }) -join ', '
    throw "Monetization-gate audit failed: $summary. Review the decoded tree before building."
}

Write-Host "Monetization-gate audit passed: $($indicators.Count) indicators absent."
