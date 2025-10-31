# Changelog


## 2025-11-01
- Добавлен каркас SystemClock и DI-интерфейсы времени; безопасные фолбеки сохраняют текущее поведение.
- Реализована синхронизация: 3 NTP-попытки при старте, ретраи каждые 30 с и плановый ресинк раз в 6 ч с проверкой сдвига >31 дня.
- Добавлены Unity-тесты SystemClock (фолбеки, стартовые попытки, фильтр >31 дня, ретраи 30 с, ресинк 6 ч, граничные годы).

## 2025-10-31
- add IWiFiSettings interface
- introduce WiFiService (DI), no integration yet
- integrate WiFiService into main (sync connect + async reconnect loop)
- use WiFiService online flag in consumers (non-breaking)
- add minimal tests for WiFiService via PlatformIO/Unity; introduce WiFiShim for fakes
- add FakeWiFi.cpp test doubles for WiFi/WiFiMulti; enable tests
- add CI job for WiFiService tests; add README testing section
- РџРµСЂРµРЅРµСЃРµРЅР° Р»РѕРіРёРєР° СЂСѓС‡РЅРѕРіРѕ РїРѕР»РёРІР° РёР· `main.cpp` РІ `Application`, С‡С‚РѕР±С‹ С†РµРЅС‚СЂР°Р»РёР·РѕРІР°С‚СЊ СѓРїСЂР°РІР»РµРЅРёРµ РЅР°СЃРѕСЃРѕРј Рё С‚Р°Р№РјР°СѓС‚Р°РјРё.
- MQTT РІС‹РЅРµСЃРµРЅ РІ `Network/MQTTClient`; main.cpp СѓРїСЂРѕС‰С‘РЅ; РїРѕРІРµРґРµРЅРёРµ РЅРµРёР·РјРµРЅРµРЅРѕ.
- main.cpp ???????; ??? ?????? manual/state/MQTT ????? ? ??????? Application ? Network/MQTTClient.
- ?????????? ???????????? retained state ? heartbeat ?? `main.cpp` ? `Application`, ???????? ?????? MQTT ? ??????????.

## 2025-10-23
- РџРµСЂРµРІРµРґРµРЅРѕ РѕРїРёСЃР°РЅРёРµ РїСЂРѕС€РёРІРєРё РЅР° СЂСѓСЃСЃРєРёР№ СЏР·С‹Рє Рё РѕР±РЅРѕРІР»РµРЅР° РґРѕРєСѓРјРµРЅС‚Р°С†РёСЏ РїРѕРґ РІСЃС‚СЂРѕРµРЅРЅС‹Рµ РґРµС„РѕР»С‚С‹.
- РЈРґР°Р»РµРЅР° Р·Р°РІРёСЃРёРјРѕСЃС‚СЊ РѕС‚ SPIFFS Рё `config.ini`, РІСЃРµ РЅР°СЃС‚СЂРѕР№РєРё С…СЂР°РЅСЏС‚СЃСЏ РІ РєРѕРґРµ.
- РћР±РЅРѕРІР»РµРЅС‹ РЅР°СЃС‚СЂРѕР№РєРё PlatformIO Рё РѕС‡РёС‰РµРЅС‹ РІСЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Рµ СЃРєСЂРёРїС‚С‹ Р·Р°РіСЂСѓР·РєРё SPIFFS.