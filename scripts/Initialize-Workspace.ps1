[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$ApkPath,

    [string]$PrivateRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) '..\BetaSafe-private')
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

$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$resolvedPrivateRoot = [IO.Path]::GetFullPath($PrivateRoot)
$repoPrefix = $repoRoot.TrimEnd('\') + '\'
if ($resolvedPrivateRoot.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $resolvedPrivateRoot.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'PrivateRoot must be outside the Git repository.'
}

if (Test-Path -LiteralPath $resolvedPrivateRoot) {
    $existing = @(Get-ChildItem -LiteralPath $resolvedPrivateRoot -Force)
    if ($existing.Count -gt 0) {
        throw "PrivateRoot already exists and is not empty: $resolvedPrivateRoot"
    }
} else {
    New-Item -ItemType Directory -Path $resolvedPrivateRoot | Out-Null
}

$jadx = Resolve-RequiredCommand -Name 'jadx'
$apktool = Resolve-RequiredCommand -Name 'apktool'
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$apktoolRoot = Join-Path $resolvedPrivateRoot 'apktool'
$jadxRoot = Join-Path $resolvedPrivateRoot 'jadx'

& $apktool d $resolvedApk -o $apktoolRoot
if ($LASTEXITCODE -ne 0) {
    throw "APKTool decode failed with exit code $LASTEXITCODE."
}

& $jadx -d $jadxRoot $resolvedApk
$jadxExit = $LASTEXITCODE
$jadxJavaFiles = @(Get-ChildItem -LiteralPath (Join-Path $jadxRoot 'sources') -Filter '*.java' -File -Recurse -ErrorAction SilentlyContinue)
if ($jadxExit -ne 0 -and $jadxJavaFiles.Count -eq 0) {
    throw "JADX failed with exit code $jadxExit and produced no Java sources."
}
if ($jadxExit -ne 0) {
    Write-Warning "JADX returned $jadxExit but produced $($jadxJavaFiles.Count) Java files. Use smali for failed methods."
}

$metadata = [ordered]@{
    sourcePath = $resolvedApk
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedApk).Hash.ToLowerInvariant()
    createdUtc = [DateTime]::UtcNow.ToString('o')
    apktoolPath = $apktool
    jadxPath = $jadx
    jadxExitCode = $jadxExit
    jadxJavaFiles = $jadxJavaFiles.Count
}
$metadataJson = $metadata | ConvertTo-Json -Depth 3
$utf8NoBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $resolvedPrivateRoot 'artifact-metadata.json'), $metadataJson, $utf8NoBom)

Write-Host "Private workspace ready: $resolvedPrivateRoot"
