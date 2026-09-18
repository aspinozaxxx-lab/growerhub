# Локальный мост для существующего Zigbee2MQTT/Home Assistant

Локальный мост не заменяет MQTT-брокер и не меняет подключение Home Assistant. Он создаёт отдельное направленное соединение между деревьями тем Zigbee2MQTT и GrowerHub.

Нужен постоянно включённый компьютер с Docker Compose: Linux/Raspberry Pi или Windows с Docker Desktop в режиме Linux containers. В Home Assistant OS этот пакет не устанавливается как дополнение; запустите его на другом компьютере с Docker в той же сети.

1. В мастере GrowerHub выберите «Уже есть Zigbee2MQTT / Home Assistant».
2. Введите LAN-адрес локального MQTT-брокера, порт, `mqtt.base_topic` из Zigbee2MQTT и локальные учётные данные. Если брокер работает на том же компьютере с Docker Desktop, можно использовать `host.docker.internal`. `127.0.0.1` внутри контейнера указывает на сам connector.
3. Скачайте личный `bridge.conf` из мастера и положите его рядом с `docker-compose.yml`. Локальные учётные данные остаются в этом файле и не отправляются GrowerHub. Не публикуйте файл; на Linux ограничьте доступ к каталогу своей учётной записью.
4. Откройте терминал в этой папке и выполните `docker compose up -d`.
5. Выполните `docker compose logs --tail=50 connector`. После подключения в GrowerHub появятся статус «В сети» и уже сопряжённые устройства. Переносить USB-координатор и заново сопрягать устройства не нужно.

Для ручной настройки можно скопировать `mosquitto-bridge.conf.example` в `bridge.conf` и заменить все `CHANGE_ME_*`, включая базовую тему локального Zigbee2MQTT. При анонимном локальном доступе удалите `remote_username` и `remote_password` только из секции `local-zigbee2mqtt`.

Если подключения нет, проверьте логи, доступность локального MQTT из Docker, адрес и учётные данные брокера, а также исходящий доступ к `growerhub.ru:8883`. После замены конфигурации выполните `docker compose restart connector`. Для остановки моста используйте `docker compose down`; существующие Zigbee2MQTT и Home Assistant продолжат работать.

Два соединения обмениваются данными через раздельные внутренние деревья `relay/from-local` и `relay/to-local`: состояние, доступность и служебные ответы идут только наружу; `/set`, `/get` и `bridge/request/*` — только обратно. Перед включением сценария выберите один источник автоматических команд для каждого исполнительного устройства, чтобы расписания Home Assistant и GrowerHub не конфликтовали.

## English

This connector keeps your existing Zigbee2MQTT broker and Home Assistant configuration. It needs an always-on computer running Docker Compose: Linux/Raspberry Pi, or Windows with Docker Desktop using Linux containers. It is not a Home Assistant OS add-on; use another Docker host on the same network for that setup.

1. In GrowerHub setup, choose the existing Zigbee2MQTT / Home Assistant option.
2. Enter your MQTT broker's LAN address, port, Zigbee2MQTT `mqtt.base_topic` and local credentials. With a broker on the same Docker Desktop computer, `host.docker.internal` can be used. Container address `127.0.0.1` refers to the connector itself.
3. Download your personal `bridge.conf` and place it beside `docker-compose.yml`. Local MQTT credentials stay in that file and are not sent to GrowerHub. Keep the file private; on Linux, restrict access to its directory to your account.
4. Open a terminal in this directory and run `docker compose up -d`.
5. Check `docker compose logs --tail=50 connector`. GrowerHub should show the connection online and import existing devices. No USB move or device re-pairing is required.

For manual configuration, copy `mosquitto-bridge.conf.example` to `bridge.conf` and replace every `CHANGE_ME_*` value, including the local Zigbee2MQTT base topic. Remove the local section's `remote_username` and `remote_password` only if your local broker allows anonymous connections.

If the connector stays offline, check its logs, local broker reachability from Docker, credentials and outbound access to `growerhub.ru:8883`. Run `docker compose restart connector` after editing the configuration. `docker compose down` stops this connector while your existing local setup keeps running. Use one automatic controller per actuator to avoid conflicting Home Assistant and GrowerHub schedules.
