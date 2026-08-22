[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,

    [Parameter(Mandatory = $true)]
    [string]$KeyAlias,

    [string]$DistinguishedName = 'CN=BetaSafe Local Debug,O=Local Development,C=DE',
    [int]$ValidityDays = 3650
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:BETASAFE_KEYSTORE_PASSWORD)) {
    throw 'Set BETASAFE_KEYSTORE_PASSWORD in the current process before creating a key.'
}
if ([string]::IsNullOrWhiteSpace($env:BETASAFE_KEY_PASSWORD)) {
    throw 'Set BETASAFE_KEY_PASSWORD in the current process before creating a key.'
}
if (Test-Path -LiteralPath $KeystorePath) {
    throw "Refusing to overwrite an existing keystore: $KeystorePath"
}

$keytoolInfo = Get-Command 'keytool' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $keytoolInfo) {
    throw 'Required command is not available on PATH: keytool'
}

$parent = Split-Path -Parent ([IO.Path]::GetFullPath($KeystorePath))
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}

& $keytoolInfo.Source -genkeypair -noprompt `
    -keystore $KeystorePath `
    -storetype PKCS12 `
    -alias $KeyAlias `
    -keyalg RSA `
    -keysize 2048 `
    -validity $ValidityDays `
    -dname $DistinguishedName `
    '-storepass:env' BETASAFE_KEYSTORE_PASSWORD `
    '-keypass:env' BETASAFE_KEY_PASSWORD

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE."
}

Write-Host "Local signing key created: $([IO.Path]::GetFullPath($KeystorePath))"
