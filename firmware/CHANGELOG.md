## 2026-01-27
- feat(firmware): ogranichenie moshchnosti Wi-Fi TX dlya profilya esp32c3_supermini (qdbm).
- refactor(firmware): ubrany diagnosticheskie [C3DBG] logi WiFiService i logirovanie SSID/parolya.

## 2026-01-24
- test: vklyuchen UNITY 64-bit support dlya native test env.
- ci: CI pereshel na env test.
- refactor: udalena legacy src/test, v2 pereexal v src/test, obnovlen platformio.ini i puti test storage.

﻿# Changelog

## 2026-01-06
- feat(v2): zapis UTC v DS3231 posle uspeshnogo NTP sync (TimeService + RTC provider).
- feat(v2): dobavlen WriteEpoch v Ds3231Driver i SetUtc v Ds3231RtcProvider.
- test(v2): testy dlya BCD encode/decode i rascheta dnya nedeli DS3231.
- feat(v2): dobavlen pio extra_script dlya generacii GH_FW_VERSION na osnove git daty i schetchika kommitov.
- web: pokaz status Wi-Fi/MQTT na glavnoj stranice (statichno, po F5).

## 2025-12-28
- fix(v2): dobavlen MQTT connect/reconnect v MqttService (setServer, retry 5s, rc reason).
- feat(v2): rasshireny logi [WIFI]/[CFG]/[MQTT]/[STATE]/[CMD] dlya podklyucheniya, konfiguracii, publish i rx.
- docs(v2): Dobavleny dokumentacionnye kommentarii (shapki fajlov + opisaniya parametrov publichnyh API) dlya v2 src.
- feat(v2): migraciya proshivki Grovika v novyj karkas (runtime, modulnaya arhitektura, MQTT cmd/ack/state).
- feat(v2): AP + web-setup dlya Wi-Fi, builtin defaults, zapis wifi.json.
- feat(v2): storage na LittleFS, retained cfg sync (scenarios.json), validaciya i migraciya shemy.
- feat(v2): avtomatika (poliv po vlazhnosti i po raspisaniyu, svet po raspisaniyu) + QoS1 events.
- feat(v2): OTA pull + rollback s markerami pending/confirm.
- feat(v2): RJ9 auto-detect soil sensors + state; dobavlen DHT22 driver + air state.
- remove(v2): udalena REST telemetriya (HTTP POST status) iz v2.
- test(v2): 39 unity-testov (native) - codec/ack, config/storage, wifi/web, soil/automation, ota rollback, dht22, scheduler/event queue.
- fix(v2): device_id teper' stroitsya kak v legacy (grovika_ + 3 bajta MAC, verhnij registr), dobavlen DeviceIdentity i unity-test.
- test(v2): test_storage_* ubrany iz korna, testovye khranilishcha pereneseny v test/tmp, dobavlen gitignore.
- fix(v2): DHT22 otklyuchen po umolchaniyu; LittleFS sozdaet /cfg i defolt scenarii/device.json bez oshibok.
- fix(v2): web-portal Wi-Fi prinimaet /save po GET i POST dlya sohraneniya.

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

## 2025-12-29
- feat(firmware): web-ui vynesena v services/website, novyi UI s fetch, status Sokhraneno, pokaz parolya.
- feat(firmware): API /api/networks (GET/POST/DELETE) dlya spiska setei s sohraneniem i sobytiem kWifiConfigUpdated.
- feat(firmware): EncodeWifiConfig teper' kodiruet spisok setei, ValidateWifiConfig trebuet hotya by odin nepustoi SSID.
- fix(firmware): WiFiService sbros indexa/sostoyaniya pri kWifiConfigUpdated i perebor setei po krugu do soedineniya.
- test: obnovleny testy Wi-Fi konfiguracii, dobavlen encode/decode spiska setei.
- fix(firmware): snizhen stack-nagruzki v WebConfigService API handlerah (staticheskie bufery JSON/spisok setei).
