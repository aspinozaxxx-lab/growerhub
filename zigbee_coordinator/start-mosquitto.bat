@echo off
setlocal

set "ROOT=%~dp0"
set "CONTAINER=growerhub-zigbee-mqtt"
set "CONFIG=%ROOT%mosquitto\config\mosquitto.conf"
set "DATA=%ROOT%mosquitto\data"
set "LOG=%ROOT%mosquitto\log"

where docker >nul 2>nul
if errorlevel 1 (
    echo Docker CLI not found. Install or start Docker Desktop.
    pause
    exit /b 1
)

docker info >nul 2>nul
if errorlevel 1 (
    echo Docker Desktop is not running or Docker daemon is not ready.
    pause
    exit /b 1
)

if not exist "%CONFIG%" (
    echo Missing Mosquitto config: %CONFIG%
    pause
    exit /b 1
)

if not exist "%DATA%" mkdir "%DATA%"
if not exist "%LOG%" mkdir "%LOG%"

docker rm -f "%CONTAINER%" >nul 2>nul

docker run -d ^
  --name "%CONTAINER%" ^
  --restart unless-stopped ^
  -p 127.0.0.1:1883:1883 ^
  -v "%CONFIG%:/mosquitto/config/mosquitto.conf:ro" ^
  -v "%DATA%:/mosquitto/data" ^
  -v "%LOG%:/mosquitto/log" ^
  eclipse-mosquitto:2

if errorlevel 1 (
    echo Failed to start Mosquitto.
    pause
    exit /b 1
)

echo Mosquitto started at mqtt://127.0.0.1:1883
docker ps --filter "name=%CONTAINER%"
