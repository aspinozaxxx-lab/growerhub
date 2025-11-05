# Changelog

## 2025-11-05
- fix(firmware): vyrovnyan ACK topic na gh/dev/<id>/state/ack dlya sovmestimosti s serverom
- feat(firmware): dobavlena komanda reboot; bezopasnyj ostan nasosa i restart ESP posle otpravki state/ACK

## 2025-11-01
- Начальная синхронизация NTP теперь выполняется только после подключения к Wi-Fi.
- РЈСЃРёР»РµРЅР° СЂР°Р±РѕС‚Р° СЃ DS3231: РєРѕСЂСЂРµРєС‚РЅР°СЏ РёРЅРёС†РёР°Р»РёР·Р°С†РёСЏ EOSC/OSF, РЅР°РґС‘Р¶РЅРѕРµ С‡С‚РµРЅРёРµ/Р·Р°РїРёСЃСЊ BCD, РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ Р·Р°РїРёСЃРё РїРѕСЃР»Рµ NTP Рё РґР°РјРї СЂРµРіРёСЃС‚СЂРѕРІ РІ РѕС‚Р»Р°РґРєРµ.
- Dobavlen defoltniy NTP-klient dlya SystemClock (ESP32), vklyucheny retry/resync bez DI; dobavlen I2C-skaner v debug dlya proverki DS3231.
- Dobavleny konstanty intervalov NTP i pinov RTC; dobavlen fail dokumentacii po nastroike RTC/NTP.
- Dobavlen adapter RTC (DS3231), SystemClock sam inicializiruet RTC; pri otsutstvii modulya - bezopasnyi folbek.
- Obedineny testy WiFi i SystemClock v odnom okruzhenii PlatformIO (wifi_service_test).
- Ispravlena kodirovka FakeScheduler.h (UTF-8), stabilizirovany testy SystemClock.
- Dobavlen karkas SystemClock i DI-interfeisy vremeni; bezopasnye folbeki sokhranjayut tekushchee povedenie.
- Realizovana sinhronizacia: 3 NTP-popytki pri starte, retry kazhdyh 30 s i planovyi resink raz v 6 ch s proverkoi sdviga >31 dnya.
- Dobavleny Unity-testy SystemClock (folbeki, startovye popytki, filtr >31 dnya, retry 30 s, resink 6 ch, granichnye gody).
