[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$assetDirectory = Join-Path $repositoryRoot 'app\src\main\assets'
$badgeDirectory = Join-Path $assetDirectory 'achievement_badges'
$modelNames = @('320n.onnx', '320n_fp16.onnx')
$badgeNames = @(
    'badge_bronze.png',
    'badge_dedicated.png',
    'badge_diamond.png',
    'badge_gold.png',
    'badge_legend.png',
    'badge_newcomer.png',
    'badge_silver.png',
    'badge_veteran.png'
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Directory]::CreateDirectory($assetDirectory) | Out-Null
[System.IO.Directory]::CreateDirectory($badgeDirectory) | Out-Null

$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    foreach ($modelName in $modelNames) {
        $entryName = "assets/$modelName"
        $entry = $archive.GetEntry($entryName)
        if ($null -eq $entry) {
            throw "Licensed APK does not contain $entryName"
        }

        $destination = Join-Path $assetDirectory $modelName
        $sourceStream = $entry.Open()
        try {
            $destinationStream = [System.IO.File]::Create($destination)
            try {
                $sourceStream.CopyTo($destinationStream)
            }
            finally {
                $destinationStream.Dispose()
            }
        }
        finally {
            $sourceStream.Dispose()
        }

        $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-Host "Imported private model $modelName ($hash)"
    }

    foreach ($badgeName in $badgeNames) {
        $entryName = "assets/badges/$badgeName"
        $entry = $archive.GetEntry($entryName)
        if ($null -eq $entry) {
            throw "Licensed APK does not contain $entryName"
        }

        $destination = Join-Path $badgeDirectory $badgeName
        $sourceStream = $entry.Open()
        try {
            $destinationStream = [System.IO.File]::Create($destination)
            try {
                $sourceStream.CopyTo($destinationStream)
            }
            finally {
                $destinationStream.Dispose()
            }
        }
        finally {
            $sourceStream.Dispose()
        }

        $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-Host "Imported private achievement badge $badgeName ($hash)"
    }
}
finally {
    $archive.Dispose()
}

Write-Host 'Model and achievement badge assets are ignored by Git and remain local to this checkout.'
