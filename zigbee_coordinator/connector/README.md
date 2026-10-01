# Модуль связи для существующего Zigbee2MQTT/Home Assistant

Версия 0.2.4. Нужен постоянно включённый компьютер с Docker Compose: Windows с Docker Desktop в режиме Linux containers или Linux/Raspberry Pi с 64-битной системой. В Home Assistant OS запускайте модуль на отдельном компьютере с Docker в той же сети.

## Подключение

1. В мастере GrowerHub или «Настройки → Подключения» выберите «Уже работает Zigbee2MQTT».
2. Укажите адрес и порт локального MQTT-брокера, `mqtt.base_topic` из Zigbee2MQTT и данные отдельного пользователя брокера. Для брокера на том же компьютере с Docker Desktop подходит `host.docker.internal`; `127.0.0.1` внутри контейнера указывает на сам модуль.
3. Скачайте личный `connector.json` и положите его рядом с `docker-compose.yml`. Этот файл создаётся в браузере; локальные MQTT credentials не отправляются GrowerHub. Храните его приватно.
4. В папке модуля выполните `docker compose up -d --build`. При первом запуске Docker скачает зависимости и соберёт модуль.
5. Выполните `docker compose logs --tail=50 connector`. Успешное подключение обозначено `local_connected`, `cloud_connected`, затем `ready` после получения списка устройств.
6. Дождитесь нового сообщения выбранного датчика в Zigbee2MQTT и сравните его с GrowerHub. Сохранённый snapshot позволяет показать последнее известное значение, но не добавляет измерение в историю и не подтверждает живую связь.

Устройства остаются на своём координаторе. Ничего менять в Home Assistant, USB-порте и сопряжении Zigbee не требуется. ZHA, произвольные сущности HA и прежняя история HA этим модулем не импортируются.

На Linux файл может быть доступен только владельцу: `chmod 600 connector.json`. Если UID владельца отличается от стандартного 1000, запускайте Compose с `CONNECTOR_UID="$(id -u)" CONNECTOR_GID="$(id -g)" docker compose up -d --build`. Docker использует эти значения только для доступа к локальному файлу.

## Что передаётся

Модуль передаёт список и показания всех устройств выбранного Zigbee2MQTT, availability, состояние bridge, безопасные сведения о версии/координаторе и ответы на поддерживаемые команды. Пароли, настройки broker, Zigbee network key, логи, discovery и эхо команд не пересылаются. Полные имена, включая `/` и Unicode, добавление, удаление и переименование обрабатываются автоматически. При конфликте имени с маршрутом другого устройства оно исключается; в логах появляется `ambiguous_names` без credentials.

Обратно разрешены команды известных устройств `/set`, `/get`, сопряжение `bridge/request/permit_join` и переименование `bridge/request/device/rename`. Отдельного режима только чтения и выбора одного устройства нет. Для каждого исполнительного устройства выбирайте одну систему автоматизации: HA или GrowerHub.

Оба соединения имеют чистую сессию и не накапливают команды или живую телеметрию при обрыве. Команды идут с QoS 0, без retain и повторов; сохранённые команды отклоняются. Если результат команды неизвестен, проверьте состояние устройства перед следующим действием. Облако всегда использует TLS с проверкой сертификата. Cloud client ID выдан GrowerHub, локальный — тот же ID с `-local`; два подключения к общему broker друг друга не вытесняют.

Наличие ON/OFF не означает допуска к поливу: требования автономного закрытия, проверенной модели и безопасного start остаются обязательными. Физически проверенных моделей Zigbee-полива пока нет. Начните с показаний датчика.

## Ошибки, остановка и обновление

При `local_connection_failed` проверьте адрес, порт и локальный MQTT-доступ из Docker. При `cloud_connection_failed` — интернет, исходящий TCP 8883 и выданные GrowerHub credentials. Не отключайте проверку сертификата. `invalid_config_or_ca` означает ошибку файла, его прав или сертификата. `subscribe_failed` — отказ broker подписаться на нужные topics. Не присылайте личный файл и пароли в чаты.

Для ручной настройки используйте `connector.example.json`, заменив примерные значения. `local` задаёт локальный broker; `cloud` — выданные namespace, username, password и client ID. Параметры `limits` регулируют размер сообщения и восстановление соединения; обычному пользователю менять их не нужно. После правки файла выполните `docker compose restart connector`.

Старые версии 0.2.2/0.2.3 с `bridge.conf` не обновляются автоматически: они имеют ограничения вложенных имён и происхождения сохранённых показаний. Для добровольного перехода остановите старый модуль командой `docker compose down` в его исходной папке. Распакуйте новый архив отдельно и подготовьте `connector.json`: скачайте его с новыми данными доступа либо перенесите локально прежние cloud/local credentials из своего `bridge.conf` в пример JSON. Не запускайте две версии с одним cloud client ID. После запуска нового модуля проверьте свежую публикацию датчика. Переход не пересчитывает старые точки истории.

`docker compose down` останавливает только этот модуль; Zigbee2MQTT и Home Assistant продолжают работать. Для возврата к старому пакету сначала остановите новый, затем запустите сохранённый старый пакет с его конфигурацией.

## English

Version 0.2.4. Use an always-on Docker Compose computer: Windows with Docker Desktop in Linux containers mode, or 64-bit Linux/Raspberry Pi. This is not a Home Assistant OS add-on; use a separate Docker computer on that network.

1. In the GrowerHub wizard or Settings → Connections, select “Zigbee2MQTT is already running”.
2. Enter your broker address, port, Zigbee2MQTT `mqtt.base_topic` and local broker credentials. `host.docker.internal` reaches a broker on the same Docker Desktop host; container address `127.0.0.1` points to the connector itself.
3. Download the personal `connector.json` and place it beside `docker-compose.yml`. The browser generates it locally; local broker credentials are not sent to GrowerHub. Keep it private.
4. Run `docker compose up -d --build`. The first start downloads dependencies and builds the connector.
5. Check `docker compose logs --tail=50 connector` for `local_connected`, `cloud_connected` and `ready` after inventory arrives.
6. Wait for a fresh sensor publication in Zigbee2MQTT and compare it with GrowerHub. Cached snapshots show the last known value but do not add history measurements or refresh live connection time.

Your USB coordinator, device pairing and Home Assistant remain in place. ZHA, arbitrary HA entities and previous HA history are not imported. On Linux, protect the file with `chmod 600 connector.json`; if its owner UID is not 1000, use `CONNECTOR_UID="$(id -u)" CONNECTOR_GID="$(id -g)" docker compose up -d --build`.

All devices of the selected Zigbee2MQTT network share inventory, state and availability. The connector also forwards bridge state, sanitized version/coordinator information and supported command responses. Broker settings, passwords, network keys, logs, discovery and command echoes are excluded. Full names with slashes and Unicode, additions, removals and renames update automatically. Ambiguous device routes are excluded with an `ambiguous_names` diagnostic.

Return commands are limited to known devices’ `/set` and `/get`, plus `bridge/request/permit_join` and `bridge/request/device/rename`. There is no separate read-only or single-device mode. Use one automation controller per actuator.

Clean MQTT sessions and QoS 0 prevent offline command queues or automatic retries. Retained commands are rejected. Check the actual device state if a command result is unknown. Cached telemetry stays cached after reconnection. The cloud connection always verifies its TLS certificate. The cloud client ID is issued by GrowerHub; the local one adds `-local`, allowing separate connections to share a broker.

ON/OFF does not qualify a valve for irrigation: a verified model and autonomous closure remain required. No physical Zigbee irrigation models are verified yet. Start by checking sensor readings.

For `local_connection_failed`, check LAN reachability and broker credentials. For `cloud_connection_failed`, check internet access, outbound TCP 8883 and GrowerHub credentials. Keep TLS verification enabled. `invalid_config_or_ca` concerns the file, permissions or certificate; `subscribe_failed` indicates broker subscription refusal. Do not share personal configuration files or passwords.

For manual setup, edit `connector.example.json`: `local` contains broker settings; `cloud` contains the issued namespace and credentials. Normal users need not edit `limits`. Restart the connector after editing: `docker compose restart connector`.

Versions 0.2.2/0.2.3 using `bridge.conf` are not upgraded automatically and retain their hierarchical-name and cached-origin limitations. For voluntary migration, run `docker compose down` in the original directory, extract the new ZIP separately and prepare `connector.json`. Download newly issued credentials or locally copy the previous cloud/local credentials into the example JSON. Do not run both versions with the same cloud client ID. Verify a fresh sensor reading; previous history points are not rewritten.

`docker compose down` stops only this connector. Zigbee2MQTT and Home Assistant keep running. To roll back, stop the new connector before restarting your saved old package.
