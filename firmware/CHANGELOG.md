# Changelog

## 2025-12-28
- feat(v2): migraciya proshivki Grovika v novyj karkas (runtime, modulnaya arhitektura, MQTT cmd/ack/state).
- feat(v2): AP + web-setup dlya Wi-Fi, builtin defaults, zapis wifi.json.
- feat(v2): storage na LittleFS, retained cfg sync (scenarios.json), validaciya i migraciya shemy.
- feat(v2): avtomatika (poliv po vlazhnosti i po raspisaniyu, svet po raspisaniyu) + QoS1 events.
- feat(v2): OTA pull + rollback s markerami pending/confirm.
- feat(v2): RJ9 auto-detect soil sensors + state; dobavlen DHT22 driver + air state.
- remove(v2): udalena REST telemetriya (HTTP POST status) iz v2.
- test(v2): 39 unity-testov (native) - codec/ack, config/storage, wifi/web, soil/automation, ota rollback, dht22, scheduler/event queue.

## 2025-11-07
- feat: edinyj bezopasnyj reboot cherez SystemMonitor::rebootIfSafe (ACK, status idle/running, zaderzhka 250ms, busy-wait).
- refactor: komanda reboot po MQTT teper' deligiruyetsya v SystemMonitor; ACK uhodit iz SystemMonitor cherez MQTT helper.
- feat: avtomaticheskij reboot pri DHT22 oshibkah — porog 3 oshibki, kuldaun 5 min (helper Dht22RebootHelper).
- test: Unity-testy dlya SystemMonitor i DHT22 triggera; dobavleny feyki Arduino i WiFiShim dlya native sredy.
- misc: melkie pravki logirovaniya [SYS], sanitizaciya CRLF.

### Izmenennye/novye fajly
- src/System/SystemMonitor.h/.cpp
- src/System/Dht22RebootHelper.h/.cpp
- src/Sensors/DHT22Sensor.h/.cpp
- src/Application.h/.cpp
- src/Network/MQTTClient.h/.cpp
- src/Network/WiFiShim.h (UNIT_TEST)
- test/test_system_monitor_reboot/*
- test/test_dht22_reboot_trigger/*
- test/fakes/Arduino.*, test/fakes/FakeWiFi.cpp
- platformio.ini (test_filter/build_src_filter)

### Migraciya/Backward compat
- API: dobavlen public helper `MQTTClient::publishAckStatus(...)`.
- SystemMonitor trebuet setteri zavisimostej (pumpStatusProvider, ackPublisher, statePublisher, rebooter) — nastroeno v main.cpp; storonnego vmeshatel'stva ne trebuetsya.
- Format ACK/state ne izmenyalsya.

## 2025-11-05
- fix(firmware): unificirovan ACK dlya reboot (status='running'|'idle' kak u manual)
- fix(firmware): vyrovnyan ACK topic na gh/dev/<id>/state/ack dlya sovmestimosti s serverom
- feat(firmware): dobavlena komanda reboot; bezopasnyj ostan nasosa i restart ESP posle otpravki state/ACK

## 2025-11-01
- ��砫쭠� ᨭ�஭����� NTP ⥯��� �믮������ ⮫쪮 ��᫥ ������祭�� � Wi-Fi.
- �?�?��>��?�� �?���+�?�'�� �? DS3231: ��?�?�?���'�?���? ��?��Ő���>������Ő�? EOSC/OSF, �?���?�'��?�?�� �ؑ'��?���/��������?�? BCD, ���?�?�'�?��?��?��?��� ��������?�� ���?�?�>�� NTP �� �?���?�� �?��?��?�'�?�?�? �? �?�'�>���?���.
- Dobavlen defoltniy NTP-klient dlya SystemClock (ESP32), vklyucheny retry/resync bez DI; dobavlen I2C-skaner v debug dlya proverki DS3231.
- Dobavleny konstanty intervalov NTP i pinov RTC; dobavlen fail dokumentacii po nastroike RTC/NTP.
- Dobavlen adapter RTC (DS3231), SystemClock sam inicializiruet RTC; pri otsutstvii modulya - bezopasnyi folbek.
- Obedineny testy WiFi i SystemClock v odnom okruzhenii PlatformIO (wifi_service_test).
- Ispravlena kodirovka FakeScheduler.h (UTF-8), stabilizirovany testy SystemClock.
- Dobavlen karkas SystemClock i DI-interfeisy vremeni; bezopasnye folbeki sokhranjayut tekushchee povedenie.
- Realizovana sinhronizacia: 3 NTP-popytki pri starte, retry kazhdyh 30 s i planovyi resink raz v 6 ch s proverkoi sdviga >31 dnya.
- Dobavleny Unity-testy SystemClock (folbeki, startovye popytki, filtr >31 dnya, retry 30 s, resink 6 ch, granichnye gody).
