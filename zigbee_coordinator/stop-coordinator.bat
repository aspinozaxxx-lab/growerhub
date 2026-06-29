@echo off
setlocal EnableExtensions

set "FRONTEND_PORT=8080"
set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

echo Stopping Zigbee2MQTT coordinator...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$listeners = Get-NetTCPConnection -LocalPort %FRONTEND_PORT% -State Listen -ErrorAction SilentlyContinue;" ^
  "$stopped = @{};" ^
  "foreach ($listener in $listeners) {" ^
  "  $process = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess);" ^
  "  if ($process -and $process.Name -ieq 'node.exe') {" ^
  "    Stop-Process -Id $listener.OwningProcess -Force;" ^
  "    $stopped[$listener.OwningProcess] = $true;" ^
  "    Write-Host ('Stopped Zigbee2MQTT process PID ' + $listener.OwningProcess);" ^
  "  } elseif ($process) {" ^
  "    Write-Error ('Frontend port %FRONTEND_PORT% is used by ' + $process.Name + ' PID ' + $listener.OwningProcess + '. Stop it manually or change frontend.port in data\configuration.yaml.');" ^
  "    exit 2;" ^
  "  }" ^
  "}" ^
  "$z2m = Get-CimInstance Win32_Process -Filter \"Name = 'node.exe'\" | Where-Object { $_.CommandLine -match 'zigbee2mqtt[\\/]index\.js' };" ^
  "foreach ($process in $z2m) {" ^
  "  if (-not $stopped.ContainsKey($process.ProcessId)) {" ^
  "    Stop-Process -Id $process.ProcessId -Force;" ^
  "    $stopped[$process.ProcessId] = $true;" ^
  "    Write-Host ('Stopped Zigbee2MQTT process PID ' + $process.ProcessId);" ^
  "  }" ^
  "}" ^
  "if ($stopped.Count -eq 0) { Write-Host 'No running Zigbee2MQTT coordinator found.'; }" ^
  "Start-Sleep -Milliseconds 800;"
set "EXITCODE=%ERRORLEVEL%"

if "%NO_PAUSE%"=="" pause
exit /b %EXITCODE%
