# Архитектура системы

Компоненты:
- Frontend (React).
- Backend (Java).
- Firmware (C++ src).
- Devices (железо).
- DB.
- MQTT broker.

Каналы:
- Frontend <-> Backend: REST.
- Backend <-> Devices: MQTT.

MQTT topics:
- `gh/dev/<device_id>/state`: текущее состояние устройства.
- `gh/dev/<device_id>/state/ack`: подтверждения команд.
- `gh/dev/<device_id>/events`: служебные события устройства.

Принципы:
- REST и MQTT - тонкие адаптеры.
- Бизнес-логика - в доменах backend и в модулях firmware.
- Frontend не является источником правды.
- Форматы MQTT payload (state/cmd/ack) - контракт системы; изменения требуют обновления docs/architecture и ADR при исключениях.

Контракт статусов датчиков:
- Пользовательский текущий статус датчика передается в `state`.
- Допустимые значения: `OK`, `DISCONNECTED`, `ERROR`.
- Для air используется общий физический статус DHT и он дублируется в logical sensors `AIR_TEMPERATURE` и `AIR_HUMIDITY`.
- Для soil статус задается отдельно по каждому `port/channel`.

State payload (sensor status):
- `air.status`: текущий статус физического air sensor.
- `soil.ports[].status`: текущий статус датчика на конкретном порту.
- Существующие поля `temperature`, `humidity`, `percent`, `available`, `detected` сохраняются для обратной совместимости.

Service events:
- `SENSOR_READ_ERROR`: одноразовое событие ошибки чтения датчика.
- `DEVICE_REBOOT_SENSOR_FAILURE`: одноразовое событие перезагрузки устройства из-за сбоя датчика.
- Service events публикуются в `gh/dev/<device_id>/events` и используются backend/admin для диагностики.

Поведение при auto reboot:
- Если auto reboot включен и firmware видит ошибку чтения, оно сначала отправляет `SENSOR_READ_ERROR`.
- Затем firmware отправляет `DEVICE_REBOOT_SENSOR_FAILURE`.
- После успешного восстановления устройство публикует новый `state`, и пользовательский status должен перейти в `OK` без сохранения старой ошибки как текущей.

Источники правды (концептуально):
- Device: фактическое состояние железа.
- Backend DB: история, конфигурация, устойчивое состояние.
- Shadow/кэши: производные данные.
- Frontend: производное представление.

ASCII-схема:

+-----------+     REST     +-----------+         +---------+
| Frontend  | <--------->  | Backend   | <-----> |   DB    |
+-----------+              +-----------+         +---------+
                                |
                                v
                           +-----------+
                           | MQTT      |
                           | broker    |
                           +-----------+
                                |
                                v
                           +-----------+
                           | Devices   |
                           +-----------+
