# Мониторинг и логирование

## Что есть сейчас
- **FastAPI**: стандартный logging + предупреждения при проблемах с MQTT (см. mqtt_publisher.py, mqtt_subscriber.py).
- **AckStore**: периодическая очистка (метод cleanup) позволяет отслеживать, сколько старых ACK удалено.
- **Nginx**: access/error логи (/var/log/nginx/growerhub.ru_*) по умолчанию включены Ansible'ом.
- **Systemd**: journalctl -u growerhub.service покажет перезапуски сервиса и ошибки uvicorn.
- **Mosquitto**: системный лог /var/log/mosquitto/mosquitto.log (ролю стоит включить ротацию).

## Что следует добавить
- Метрики доступности MQTT и задержки ACK (можно начать с логирования таймаутов в manual_watering_wait_ack).
- Алерты по last_seen устройств: если DeviceShadowStore не обновлялся > DEVICE_ONLINE_THRESHOLD_S, отправлять уведомление.
- Сбор и визуализация телеметрии (InfluxDB + Grafana или Lighthouse) — данные уже лежат в sensor_data.
- централизованный лог-пайплайн (Vector/Fluent Bit) для nginx + FastAPI.
