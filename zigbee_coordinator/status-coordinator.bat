@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion

set "FRONTEND_PORT=8080"
set "GH_COORDINATOR_ROOT=%~dp0"
set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$entry = [regex]::Escape((Join-Path $env:GH_COORDINATOR_ROOT 'zigbee2mqtt\index.js'));" ^
  "$pattern = '^\s*(?:\x22[^\x22]+\x22|\S+)\s+(?:\x22' + $entry + '\x22|' + $entry + ')(?=\s|$)';" ^
  "$z2m = @(Get-CimInstance Win32_Process -Filter \"Name = 'node.exe'\" | Where-Object { $_.CommandLine -match $pattern });" ^
  "$listeners = @(Get-NetTCPConnection -LocalPort %FRONTEND_PORT% -State Listen -ErrorAction SilentlyContinue);" ^
  "$ownListeners = @($listeners | Where-Object { $_.OwningProcess -in $z2m.ProcessId });" ^
  "$blocking = @($listeners | Where-Object { $_.OwningProcess -notin $z2m.ProcessId });" ^
  "if ($z2m.Count -gt 0) {" ^
  "  Write-Host 'Состояние Zigbee2MQTT: запущен';" ^
  "  foreach ($process in $z2m) {" ^
  "    Write-Host ('PID: ' + $process.ProcessId);" ^
  "  }" ^
  "  if ($ownListeners.Count -gt 0) { Write-Host 'Интерфейс: http://127.0.0.1:%FRONTEND_PORT%'; } else { Write-Host 'Интерфейс ещё не запущен'; }" ^
  "} else {" ^
  "  Write-Host 'Состояние Zigbee2MQTT: остановлен';" ^
  "}" ^
  "if ($blocking.Count -gt 0) {" ^
  "  foreach ($listener in $blocking) { Write-Host ('Порт интерфейса %FRONTEND_PORT% занят другим процессом, PID ' + $listener.OwningProcess + '. Он не относится к этой установке.'); }" ^
  "  exit 2;" ^
  "}" ^
  "if ($z2m.Count -gt 0) { exit 0; }" ^
  "exit 1;"
set "EXITCODE=%ERRORLEVEL%"

if "%NO_PAUSE%"=="" pause
exit /b %EXITCODE%
