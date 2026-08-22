[CmdletBinding()]
param(
    [string]$PrivateRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) '..\BetaSafe-private'),
    [string]$KeystorePath,
    [string]$KeyAlias
)

$ErrorActionPreference = 'Stop'

function Resolve-RequiredCommand {
    param([Parameter(Mandatory = $true)][string]$Name)

    $commandInfo = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $commandInfo) {
        throw "Required command is not available on PATH: $Name"
    }
    return $commandInfo.Source
}

$resolvedPrivateRoot = [IO.Path]::GetFullPath($PrivateRoot)
$decodeRoot = Join-Path $resolvedPrivateRoot 'apktool'
if (-not (Test-Path -LiteralPath (Join-Path $decodeRoot 'apktool.yml') -PathType Leaf)) {
    throw "No APKTool workspace found at: $decodeRoot"
}

$apktool = Resolve-RequiredCommand -Name 'apktool'
$zipalign = Resolve-RequiredCommand -Name 'zipalign'
$apksigner = Resolve-RequiredCommand -Name 'apksigner'
$buildRoot = Join-Path $resolvedPrivateRoot 'build'
if (-not (Test-Path -LiteralPath $buildRoot)) {
    New-Item -ItemType Directory -Path $buildRoot | Out-Null
}

$unsignedApk = Join-Path $buildRoot 'betasafe-unsigned.apk'
$alignedApk = Join-Path $buildRoot 'betasafe-aligned.apk'
$signedApk = Join-Path $buildRoot 'betasafe-local-signed.apk'

& $apktool b $decodeRoot -o $unsignedApk
if ($LASTEXITCODE -ne 0) {
    throw "APKTool build failed with exit code $LASTEXITCODE."
}

& $zipalign -p -f 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) {
    throw "zipalign failed with exit code $LASTEXITCODE."
}
& $zipalign -c -p 4 $alignedApk
if ($LASTEXITCODE -ne 0) {
    throw "zipalign verification failed with exit code $LASTEXITCODE."
}

if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
    Write-Warning 'No KeystorePath supplied. The aligned APK is unsigned.'
    Write-Host "Aligned APK: $alignedApk"
    return
}
if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
    throw 'KeyAlias is required when KeystorePath is supplied.'
}
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Keystore does not exist: $KeystorePath"
}
if ([string]::IsNullOrWhiteSpace($env:BETASAFE_KEYSTORE_PASSWORD)) {
    throw 'Set BETASAFE_KEYSTORE_PASSWORD in the current process before signing.'
}

$signArgs = @(
    'sign',
    '--ks', (Resolve-Path -LiteralPath $KeystorePath).Path,
    '--ks-key-alias', $KeyAlias,
    '--ks-pass', 'env:BETASAFE_KEYSTORE_PASSWORD'
)
if (-not [string]::IsNullOrWhiteSpace($env:BETASAFE_KEY_PASSWORD)) {
    $signArgs += @('--key-pass', 'env:BETASAFE_KEY_PASSWORD')
}
$signArgs += @('--out', $signedApk, $alignedApk)

& $apksigner @signArgs
if ($LASTEXITCODE -ne 0) {
    throw "APK signing failed with exit code $LASTEXITCODE."
}
& $apksigner verify --verbose $signedApk
if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed with exit code $LASTEXITCODE."
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $signedApk).Hash.ToLowerInvariant()
Write-Host "Signed APK: $signedApk"
Write-Host "SHA-256: $hash"
