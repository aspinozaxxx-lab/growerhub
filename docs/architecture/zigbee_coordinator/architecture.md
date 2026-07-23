# Архитектура zigbee_coordinator

`zigbee_coordinator` — локальная обвязка вокруг Zigbee2MQTT и двусторонний connector для уже работающего локального MQTT. Компонент поддерживает Windows и Raspberry Pi/Linux и подключает Zigbee-сеть к изолированному namespace GrowerHub.

## Ответственность

- Запуск Zigbee2MQTT `2.12.0` из `zigbee_coordinator/zigbee2mqtt`.
- Хранение локальной Zigbee runtime-конфигурации в `zigbee_coordinator/data`.
- Выбор USB-порта и adapter `zstack` либо `ember` без зашитого COM-порта.
- Публикация состояний и прием команд через MQTT namespace Zigbee2MQTT.
- Предоставление локального frontend Zigbee2MQTT на `127.0.0.1:8080`.
- Направленная пересылка разрешённых Z2M topics между существующим локальным broker и GrowerHub без циклов.
- Шаблоны Windows ZIP и Raspberry Pi/Linux Docker Compose без персональных credentials.

## Границы

`zigbee_coordinator` не является backend-доменом и не хранит бизнес-истину GrowerHub. Он не заменяет основной MQTT-контракт `gh/dev/<device_id>/...`; он публикует сырые Zigbee2MQTT topics, а их отображение в домены GrowerHub должно выполняться отдельным backend adapter или явно описанным интеграционным слоем.

Админская вкладка Zigbee в frontend не подключается к MQTT напрямую. Она читает snapshot через backend REST, а backend получает и отправляет сообщения координатору только через MQTT.

Backend строит отображение устройств из metadata Zigbee2MQTT `bridge/devices[].definition`. Основной источник UI-возможностей - `definition.exposes`: свойства с access bit `STATE=1` показываются как метрики, свойства с `SET=2` доступны для команд через backend REST, `GET=4` означает возможность запроса значения у устройства. Frontend не содержит ручной таблицы моделей Zigbee-устройств.

## MQTT

Рабочий broker задается в `zigbee_coordinator/data/configuration.yaml`.

Пользовательский namespace задаётся одноразовым конфигом из кабинета и имеет вид `gh/z2m/<mqtt_username>`:

- `<base_topic>/bridge/state` — состояние Zigbee2MQTT bridge.
- `<base_topic>/bridge/info` — сведения о bridge, координаторе и конфиге.
- `<base_topic>/bridge/devices` — список Zigbee-устройств.
- `<base_topic>/<friendly_name>` — состояние устройства.
- `<base_topic>/<friendly_name>/set` — команда устройству.

Connector передаёт из локального broker в GrowerHub только state, availability и `bridge/state|info|devices|response`; обратно — только `<device>/set`, `<device>/get` и `bridge/request/*`. Каждое направление имеет отдельный allowlist; сообщение не публикуется обратно в источник.

## Конфигурация и секреты

В git хранятся только шаблоны без MQTT credentials и Zigbee network key. Одноразовый `secret.yaml` скачивается отдельно из кабинета и хранится локально в ignored runtime-каталоге. Credentials локального broker для connector вводятся только на машине пользователя и не отправляются GrowerHub.

Шаблон для новой машины:

```text
zigbee_coordinator/data/secret.example.yaml
```

Файлы runtime-состояния, логи, база Zigbee-сети, coordinator backup, `node_modules` и локальные данные Mosquitto не коммитятся.

## Запуск

- `start-coordinator.bat` проверяет конфиг, выбранный COM-порт и adapter, затем запускает Zigbee2MQTT отдельным процессом.
- `status-coordinator.bat` проверяет процесс Zigbee2MQTT и frontend port `8080`, выводит `running` или `stopped`.
- `stop-coordinator.bat` останавливает процесс Zigbee2MQTT по frontend port `8080` и по команде `zigbee2mqtt/index.js`.
- Linux-пакет запускается через Docker Compose, имеет явный USB mapping и постоянный volume.

## Обновление Zigbee2MQTT

Обновление версии Zigbee2MQTT меняет внешний код, MQTT API и поддержку устройств. Такое изменение должно фиксироваться отдельным commit и проверяться запуском coordinator, публикацией `bridge/state`, чтением `bridge/info` и командой на безопасный request topic.
