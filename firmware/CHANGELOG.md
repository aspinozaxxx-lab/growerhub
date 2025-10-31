# Changelog

## 2025-11-01
- Dobavleny konstanty intervalov NTP i pinov RTC; dobavlen fail dokumentacii po nastroike RTC/NTP.
- Dobavlen adapter RTC (DS3231), SystemClock sam inicializiruet RTC; pri otsutstvii modulya - bezopasnyi folbek.
- Obedineny testy WiFi i SystemClock v odnom okruzhenii PlatformIO (wifi_service_test).
- Ispravlena kodirovka FakeScheduler.h (UTF-8), stabilizirovany testy SystemClock.
- Dobavlen karkas SystemClock i DI-interfeisy vremeni; bezopasnye folbeki sokhranjayut tekushchee povedenie.
- Realizovana sinhronizacia: 3 NTP-popytki pri starte, retry kazhdyh 30 s i planovyi resink raz v 6 ch s proverkoi sdviga >31 dnya.
- Dobavleny Unity-testy SystemClock (folbeki, startovye popytki, filtr >31 dnya, retry 30 s, resink 6 ch, granichnye gody).
