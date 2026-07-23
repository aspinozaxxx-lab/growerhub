param(
    [Parameter(Mandatory = $false)]
    [string]$Port,

    [Parameter(Mandatory = $false)]
    [ValidateSet("zstack", "ember")]
    [string]$Adapter
)

$ErrorActionPreference = "Stop"
$packageRoot = $PSScriptRoot
$dataDir = Join-Path $packageRoot "data"
$configurationPath = Join-Path $dataDir "configuration.yaml"
$secretPath = Join-Path $dataDir "secret.yaml"

if (-not (Test-Path -LiteralPath $configurationPath)) {
    throw "Download configuration.yaml from GrowerHub and place it into the data folder."
}

if (-not (Test-Path -LiteralPath $secretPath)) {
    throw "Download secret.yaml from GrowerHub and place it into the data folder."
}

if ([string]::IsNullOrWhiteSpace($Port)) {
    $ports = @(Get-CimInstance Win32_SerialPort -ErrorAction SilentlyContinue | Sort-Object DeviceID)
    if ($ports.Count -gt 0) {
        Write-Host "Available serial ports:"
        for ($index = 0; $index -lt $ports.Count; $index++) {
            Write-Host ("[{0}] {1} - {2}" -f ($index + 1), $ports[$index].DeviceID, $ports[$index].Name)
        }
        $selection = Read-Host "Select port number"
        if ($selection -notmatch '^\d+$' -or [int]$selection -lt 1 -or [int]$selection -gt $ports.Count) {
            throw "Invalid serial port selection."
        }
        $Port = $ports[[int]$selection - 1].DeviceID
    } else {
        $Port = Read-Host "Enter COM port, for example COM7"
    }
}

if ($Port -notmatch '^COM\d+$') {
    throw "Serial port must look like COM7."
}

if ([string]::IsNullOrWhiteSpace($Adapter)) {
    $adapterChoice = Read-Host "Adapter: 1 - Z-Stack (ZBDongle-P), 2 - Ember (ZBDongle-E)"
    $Adapter = if ($adapterChoice -eq "2") { "ember" } elseif ($adapterChoice -eq "1") { "zstack" } else { $null }
}

if ($Adapter -notin @("zstack", "ember")) {
    throw "Adapter must be zstack or ember."
}

$configuration = Get-Content -LiteralPath $configurationPath -Raw -Encoding UTF8
if ($configuration -notmatch 'CHANGE_ME_SERIAL_PORT|(?m)^\s*port:\s*COM\d+\s*$') {
    throw "configuration.yaml has no supported serial.port field."
}
if ($configuration -notmatch 'CHANGE_ME_ADAPTER|(?m)^\s*adapter:\s*(zstack|ember)\s*$') {
    throw "configuration.yaml has no supported serial.adapter field."
}

$configuration = $configuration -replace 'CHANGE_ME_SERIAL_PORT', $Port
$configuration = $configuration -replace 'CHANGE_ME_ADAPTER', $Adapter
$configuration = $configuration -replace '(?m)^(\s*port:\s*)COM\d+\s*$', "`${1}$Port"
$configuration = $configuration -replace '(?m)^(\s*adapter:\s*)(zstack|ember)\s*$', "`${1}$Adapter"
Set-Content -LiteralPath $configurationPath -Value $configuration -Encoding UTF8

Write-Host "Coordinator configured: $Port, $Adapter."
Write-Host "Run start-coordinator.bat."
