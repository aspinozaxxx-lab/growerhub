# Компоненты системы

## ESP32 (firmware)
- Проект на PlatformIO (irmware/), основной класс WateringApplication и менеджеры Sensors, Actuators, Network.
- Загружает настройки из SPIFFS (SettingsManager), поддерживает до 10 Wi-Fi сетей и TLS для HTTP.
- Периодически отправляет телеметрию и состояние реле, умеет выполнять factory reset и тест насосов.

## FastAPI-backend
- Точка входа server/app/main.py, подключаются роутеры ручного полива и статические файлы.
- Использует SQLAlchemy (таблицы devices, sensor_data, watering_logs) и Pydantic-схемы.
- Управляет потоками MQTT: инициализирует AckStore, DeviceShadowStore, запускает подписчиков и публишер.

## MQTT-брокер (Mosquitto)
- Конфигурация и запуск через роль nsible/roles/mosquitto.
- Открывает порты 1883/8883, поддерживает базовую авторизацию и TLS.
- Команды pump.start/pump.stop и ACK ходят QoS1, retained state хранит текущее состояние насоса.

## База данных
- По умолчанию PostgreSQL (pp/core/database.py), для тестов допускается SQLite.
- Хранит параметры таргетной влажности, длительность полива, историю измерений.

## Nginx
- SSL reverse-proxy с Let's Encrypt (nsible/roles/nginx/templates/site.conf.j2).
- Проксирует /api, /docs, /redoc на FastAPI, /static и SPA отдает напрямую из рабочего каталога.

## Deploy-agent и скрипты
- deploy_agent.py опрашивает GitHub API, при новом коммите запускает deploy.sh.
- deploy.sh обновляет репозиторий, ставит зависимости, гоняет pytest и перезапускает growerhub.service.

## Ansible-инфраструктура
- Каталог nsible/ содержит playbooks для bootstrap сервера, деплоя FastAPI, Nginx, Mosquitto, агента.
- Inventory описывает окружения, nsible.cfg задает параметры подключения.
