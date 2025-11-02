_Обновлено: 2025-10-26 — описание приведено в соответствие с прошивкой ESP32 (шаг 4)._ 

# Руководство по протоколу ручного полива

## Общее описание
GrowerHub — MVP системы «умного полива». ESP32 в режиме ручного полива управляет насосом по командам сервера FastAPI. Связь строится поверх MQTT (Mosquitto) с дополнительными HTTP-эндпоинтами для фронтенда `/static/manual_watering.html`.

## Подключение и устойчивость связи
### Wi-Fi STA
- Устройство работает в режиме STA и подключается к локальной точке доступа.
- После загрузки ESP32 запускает процесс подключения к Wi-Fi, затем к MQTT-брокеру.

### MQTT (Mosquitto)
- Брокер: Mosquitto, порт `1883`.
- Аутентификация: `username=mosquitto-admin`, `password=qazwsxedc`.
- `clientId=<DEVICE_ID>` (например, `ESP32_2C294C`). Идентификатор уникален для каждого устройства и используется в именах топиков.
- QoS всех обменов — `1`.
- При разрыве соединения устройство восстанавливает MQTT-сессию и повторно публикует `state` с `retain=true`, чтобы сервер видел актуальное теневая состостояние.

## MQTT-топики
| Направление | Топик | Описание | QoS | Retain |
| --- | --- | --- | --- | --- |
| Сервер → Устройство | `gh/dev/<DEVICE_ID>/cmd` | Команды управления насосом (`pump.start`, `pump.stop`) | 1 | false |
| Устройство → Сервер | `gh/dev/<DEVICE_ID>/ack` | Подтверждения (ACK) обработки команд | 1 | false |
| Устройство → Сервер | `gh/dev/<DEVICE_ID>/state` | Retained-снимок состояния устройства | 1 | true |

`<DEVICE_ID>` — уникальный идентификатор конкретного контроллера ESP32 (например, `ESP32_2C294C`). Сервер и фронтенд используют его, чтобы подписываться на нужные топики.

## Серверная реализация MQTT-слоя

Основная логика размещена в каталоге `server/app/api/routers/mqtt/` и разделена по ответственностям:

- `topics.py` — шаблоны и генераторы топиков `gh/dev/<DEVICE_ID>/(cmd|ack|state)`.
- `serialization.py` — Pydantic-модели команд, ACK и теневых состояний; JSON-(де)сериализация.
- `client.py` — адаптер над `paho-mqtt` с публикацией QoS=1.
- `handlers/ack.py`, `handlers/device_state.py` — фильтры топиков и парсинг payload для подписчиков.
- `router.py` — классы `MqttAckSubscriber` и `MqttStateSubscriber`, маршрутизация входящих сообщений.
- `store.py` — in-memory `AckStore` и `DeviceShadowStore`, используемые HTTP-слоем.
- `lifecycle.py` — единые точки `init/start/stop/shutdown` для паблишера, подписчиков и сторажей.

FastAPI-приложение (`server/app/main.py`) вызывает `app.api.routers.mqtt.lifecycle` на старте и остановке, чтобы держать подключение к брокеру и обновлять сторы. API `/api/manual-watering/*` (`server/api_manual_watering.py`) получает зависимости через `app.api.routers.mqtt.lifecycle` и `app.api.routers.mqtt.store`: публикует команды, читает ACK и представление теневого состояния для фронтенда.

## Форматы сообщений
### Команды сервера (`gh/dev/<DEVICE_ID>/cmd`)
Устройство поддерживает две команды:

```json
{
  "type": "pump.start",
  "duration_s": 20,
  "correlation_id": "abc123"
}
```

```json
{
  "type": "pump.stop",
  "correlation_id": "abc124"
}
```

- `type` — тип команды (`pump.start` или `pump.stop`).
- `duration_s` — длительность сессии ручного полива в секундах (обязателен для `pump.start`).
- `correlation_id` — маркер запроса, который сервер возвращает пользователю для ожидания ACK.

### Подтверждения устройства (`gh/dev/<DEVICE_ID>/ack`)
ACK публикуется без retain (одноразовое подтверждение) и передаёт результат обработки команды:

```json
{
  "correlation_id": "abc123",
  "result": "accepted",
  "status": "running"
}
```

```json
{
  "correlation_id": "abc124",
  "result": "accepted",
  "status": "idle"
}
```

```json
{
  "correlation_id": "abc123",
  "result": "error",
  "reason": "bad command format"
}
```

- `status` принимает значения `running` или `idle` и отражает текущую работу насоса.
- `correlation_id` позволяет серверу связать ACK с исходной командой пользователя.
- В случае ошибок поле `reason` содержит причину отклонения.

### Состояние устройства (`gh/dev/<DEVICE_ID>/state`, retained)
Retained-сообщение описывает теневая состояние (Device Shadow) ручного режима:

```json
{
  "manual_watering": {
    "status": "running",
    "duration_s": 20,
    "started_at": "1970-01-01T00:00:00Z",
    "correlation_id": "abc123"
  },
  "fw": "esp32-alpha1"
}
```

```json
{
  "manual_watering": {
    "status": "idle",
    "duration_s": null,
    "started_at": null,
    "correlation_id": null
  },
  "fw": "esp32-alpha1"
}
```

- Сообщение сохраняется брокером (retain) и немедленно выдаётся при подписке, поэтому сервер определяет онлайн/офлайн состояние устройства, состояние насоса и активный сеанс полива.
- `started_at` пока заполняется заглушкой (нет RTC/NTP); в будущих версиях будет реальное значение в UTC.

## Поведение устройства
1. **Старт:** ESP32 подключается к Wi-Fi, затем к MQTT, публикует retained `state` с `status="idle"`.
2. **Команда `pump.start`:** включает насос (`app.setManualPumpState(true)`), сохраняет локальные флаги (`g_isWatering`, `g_wateringDurationSec`, `g_activeCorrelationId`), публикует ACK и обновлённый `state` со `status="running"`.
3. **Команда `pump.stop`:** выключает насос, публикует ACK и `state` со `status="idle"`.
4. **Автоостановка по таймеру:** по истечении `duration_s` насос отключается автоматически, публикуется `state` со `status="idle"`.
5. **Реконнект MQTT:** при восстановлении соединения устройство повторно публикует retained `state`, чтобы сервер видел, что контроллер снова онлайн.

## Примечания
- QoS=1 выбран для надёжной доставки без сложного дедуплирования.
- Retain=true используется только для топика `state`; все ACK и команды отправляются без retain.
- Сервер применяет HTTP-эндпоинт ожидания (`/api/manual-watering/wait-ack`) для синхронизации с ACK.
- UI `/static/manual_watering.html` отображает прогресс по данным ACK и текущему `state`.

## Будущие расширения (TODO)
- Синхронизация времени через NTP для корректного заполнения `started_at`.
- Периодическая (heartbeat) публикация `state`, даже без команд.
- Поддержка обновления прошивки по MQTT.
- Расширение протокола дополнительными типами команд (датчики, освещение и т.п.).
