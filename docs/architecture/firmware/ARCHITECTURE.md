# Firmware: фактическая архитектура (v2, src/test)

Слои:
- core: Context, EventQueue, Scheduler, AppRuntime.
- modules: StateModule, CommandRouterModule, ActuatorModule, SensorHubModule, ConfigSyncModule, AutomationModule, OtaModule.
- services: MqttService, WiFiService, WebConfigService, StorageService, TimeService, DeviceIdentity.
- drivers: dht, soil, relay, rtc.
- util: JsonUtil, Logger, MqttCodec.
- config: BuildFlags, HardwareProfile, PinMap.

Модули и ответственность:
- StateModule: формирование и публикация state.
- CommandRouterModule: обработка MQTT команд и reboot, отправка ACK.
- ActuatorModule: насос/свет и состояние ручного полива.
- SensorHubModule: сканирование портов и чтение датчиков.
- ConfigSyncModule: синхронизация конфигурации сценариев.
- AutomationModule: автоматизация полива и света.
- OtaModule: подтверждение OTA и rollback.

Сервисы:
- MqttService: подключение, publish/subscribe, доставка сообщений в EventQueue.
- WiFiService: STA/AP режимы и список сетей.
- WebConfigService: web-конфиг Wi-Fi и запись конфигурации.
- StorageService: файловое хранилище конфигураций/состояний.
- TimeService: время, NTP/RTC синхронизация и таймстемпы.
- DeviceIdentity: формирование device_id из MAC.

State и команды:
- State формируется в StateModule и публикуется через MqttService.
- MQTT команды обрабатывает CommandRouterModule через события из MqttService/EventQueue.

Тесты (test/test_v2):
- Набор unit-тестов модулей и сервисов (command_router, config_sync, mqtt_codec, time_service и др.).