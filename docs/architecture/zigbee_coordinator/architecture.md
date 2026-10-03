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
- `<base_topic>/bridge/info` — сведения о bridge и координаторе.
- `<base_topic>/bridge/devices` — список Zigbee-устройств.
- `<base_topic>/<friendly_name>` — состояние устройства.
- `<base_topic>/<friendly_name>/set` — команда устройству.

Connector 0.2.5 — процесс Node.js с двумя MQTT-соединениями по ADR-009. Полные имена из `bridge/devices` определяют точные маршруты state, availability и команд; неоднозначные имена исключаются. Наружу идут также `bridge/state|info|devices` и ответы на поддерживаемые bridge-команды. `bridge/info` очищается от конфигурации и секретов; для проверки профиля ADR-010 сохраняет только короткие версии `zigbee_herdsman_converters` и `zigbee_herdsman`, если они присутствуют и имеют формат версии пакета. Исходящие данные помещаются в `<base_topic>/bridge/relay/<исходный относительный topic>` с конвертом v1, исходным payload и признаком сохранённой доставки. Прямой Z2M-маршрут не меняется.

Обратные команды идут только известным устройствам через `/set|/get` и через `bridge/request/permit_join|device/rename`. Облачная подписка MQTT 5 сохраняет RETAIN отправителя и не получает retained-команды при подписке; такие команды отклоняются. Чистые сессии, QoS 0 и выключенная очередь QoS 0 исключают накопление и автоматический повтор после обрыва. После восстановления исходные retained snapshot запрашиваются повторной подпиской локальному broker; живые сообщения из памяти не воспроизводятся.

## Конфигурация и секреты

В git хранятся только шаблоны без MQTT credentials и Zigbee network key. Одноразовые `secret.yaml` и `connector.json` скачиваются из кабинета и остаются в ignored runtime-каталогах. Credentials локального broker для connector вводятся только на машине пользователя и не отправляются GrowerHub. Новый пакет собирается Docker Compose из зафиксированных зависимостей; старые установки `bridge.conf` автоматически не изменяются.

Client ID соединения connector с локальным broker равен `<GrowerHub client ID>-local`; он постоянен для координатора и различается у разных подключений, в том числе при общем локальном broker. Облачное соединение сохраняет выданный backend client ID.

Шаблон для новой машины:

```text
zigbee_coordinator/data/secret.example.yaml
```

Файлы runtime-состояния, логи, база Zigbee-сети, coordinator backup, `node_modules` и локальные данные Mosquitto не коммитятся.

## Запуск

- `start-coordinator.bat` проверяет конфиг и свободный frontend port `8080`, затем запускает Zigbee2MQTT отдельным процессом по абсолютному пути в кавычках. COM-порт и adapter выбираются в `setup-coordinator.bat`.
- `status-coordinator.bat` и `stop-coordinator.bat` распознают только Node.js с абсолютным путём `zigbee2mqtt/index.js` текущей установки. Чужой процесс на `8080` или другая установка Zigbee2MQTT не считаются своими и не останавливаются; занятый порт блокирует новый запуск.
- Linux-пакет запускается через Docker Compose, имеет явный USB mapping и постоянный volume.

## Обновление Zigbee2MQTT

Обновление версии Zigbee2MQTT меняет внешний код, MQTT API и поддержку устройств. Такое изменение должно фиксироваться отдельным commit и проверяться запуском coordinator, публикацией `bridge/state`, чтением `bridge/info` и командой на безопасный request topic.
