@echo off
chcp 65001 >nul
setlocal EnableExtensions

set "FRONTEND_PORT=8080"
set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$z2m = @(Get-CimInstance Win32_Process -Filter \"Name = 'node.exe'\" | Where-Object { $_.CommandLine -match 'zigbee2mqtt[\\/]index\.js' });" ^
  "$listeners = @(Get-NetTCPConnection -LocalPort %FRONTEND_PORT% -State Listen -ErrorAction SilentlyContinue);" ^
  "$portOwners = @();" ^
  "foreach ($listener in $listeners) {" ^
  "  $process = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess);" ^
  "  if ($process) { $portOwners += $process; }" ^
  "}" ^
  "if ($z2m.Count -gt 0) {" ^
  "  Write-Host 'Состояние Zigbee2MQTT: запущен';" ^
  "  foreach ($process in $z2m) {" ^
  "    Write-Host ('PID: ' + $process.ProcessId);" ^
  "    Write-Host ('Команда: ' + $process.CommandLine);" ^
  "  }" ^
  "  if ($listeners.Count -gt 0) { Write-Host 'Интерфейс: http://127.0.0.1:%FRONTEND_PORT%'; } else { Write-Host 'Интерфейс ещё не запущен'; }" ^
  "  exit 0;" ^
  "}" ^
  "$blocking = @($portOwners | Where-Object { $_.Name -ine 'node.exe' });" ^
  "if ($blocking.Count -gt 0) {" ^
  "  Write-Host 'Состояние Zigbee2MQTT: остановлен';" ^
  "  foreach ($process in $blocking) { Write-Host ('Порт интерфейса %FRONTEND_PORT% занят процессом ' + $process.Name + ', PID ' + $process.ProcessId); }" ^
  "  exit 2;" ^
  "}" ^
  "Write-Host 'Состояние Zigbee2MQTT: остановлен';" ^
  "exit 1;"
set "EXITCODE=%ERRORLEVEL%"

if "%NO_PAUSE%"=="" pause
exit /b %EXITCODE%
