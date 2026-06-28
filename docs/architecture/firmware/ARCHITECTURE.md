# Firmware

Firmware управляет устройством, читает датчики, управляет исполнительными устройствами, публикует state и service events, принимает команды через MQTT.

## Структура

- `src/app` - точка сборки приложения.
- `src/core` - runtime, context, event queue, scheduler и базовый интерфейс module.
- `src/modules` - прикладные модули устройства.
- `src/services` - Wi-Fi, MQTT, storage, time, OTA, web config и device identity.
- `src/drivers` - драйверы DHT, soil, relay и RTC.
- `src/config` - build flags, hardware profile и pin map.
- `src/util` - JSON, logging и MQTT codec.
- `test` - unit-тесты PlatformIO.

## Модули

- `StateModule` формирует и публикует state.
- `CommandRouterModule` обрабатывает MQTT-команды и ACK.
- `ActuatorModule` управляет насосом, светом и состоянием ручного полива.
- `SensorHubModule` сканирует порты и читает датчики.
- `ConfigSyncModule` синхронизирует сценарии.
- `AutomationModule` выполняет автоматизацию полива и света.
- `OtaModule` подтверждает OTA и rollback.

## Контракты

Firmware использует системный MQTT-контракт state, ack и service events из `docs/architecture/architecture.md`.
