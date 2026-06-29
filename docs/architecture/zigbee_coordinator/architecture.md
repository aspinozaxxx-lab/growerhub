# Архитектура zigbee_coordinator

`zigbee_coordinator` - локальная обвязка вокруг Zigbee2MQTT для USB-координатора CC2652P и Zigbee-устройств. Компонент запускается на Windows через bat-файлы и подключает Zigbee-сеть к MQTT broker GrowerHub.

## Ответственность

- Запуск Zigbee2MQTT `2.12.0` из `zigbee_coordinator/zigbee2mqtt`.
- Хранение локальной Zigbee runtime-конфигурации в `zigbee_coordinator/data`.
- Подключение USB-координатора через `COM7` с adapter `zstack`.
- Публикация состояний и прием команд через MQTT namespace Zigbee2MQTT.
- Предоставление локального frontend Zigbee2MQTT на `127.0.0.1:8080`.

## Границы

`zigbee_coordinator` не является backend-доменом и не хранит бизнес-истину GrowerHub. Он не заменяет основной MQTT-контракт `gh/dev/<device_id>/...`; он публикует сырые Zigbee2MQTT topics, а их отображение в домены GrowerHub должно выполняться отдельным backend adapter или явно описанным интеграционным слоем.

## MQTT

Рабочий broker задается в `zigbee_coordinator/data/configuration.yaml`.

Основной namespace:

- `zigbee2growerhub/bridge/state` - состояние Zigbee2MQTT bridge.
- `zigbee2growerhub/bridge/info` - сведения о bridge, координаторе и конфиге.
- `zigbee2growerhub/bridge/devices` - список Zigbee-устройств.
- `zigbee2growerhub/<friendly_name>` - состояние устройства.
- `zigbee2growerhub/<friendly_name>/set` - команда устройству.

## Конфигурация и секреты

В git хранится `configuration.yaml` без MQTT credentials и Zigbee network key. MQTT credentials и network key хранятся локально в ignored файле `zigbee_coordinator/data/secret.yaml`. PAN ID и extended PAN ID не являются учетными данными и зафиксированы в `configuration.yaml`, потому что Zigbee2MQTT валидирует эти поля до подстановки `!secret`.

Шаблон для новой машины:

```text
zigbee_coordinator/data/secret.example.yaml
```

Файлы runtime-состояния, логи, база Zigbee-сети, coordinator backup, `node_modules` и локальные данные Mosquitto не коммитятся.

## Запуск

- `start-coordinator.bat` проверяет Node/Corepack, устанавливает pnpm-зависимости при отсутствии `node_modules`, останавливает уже работающий Zigbee2MQTT на `8080` и запускает новый экземпляр.
- `start-mosquitto.bat` оставлен только для локального broker-режима; штатный режим использует внешний broker из `configuration.yaml`.

## Обновление Zigbee2MQTT

Обновление версии Zigbee2MQTT меняет внешний код, MQTT API и поддержку устройств. Такое изменение должно фиксироваться отдельным commit и проверяться запуском coordinator, публикацией `bridge/state`, чтением `bridge/info` и командой на безопасный request topic.
