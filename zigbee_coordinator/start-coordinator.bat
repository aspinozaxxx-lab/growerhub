@echo off
setlocal

set "ROOT=%~dp0"
set "Z2M_DIR=%ROOT%zigbee2mqtt"
set "Z2M_DATA=%ROOT%data"
set "FRONTEND_PORT=8080"
set "PATH=%ROOT%bin;%PATH%"

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js not found. Install Node.js or rerun the initial setup.
    pause
    exit /b 1
)

where corepack >nul 2>nul
if errorlevel 1 (
    echo Corepack not found. Install Node.js with Corepack support.
    pause
    exit /b 1
)

if not exist "%Z2M_DIR%\index.js" (
    echo Zigbee2MQTT source is missing: %Z2M_DIR%
    pause
    exit /b 1
)

if not exist "%Z2M_DATA%\configuration.yaml" (
    echo Zigbee2MQTT config is missing: %Z2M_DATA%\configuration.yaml
    pause
    exit /b 1
)

if not exist "%Z2M_DATA%\secrets.yaml" (
    echo Zigbee2MQTT secrets file is missing: %Z2M_DATA%\secrets.yaml
    echo Copy data\secrets.example.yaml to data\secrets.yaml and fill local values.
    pause
    exit /b 1
)

if not exist "%Z2M_DIR%\node_modules\source-map-support" (
    echo Installing Zigbee2MQTT dependencies...
    pushd "%Z2M_DIR%"
    corepack pnpm install --frozen-lockfile
    set "INSTALL_EXITCODE=%ERRORLEVEL%"
    popd
    if not "%INSTALL_EXITCODE%"=="0" (
        echo Failed to install Zigbee2MQTT dependencies.
        pause
        exit /b %INSTALL_EXITCODE%
    )
)

set "ZIGBEE2MQTT_DATA=%Z2M_DATA%"

echo Stopping existing Zigbee2MQTT coordinator if it is already running...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$listeners = Get-NetTCPConnection -LocalPort %FRONTEND_PORT% -State Listen -ErrorAction SilentlyContinue;" ^
  "$stopped = @{};" ^
  "foreach ($listener in $listeners) {" ^
  "  $process = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess);" ^
  "  if ($process -and $process.Name -ieq 'node.exe') {" ^
  "    Stop-Process -Id $listener.OwningProcess -Force;" ^
  "    $stopped[$listener.OwningProcess] = $true;" ^
  "    Write-Host ('Stopped existing Zigbee2MQTT process PID ' + $listener.OwningProcess);" ^
  "  } elseif ($process) {" ^
  "    Write-Error ('Frontend port %FRONTEND_PORT% is used by ' + $process.Name + ' PID ' + $listener.OwningProcess + '. Stop it manually or change frontend.port in data\configuration.yaml.');" ^
  "    exit 2;" ^
  "  }" ^
  "}" ^
  "$z2m = Get-CimInstance Win32_Process -Filter \"Name = 'node.exe'\" | Where-Object { $_.CommandLine -match 'zigbee2mqtt[\\/]index\.js' };" ^
  "foreach ($process in $z2m) {" ^
  "  if (-not $stopped.ContainsKey($process.ProcessId)) {" ^
  "    Stop-Process -Id $process.ProcessId -Force;" ^
  "    Write-Host ('Stopped existing Zigbee2MQTT process PID ' + $process.ProcessId);" ^
  "  }" ^
  "}" ^
  "Start-Sleep -Milliseconds 800;"
if errorlevel 1 (
    echo Failed to stop existing Zigbee2MQTT coordinator.
    pause
    exit /b 1
)

echo Starting Zigbee2MQTT coordinator...
echo Web UI: http://127.0.0.1:8080
echo Press Ctrl+C to stop.

pushd "%Z2M_DIR%"
node "%Z2M_DIR%\index.js"
set "EXITCODE=%ERRORLEVEL%"
popd

echo Zigbee2MQTT stopped with exit code %EXITCODE%.
pause
exit /b %EXITCODE%
