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
    throw "Скачайте configuration.yaml из GrowerHub и поместите его в папку data."
}

if (-not (Test-Path -LiteralPath $secretPath)) {
    throw "Скачайте secret.yaml из GrowerHub и поместите его в папку data."
}

if ([string]::IsNullOrWhiteSpace($Port)) {
    $ports = @(Get-CimInstance Win32_SerialPort -ErrorAction SilentlyContinue | Sort-Object DeviceID)
    if ($ports.Count -gt 0) {
        Write-Host "Доступные последовательные порты:"
        for ($index = 0; $index -lt $ports.Count; $index++) {
            Write-Host ("[{0}] {1} - {2}" -f ($index + 1), $ports[$index].DeviceID, $ports[$index].Name)
        }
        $selection = Read-Host "Введите номер порта"
        if ($selection -notmatch '^\d+$' -or [int]$selection -lt 1 -or [int]$selection -gt $ports.Count) {
            throw "Выбран неверный последовательный порт."
        }
        $Port = $ports[[int]$selection - 1].DeviceID
    } else {
        $Port = Read-Host "Введите COM-порт, например COM7"
    }
}

if ($Port -notmatch '^COM\d+$') {
    throw "Последовательный порт должен выглядеть как COM7."
}

if ([string]::IsNullOrWhiteSpace($Adapter)) {
    $adapterChoice = Read-Host "Адаптер: 1 — Z-Stack (ZBDongle-P), 2 — Ember (ZBDongle-E)"
    $Adapter = if ($adapterChoice -eq "2") { "ember" } elseif ($adapterChoice -eq "1") { "zstack" } else { $null }
}

if ($Adapter -notin @("zstack", "ember")) {
    throw "Тип адаптера должен быть zstack или ember."
}

$configuration = Get-Content -LiteralPath $configurationPath -Raw -Encoding UTF8
if ($configuration -notmatch 'CHANGE_ME_SERIAL_PORT|(?m)^\s*port:\s*COM\d+\s*$') {
    throw "В configuration.yaml нет поддерживаемого поля serial.port."
}
if ($configuration -notmatch 'CHANGE_ME_ADAPTER|(?m)^\s*adapter:\s*(zstack|ember)\s*$') {
    throw "В configuration.yaml нет поддерживаемого поля serial.adapter."
}

$configuration = $configuration -replace 'CHANGE_ME_SERIAL_PORT', $Port
$configuration = $configuration -replace 'CHANGE_ME_ADAPTER', $Adapter
$configuration = $configuration -replace '(?m)^(\s*port:\s*)COM\d+\s*$', "`${1}$Port"
$configuration = $configuration -replace '(?m)^(\s*adapter:\s*)(zstack|ember)\s*$', "`${1}$Adapter"
Set-Content -LiteralPath $configurationPath -Value $configuration -Encoding UTF8

Write-Host "Координатор настроен: $Port, $Adapter."
Write-Host "Теперь запустите start-coordinator.bat."
