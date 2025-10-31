# Changelog

## 2025-10-31
- add IWiFiSettings interface
- introduce WiFiService (DI), no integration yet
- integrate WiFiService into main (sync connect + async reconnect loop)
- use WiFiService online flag in consumers (non-breaking)
- add minimal tests for WiFiService via PlatformIO/Unity; introduce WiFiShim for fakes
- add CI job for WiFiService tests; add README testing section
- Перенесена логика ручного полива из `main.cpp` в `Application`, чтобы централизовать управление насосом и таймаутами.
- MQTT вынесен в `Network/MQTTClient`; main.cpp упрощён; поведение неизменено.
- main.cpp ???????; ??? ?????? manual/state/MQTT ????? ? ??????? Application ? Network/MQTTClient.
- ?????????? ???????????? retained state ? heartbeat ?? `main.cpp` ? `Application`, ???????? ?????? MQTT ? ??????????.

## 2025-10-23
- Переведено описание прошивки на русский язык и обновлена документация под встроенные дефолты.
- Удалена зависимость от SPIFFS и `config.ini`, все настройки хранятся в коде.
- Обновлены настройки PlatformIO и очищены вспомогательные скрипты загрузки SPIFFS.
