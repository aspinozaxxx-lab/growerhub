@echo off
setlocal EnableExtensions EnableDelayedExpansion

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

if not exist "%Z2M_DATA%\secret.yaml" (
    echo Zigbee2MQTT secret file is missing: %Z2M_DATA%\secret.yaml
    echo Copy data\secret.example.yaml to data\secret.yaml and fill local values.
    pause
    exit /b 1
)

if not exist "%Z2M_DIR%\node_modules\source-map-support" (
    echo Installing Zigbee2MQTT dependencies...
    pushd "%Z2M_DIR%"
    call corepack pnpm install --frozen-lockfile --no-optional
    set "INSTALL_EXITCODE=!ERRORLEVEL!"
    popd
    if not "!INSTALL_EXITCODE!"=="0" (
        echo Failed to install Zigbee2MQTT dependencies.
        pause
        exit /b !INSTALL_EXITCODE!
    )
)

set "ZIGBEE2MQTT_DATA=%Z2M_DATA%"

echo Stopping existing Zigbee2MQTT coordinator if it is already running...
call "%ROOT%stop-coordinator.bat" --no-pause
if errorlevel 1 (
    echo Failed to stop existing Zigbee2MQTT coordinator.
    pause
    exit /b 1
)

echo Starting Zigbee2MQTT coordinator...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$env:ZIGBEE2MQTT_DATA = '%Z2M_DATA%';" ^
  "$env:PATH = '%ROOT%bin;' + $env:PATH;" ^
  "$process = Start-Process -FilePath 'node.exe' -ArgumentList @('%Z2M_DIR%\index.js') -WorkingDirectory '%Z2M_DIR%' -WindowStyle Hidden -PassThru;" ^
  "Write-Host ('Started Zigbee2MQTT process PID ' + $process.Id);"
if errorlevel 1 (
    echo Failed to start Zigbee2MQTT coordinator.
    pause
    exit /b 1
)

echo Web UI: http://127.0.0.1:8080
echo Run status-coordinator.bat to check status.
echo Run stop-coordinator.bat to stop.
exit /b 0
