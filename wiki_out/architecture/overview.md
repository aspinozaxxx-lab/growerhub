# Обзор архитектуры

## Контекст
GrowerHub — это связка из ESP32-контроллера, облачного FastAPI-сервиса и MQTT-брокера Mosquitto. Система управляет насосом полива и подсветкой, принимает телеметрию и позволяет запускать ручной полив через веб-интерфейс.

## Основные блоки
- **Устройство ESP32**: считывает влажность почвы (аналоговый сенсор на GPIO34) и климат (DHT22 на GPIO15), управляет насосом (реле на GPIO4, инвертированная логика) и светом (реле на GPIO5).
- **FastAPI-сервер (server/app/main.py)**: REST API для телеметрии, настроек и OTA, а также маршруты ручного полива в server/api_manual_watering.py.
- **MQTT-брокер**: Mosquitto принимает команды gh/dev/{device_id}/cmd, acknowledgements и retained state от устройств.
- **База данных**: SQLAlchemy подключается к PostgreSQL (watering_db), хранит устройства, историю датчиков и логи полива.
- **Статика**: static/manual_watering.html — SPA-панель ручного полива, отдается через FastAPI и проксируется Nginx.
- **Nginx**: SSL-терминация и reverse-proxy к FastAPI (nsible/roles/nginx), раздает статические файлы.
- **Ansible и deploy-agent**: автоматизируют установку брокера, FastAPI-сервиса и собственного агента деплоя.

## Потоки данных
- **Телеметрия**: прошивка вызывает POST /api/device/{device_id}/status, сервер обновляет запись устройства и добавляет точку в sensor_data.
- **Ручной полив**: веб-клиент вызывает POST /api/manual-watering/start, сервер публикует MQTT-команду, ждет ACK через подписчик, UI отслеживает статус через /status и /wait-ack.
- **Состояние устройства**: ESP32 публикует retained state (gh/dev/{device_id}/state), FastAPI кладет его в DeviceShadowStore, UI показывает прогресс и актуальность.
- **OTA**: администратор загружает .bin через /api/upload-firmware, сервер кладет файл в server/firmware_binaries, устройство запрашивает /api/device/{device_id}/firmware.

## Инфраструктура
- CI/CD на GitHub Actions (.github/workflows/ci-cd.yml) гоняет pytest и rsync-деплой на growerhub.ru.
- На сервере запущен systemd-сервис growerhub.service; deploy-agent (deploy_agent.py) следит за веткой main и запускает deploy.sh с прогоном тестов.
- Mosquitto и Nginx ставятся Ansible-ролями; firewall настраивается через UFW.
