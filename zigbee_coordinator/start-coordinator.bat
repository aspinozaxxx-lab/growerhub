@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion

set "ROOT=%~dp0"
set "GH_COORDINATOR_ROOT=%~dp0"
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
    if errorlevel 1 (
        popd
        echo Не удалось установить зависимости Zigbee2MQTT.
        pause
        exit /b 1
    )
    popd
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
  "$listeners = @(Get-NetTCPConnection -LocalPort %FRONTEND_PORT% -State Listen -ErrorAction SilentlyContinue);" ^
  "if ($listeners.Count -gt 0) { throw 'Порт интерфейса %FRONTEND_PORT% занят другим приложением. Координатор не запущен; другие процессы не остановлены.'; }" ^
  "$entry = Join-Path $env:GH_COORDINATOR_ROOT 'zigbee2mqtt\index.js';" ^
  "$process = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList ([char]34 + $entry + [char]34) -WorkingDirectory (Split-Path -Parent $entry) -WindowStyle Hidden -PassThru;" ^
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
