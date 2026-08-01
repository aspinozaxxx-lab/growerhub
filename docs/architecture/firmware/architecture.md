# Firmware

Firmware является исполнительным контуром Grovika: читает датчики, управляет реле, публикует state и service events и принимает команды через MQTT. Решения автоматизации и календарные расписания выполняет backend.

## Структура

- `src/app` - точка сборки приложения.
- `src/core` - runtime, context, event queue, scheduler и базовый интерфейс module.
- `src/modules` - прикладные модули устройства.
- `src/services` - Wi-Fi, MQTTS, storage, сетевое UTC, OTA, web config и device identity.
- `src/drivers` - драйверы DHT, soil и relay.
- `src/config` - build flags, hardware profile и pin map.
- `src/util` - JSON, logging и MQTT codec.
- `test` - unit-тесты PlatformIO.

## Модули

- `StateModule` формирует и публикует state.
- `CommandRouterModule` обрабатывает MQTT-команды и ACK.
- `ActuatorModule` управляет насосом, светом и состоянием ручного полива.
- `SensorHubModule` сканирует порты и читает датчики.
- `OtaModule` подтверждает OTA и rollback.

## Контракты

Firmware использует системный MQTT-контракт state, ack и service events из `docs/architecture/architecture.md`.

Серийная подготовка формирует `device_id` вида `GROVIKA_XXXXXX` из последних трёх байт eFuse MAC, получает одноразовый пароль через административный API и записывает `/cfg/mqtt.json` в LittleFS. Пароль не показывается в web UI и логах. Без корректной конфигурации MQTTS не запускается; web UI показывает `device_id`, endpoint и диагностическое состояние подключения.

Соединение использует `WiFiClientSecure`, `growerhub.ru:8883` и встроенный корневой сертификат ISRG Root X1. NTP нужен только для проверки TLS-сертификата и UTC-меток телеметрии. Аппаратного RTC и локальных календарных расписаний нет. Длительность команды `pump.start` отсчитывается монотонно через `millis()` и ограничивается аппаратным максимумом, поэтому остановка насоса не зависит от сети или абсолютного времени.
