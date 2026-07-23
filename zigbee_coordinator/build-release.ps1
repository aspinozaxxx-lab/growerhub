param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^v\d+\.\d+\.\d+$')]
    [string]$Version,

    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$repositoryRoot = Split-Path -Parent $root
$outputRoot = [System.IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

function Reset-Stage([string]$Path) {
    $resolved = [System.IO.Path]::GetFullPath($Path)
    $safePrefix = $outputRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($safePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Release stage must stay inside $outputRoot"
    }
    if (Test-Path -LiteralPath $resolved) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    New-Item -ItemType Directory -Path $resolved -Force | Out-Null
    return $resolved
}

function Assert-NoSecrets([string]$Path) {
    $forbidden = Get-ChildItem -LiteralPath $Path -Recurse -File | Where-Object {
        $_.Name -match '^secret\.ya?ml$' `
            -or $_.Name -eq '.env' `
            -or $_.Name -eq 'bridge.conf' `
            -or $_.Name -match 'credentials'
    }
    if ($forbidden) {
        throw "Release contains a secret or personal configuration file."
    }
}

function Compress-Release([string]$Stage, [string]$Name) {
    Assert-NoSecrets $Stage
    $archive = Join-Path $outputRoot "$Name-$Version.zip"
    if (Test-Path -LiteralPath $archive) {
        Remove-Item -LiteralPath $archive -Force
    }
    Compress-Archive -LiteralPath $Stage -DestinationPath $archive -CompressionLevel Optimal
    Write-Host "Created $archive"
}

$windowsName = "growerhub-coordinator-windows"
$windowsStage = Reset-Stage (Join-Path $outputRoot "$windowsName-$Version")
New-Item -ItemType Directory -Path (Join-Path $windowsStage "data") -Force | Out-Null

$trackedFiles = @(& git -C $repositoryRoot ls-files -- "zigbee_coordinator/zigbee2mqtt" "zigbee_coordinator/bin")
if ($LASTEXITCODE -ne 0 -or $trackedFiles.Count -eq 0) {
    throw "Cannot read tracked coordinator files from git."
}
foreach ($trackedPath in $trackedFiles) {
    $relativePath = $trackedPath.Substring("zigbee_coordinator/".Length)
    $sourcePath = Join-Path $repositoryRoot $trackedPath
    $destinationPath = Join-Path $windowsStage $relativePath
    $destinationDirectory = Split-Path -Parent $destinationPath
    New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
    Copy-Item -LiteralPath $sourcePath -Destination $destinationPath
}
foreach ($source in @(
    "start-coordinator.bat",
    "status-coordinator.bat",
    "stop-coordinator.bat",
    "packages/windows/setup-coordinator.ps1",
    "packages/windows/setup-coordinator.bat",
    "packages/windows/README.txt"
)) {
    Copy-Item -LiteralPath (Join-Path $root $source) -Destination $windowsStage
}
Compress-Release $windowsStage $windowsName

$linuxName = "growerhub-coordinator-linux"
$linuxStage = Reset-Stage (Join-Path $outputRoot "$linuxName-$Version")
foreach ($source in @("docker-compose.yml", ".env.example", "README.md")) {
    Copy-Item -LiteralPath (Join-Path $root "packages/linux/$source") -Destination $linuxStage
}
New-Item -ItemType Directory -Path (Join-Path $linuxStage "data") -Force | Out-Null
Compress-Release $linuxStage $linuxName

$connectorName = "growerhub-zigbee-connector"
$connectorStage = Reset-Stage (Join-Path $outputRoot "$connectorName-$Version")
foreach ($source in @("docker-compose.yml", "mosquitto.conf", "mosquitto-bridge.conf.example", "README.md")) {
    Copy-Item -LiteralPath (Join-Path $root "connector/$source") -Destination $connectorStage
}
Compress-Release $connectorStage $connectorName
