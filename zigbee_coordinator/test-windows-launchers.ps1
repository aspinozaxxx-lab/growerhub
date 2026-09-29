$ErrorActionPreference = 'Stop'
$fixtureBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$fixtureRoot = Join-Path $fixtureBase ('growerhub-launchers-' + [guid]::NewGuid().ToString('N'))
$packageRoot = Join-Path $fixtureRoot "Test O'Neil ferma!"
$foreignRoot = Join-Path $fixtureRoot 'other-installation'
$ownedProcesses = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$previousTestPort = $env:GH_LAUNCHER_TEST_PORT
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Assert-Alive([System.Diagnostics.Process]$Process, [string]$Message) {
    $Process.Refresh()
    Assert-True (-not $Process.HasExited) $Message
}

function Wait-For([scriptblock]$Condition, [string]$Message) {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        if (& $Condition) { return }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw $Message
}

function Start-TestNode([string]$Script, [string]$ExtraArguments = '') {
    $process = Start-Process -FilePath (Get-Command node.exe).Source `
        -ArgumentList ('"' + $Script + '" ' + $ExtraArguments) `
        -WindowStyle Hidden -PassThru
    $ownedProcesses.Add($process)
    return $process
}

function Invoke-Launcher([string]$Name, [string]$Arguments = '--no-pause') {
    $callId = [guid]::NewGuid().ToString('N')
    $stdout = Join-Path $fixtureRoot ($callId + '.stdout')
    $stderr = Join-Path $fixtureRoot ($callId + '.stderr')
    $batch = Join-Path $packageRoot $Name
    $process = Start-Process -FilePath $env:ComSpec `
        -ArgumentList ('/d /s /c ""' + $batch + '" ' + $Arguments + ' <nul"') `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
        -WindowStyle Hidden -PassThru
    $ownedProcesses.Add($process)
    Assert-True ($process.WaitForExit(20000)) "Launcher timed out: $Name"
    $process.Refresh()
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Text = [System.IO.File]::ReadAllText($stdout, $utf8) + [System.IO.File]::ReadAllText($stderr, $utf8)
    }
}

try {
    foreach ($directory in @('data', 'bin', 'zigbee2mqtt/node_modules/source-map-support')) {
        New-Item -ItemType Directory -Path (Join-Path $packageRoot $directory) -Force | Out-Null
    }
    New-Item -ItemType Directory -Path (Join-Path $foreignRoot 'zigbee2mqtt') -Force | Out-Null

    $portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $portProbe.Start()
    $testPort = $portProbe.LocalEndpoint.Port
    $portProbe.Stop()
    $env:GH_LAUNCHER_TEST_PORT = [string]$testPort

    foreach ($name in @('start-coordinator.bat', 'stop-coordinator.bat', 'status-coordinator.bat')) {
        $content = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot $name), $utf8)
        $content = $content.Replace('set "FRONTEND_PORT=8080"', ('set "FRONTEND_PORT=' + $testPort + '"'))
        [System.IO.File]::WriteAllText((Join-Path $packageRoot $name), $content, $utf8)
    }
    [System.IO.File]::WriteAllText((Join-Path $packageRoot 'bin/corepack.cmd'), '@exit /b 0', $utf8)
    [System.IO.File]::WriteAllText((Join-Path $packageRoot 'data/configuration.yaml'), 'test: true', $utf8)
    [System.IO.File]::WriteAllText((Join-Path $packageRoot 'data/secret.yaml'), 'synthetic: no-network-credentials', $utf8)

    $ownEntry = Join-Path $packageRoot 'zigbee2mqtt/index.js'
    $markerPath = Join-Path $packageRoot 'zigbee2mqtt/started.json'
    $ownCode = @'
const fs = require('node:fs');
const path = require('node:path');
require('node:http').createServer((_, response) => response.end('synthetic fixture'))
  .listen(Number(process.env.GH_LAUNCHER_TEST_PORT), '127.0.0.1', () => {
    fs.writeFileSync(path.join(__dirname, 'started.json'), JSON.stringify({pid: process.pid, data: process.env.ZIGBEE2MQTT_DATA}));
  });
'@
    [System.IO.File]::WriteAllText($ownEntry, $ownCode, $utf8)
    $foreignEntry = Join-Path $foreignRoot 'zigbee2mqtt/index.js'
    [System.IO.File]::WriteAllText($foreignEntry, 'setInterval(() => {}, 1000);', $utf8)
    $foreignZigbee = Start-TestNode $foreignEntry
    $prefixEntry = $ownEntry + '.other'
    [System.IO.File]::WriteAllText($prefixEntry, 'setInterval(() => {}, 1000);', $utf8)
    $foreignPrefix = Start-TestNode $prefixEntry
    $foreignServerPath = Join-Path $fixtureRoot 'unrelated-server.js'
    [System.IO.File]::WriteAllText($foreignServerPath, "require('node:http').createServer().listen(Number(process.argv[2]), '127.0.0.1');", $utf8)
    $foreignServer = Start-TestNode $foreignServerPath ([string]$testPort)
    Wait-For { @(Get-NetTCPConnection -LocalPort $testPort -State Listen -ErrorAction SilentlyContinue).Count -gt 0 } 'Foreign fixture did not start'

    $stopped = Invoke-Launcher 'stop-coordinator.bat'
    Assert-True ($stopped.ExitCode -eq 0) $stopped.Text
    Assert-Alive $foreignServer 'Stop terminated an unrelated server'
    Assert-Alive $foreignZigbee 'Stop terminated another Zigbee installation'
    Assert-Alive $foreignPrefix 'Stop matched a script path prefix'

    $status = Invoke-Launcher 'status-coordinator.bat'
    Assert-True ($status.ExitCode -eq 2) ('Foreign listener must be reported as a conflict: ' + $status.Text)
    Assert-True (-not $status.Text.Contains([string]$foreignZigbee.Id)) 'Status reported another Zigbee installation'

    $blocked = Invoke-Launcher 'start-coordinator.bat' ''
    Assert-True ($blocked.ExitCode -ne 0) 'Start accepted a port occupied by an unrelated server'
    Assert-True (-not (Test-Path -LiteralPath $markerPath)) 'Coordinator started despite a foreign listener'
    Assert-Alive $foreignServer 'Start terminated an unrelated server'
    Assert-Alive $foreignZigbee 'Start terminated another Zigbee installation'
    Assert-Alive $foreignPrefix 'Start matched a script path prefix'
    Write-Output 'PASS: foreign Node.js listener, other Zigbee installation and path prefix preserved'

    Stop-Process -InputObject $foreignServer -Force
    Wait-For { @(Get-NetTCPConnection -LocalPort $testPort -State Listen -ErrorAction SilentlyContinue).Count -eq 0 } 'Fixture port did not become free'
    $started = Invoke-Launcher 'start-coordinator.bat' ''
    Assert-True ($started.ExitCode -eq 0) $started.Text
    Wait-For { Test-Path -LiteralPath $markerPath } ('Quoted-path coordinator did not start: ' + $started.Text)
    $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    $ownProcess = Get-Process -Id $marker.pid
    $ownedProcesses.Add($ownProcess)
    Assert-True ($marker.data.TrimEnd('\') -eq (Join-Path $packageRoot 'data')) 'Data directory changed in a path with spaces, apostrophe or exclamation mark'
    $running = Invoke-Launcher 'status-coordinator.bat'
    Assert-True ($running.ExitCode -eq 0) $running.Text
    Assert-True ($running.Text.Contains([string]$ownProcess.Id)) 'Status did not identify the current installation'
    Assert-Alive $foreignZigbee 'Start terminated another Zigbee installation'
    Assert-Alive $foreignPrefix 'Start matched a script path prefix'
    Write-Output 'PASS: launch and status work from a path with spaces, apostrophe and exclamation mark'

    Remove-Item -LiteralPath $markerPath
    $restarted = Invoke-Launcher 'start-coordinator.bat' ''
    Assert-True ($restarted.ExitCode -eq 0) $restarted.Text
    Wait-For { Test-Path -LiteralPath $markerPath } 'Restart did not launch the current installation'
    $ownProcess.Refresh()
    Assert-True $ownProcess.HasExited 'Restart did not stop its previous process'
    $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    $restartedProcess = Get-Process -Id $marker.pid
    $ownedProcesses.Add($restartedProcess)
    $stopped = Invoke-Launcher 'stop-coordinator.bat'
    Assert-True ($stopped.ExitCode -eq 0) $stopped.Text
    Wait-For { $restartedProcess.Refresh(); $restartedProcess.HasExited } 'Stop did not terminate its own process'
    Assert-Alive $foreignZigbee 'Restart or stop terminated another Zigbee installation'
    Assert-Alive $foreignPrefix 'Restart or stop matched a script path prefix'
    $status = Invoke-Launcher 'status-coordinator.bat'
    Assert-True ($status.ExitCode -eq 1) ('Stopped installation was mistaken for another Zigbee process: ' + $status.Text)
    Write-Output 'PASS: restart, stop and stopped status are scoped to the current installation'
} finally {
    foreach ($process in $ownedProcesses) {
        $process.Refresh()
        if (-not $process.HasExited) { Stop-Process -InputObject $process -Force -ErrorAction SilentlyContinue }
    }
    $env:GH_LAUNCHER_TEST_PORT = $previousTestPort
    $resolvedFixtureRoot = [System.IO.Path]::GetFullPath($fixtureRoot)
    $safePrefix = $fixtureBase.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedFixtureRoot.StartsWith($safePrefix, [System.StringComparison]::OrdinalIgnoreCase) `
        -or [System.IO.Path]::GetFileName($resolvedFixtureRoot) -notmatch '^growerhub-launchers-[a-f0-9]{32}$') {
        throw 'Fixture cleanup target is outside the test directory'
    }
    if (Test-Path -LiteralPath $resolvedFixtureRoot) { Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force }
}
