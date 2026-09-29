@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion

set "GH_COORDINATOR_ROOT=%~dp0"
set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

echo Останавливается координатор Zigbee2MQTT...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$entry = [regex]::Escape((Join-Path $env:GH_COORDINATOR_ROOT 'zigbee2mqtt\index.js'));" ^
  "$pattern = '^\s*(?:\x22[^\x22]+\x22|\S+)\s+(?:\x22' + $entry + '\x22|' + $entry + ')(?=\s|$)';" ^
  "$z2m = @(Get-CimInstance Win32_Process -Filter \"Name = 'node.exe'\" | Where-Object { $_.CommandLine -match $pattern });" ^
  "foreach ($process in $z2m) {" ^
  "  Stop-Process -Id $process.ProcessId -Force;" ^
  "  Write-Host ('Остановлен координатор из этой папки, PID ' + $process.ProcessId);" ^
  "}" ^
  "if ($z2m.Count -eq 0) { Write-Host 'Запущенный координатор из этой папки не найден.'; }" ^
  "Start-Sleep -Milliseconds 800;"
set "EXITCODE=%ERRORLEVEL%"

if "%NO_PAUSE%"=="" pause
exit /b %EXITCODE%
