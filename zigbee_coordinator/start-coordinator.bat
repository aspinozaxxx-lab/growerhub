@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
set "Z2M_DIR=%ROOT%zigbee2mqtt"
set "Z2M_DATA=%ROOT%data"
set "FRONTEND_PORT=8080"
set "PATH=%ROOT%bin;%PATH%"

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js не найден. Установите актуальную LTS-версию Node.js.
    pause
    exit /b 1
)

where corepack >nul 2>nul
if errorlevel 1 (
    echo Corepack не найден. Установите Node.js с поддержкой Corepack.
    pause
    exit /b 1
)

if not exist "%Z2M_DIR%\index.js" (
    echo Не найдены файлы Zigbee2MQTT: %Z2M_DIR%
    pause
    exit /b 1
)

if not exist "%Z2M_DATA%\configuration.yaml" (
    echo Не найден configuration.yaml: %Z2M_DATA%\configuration.yaml
    pause
    exit /b 1
)

if not exist "%Z2M_DATA%\secret.yaml" (
    echo Не найден secret.yaml: %Z2M_DATA%\secret.yaml
    echo Скачайте этот файл на экране подключения GrowerHub и поместите в папку data.
    pause
    exit /b 1
)

if not exist "%Z2M_DIR%\node_modules\source-map-support" (
    echo Устанавливаются зависимости Zigbee2MQTT...
    pushd "%Z2M_DIR%"
    call corepack pnpm install --frozen-lockfile --no-optional
    set "INSTALL_EXITCODE=!ERRORLEVEL!"
    popd
    if not "!INSTALL_EXITCODE!"=="0" (
        echo Не удалось установить зависимости Zigbee2MQTT.
        pause
        exit /b !INSTALL_EXITCODE!
    )
)

set "ZIGBEE2MQTT_DATA=%Z2M_DATA%"

echo Останавливается ранее запущенный координатор Zigbee2MQTT...
call "%ROOT%stop-coordinator.bat" --no-pause
if errorlevel 1 (
    echo Не удалось остановить ранее запущенный координатор Zigbee2MQTT.
    pause
    exit /b 1
)

echo Запускается координатор Zigbee2MQTT...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$env:ZIGBEE2MQTT_DATA = '%Z2M_DATA%';" ^
  "$env:PATH = '%ROOT%bin;' + $env:PATH;" ^
  "$process = Start-Process -FilePath 'node.exe' -ArgumentList @('%Z2M_DIR%\index.js') -WorkingDirectory '%Z2M_DIR%' -WindowStyle Hidden -PassThru;" ^
  "Write-Host ('Zigbee2MQTT запущен, PID ' + $process.Id);"
if errorlevel 1 (
    echo Не удалось запустить координатор Zigbee2MQTT.
    pause
    exit /b 1
)

echo Интерфейс: http://127.0.0.1:8080
echo Для проверки состояния запустите status-coordinator.bat.
echo Для остановки запустите stop-coordinator.bat.
exit /b 0
