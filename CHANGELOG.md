# Changelog

## [2025-11-05] MQTT reboot command
- chore(server): ubrany debug-endpointy i vremennoe logirovanie po ACK
- fix(firmware): ACK dlya reboot teper' ispol'zuet status 'running'|'idle' kak u manual; udalen status 'reboot'
- fix(server): vklyuchen i zalogirovan ACK-subscriber; dobavlen debug status endpoint
- fix(firmware): vyrovnyan ACK topic na gh/dev/<id>/state/ack dlya sovmestimosti s serverom
- feat(mqtt): dobavlen komandnyj tip reboot i model dlya serialize protokola
- feat(api): dobavlen REST endpoint /api/manual-watering/reboot s vydachej correlation_id i publikaciej komandy reboot v MQTT
- feat(ui): dobavlena knopka "Peregruzit' ustrojstvo" na manual_watering s ozhidaniem ACK

## [2025-11-04] Manual watering status fallback
- fix(manual_watering): dobavlen db fallback v /api/manual-watering/status, chtoby obogatit status onlayna i poslednee nablyudenie pri otsutstvii teni
- tests: dobavleny sluchai bez teni (online/offline) dlya verifikacii db_fallback logiki
- fix: privedena metka source k `db_fallback` pri ottyazhnom statuse iz DeviceDB

## [2025-11-02] Tests translit cleanup
- docs(tests, manual_watering): kommentarii i dokstringi perevedeny v russkiy translit; logika bez izmeneniy
- pytest: 32 passed

## [2025-11-02] FastAPI package rename
- refactor(server): pereimenovan katalog app/api v app/fastapi, vse importy obnovleny
- pytest: 32 passed

## [2025-11-02] MQTT package move
- refactor(server): perenos paketa mqtt iz app/api/routers/ v app/ s ispravleniem importov
- pytest: 32 passed

## [2025-11-02] MQTT docs refresh
- docs(server/mqtt): dobavleny kommentarii i dokstringi bez izmeneniya logiki
- zachem: povyshenie chitaemosti, snizhenie bus-factora
- pytest: 32 passed

## [2025-11-02] MQTT cleanup
- ochistka prokladok MQTT, vse importy perevedeny na novyy paket
- pytest: 32 passed

## [2025-11-02] MQTT modules relocate
- refactor(server/mqtt): perenos v app/api/routers/mqtt s prokladkami dlya sovmestimosti
- peremeshcheny: mqtt/{__init__.py,config.py,interfaces.py,topics.py,serialization.py,store.py,client.py,router.py,lifecycle.py}; mqtt/handlers/{__init__.py,ack.py,device_state.py}
- obnovleny prokladki v server/service/mqtt/* i server/device_shadow.py dlya sokhraneniya staryh importov bez lomki API
- riski: global'nye singltony init/shutdown i dvoinaya inic., mitigaciya cherez aliasy sys.modules + pytest 32 testa

## [2025-11-01] Cleanup warnings and improve API docs
- Ustraneny ili podavleny 44 preduprezhdeniya (Deprecation/SQLAlchemy/Pydantic/Pytest).
- Dobavlen pytest.ini s filtratsiey lishnikh preduprezhdeniy.
- README_API.md dopolnen tablitsey "endpoint -> fayl routera".
- Testy uspeshno prokhodyat bez varningov.


## [2025-10-31] Tests fixed after router refactor
- Ispravleny puti importov v testakh (`api_manual_watering` -> `app.api.routers.manual_watering`).
- Udaleny ustarevshie testy, neaktualnye posle refaktoringa.
- Testy uspeshno prokhodyat na vetke `origin/main`.

## [2025-10-31] FastAPI main cleanup
- Zavershena chistka main.py: import-taym create_tables() udalyon, initsializatsiya BD teper proiskhodit v startup. Povedenie API i URL neizmenny.


## [2025-10-31] FastAPI routers layout
- Vyneseny HTTP-endpoynty v otdelnye routery: manual_watering.py, devices.py, history.py, firmware.py. V main.py ostalis tolko tochka vkhoda, sobytiya i statika. Kontrakty i URL bez izmeneniy.

## [2025-10-31] FastAPI smoke checks and docs
- Provereny testy/smouk-proverka posle vynosa routerov; dobavleny OpenAPI-tegi dlya gruppirovki; dobavlen README_API.md. Kontrakty ne izmenyalis.

## [2025-10-31] FastAPI startup safety net
- Tsel: podgotovit perenos initsializatsii BD v `startup`, ne lomaya tekuschie testy i okruzhenie.
- Vstavlen dubliruyuschiy vyzov `create_tables()` v `@app.on_event("startup")` s bezopasnym logirovaniem pri povtore; import-time vyzov ostavlen bez izmeneniy.
- Sovmestimost API i testov sokhranena.

## [2025-10-30] MQTT refactor - summary
- Provedyon polnyy refaktoring MQTT-podsistemy: kod perenesyon v `server/service/mqtt/`.
- Razdeleny urovni:
  - `topics.py`, `serialization.py`, `interfaces.py`, `config.py` - protokol i bazovye tipy.
  - `client.py`, `lifecycle.py` - publikatsiya i zhiznennyy tsikl klienta.
  - `router.py`, `handlers/` - marshrutizatsiya i obrabotchiki podpisok.
  - `store.py` - story dlya ACK i tenevykh sostoyaniy.
- Obnovleny importy i testy, dokumentatsiya `docs/manual_watering_protocol.md`.
- Protokol MQTT i formaty JSON ne izmenyalis.
- Vse testy uspeshno prokhodyat (`pytest -q` zelyonyy).

## [2025-10-30] MQTT refactor - step 8
- Udaleny sovmestimye moduli server/mqtt_*.py, vse importy perevedeny na server/service/mqtt.
- Obnovleny server/app/main.py, testy i dokumentatsiya manual_watering na novuyu arkhitekturu.
- Tsel: zavershit migratsiyu MQTT-sloya, ostavit edinyy servisnyy fasad i aktualnuyu dokumentatsiyu.

## [2025-10-26] FastAPI MQTT lifecycle integration
- FastAPI startup/shutdown teper ispolzuyut fasady service.mqtt.lifecycle vmesto ruchnogo vyzova podpischikov.
- API manual_watering perevedyon na service.mqtt.interfaces.IMqttPublisher i novye storazhi; testy patchat lifecycle/store.

## [2025-10-26] MQTT serialization cleanup
- Udaleny izbytochnye funktsii deserialize_* i testy perevedeny na pryamoy model_validate_json.
- Sovmestimyy modul mqtt_protocol.py i testy privedeny k novym API, stroki otvetov normalizovany.

## [2025-10-26] MQTT store refactor (Ack/Shadow)
- AckStore i DeviceShadowStore pereneseny v service/mqtt/store.py s edinym upravleniem initsializatsiey.
- lifecycle teper upravlyaet zapuskom/ostanovkoy storazhey i podpischikov, API i testy perevedeny na novye importy.
- Doktesty/ozhidaniya obnovleny na normalnye stroki oshibok i sovmestimye patchi.

## [2025-10-26] MQTT router refactor (subscribers)

- Logika podpischikov i obrabotchikov vynesena v service/mqtt/router.py i service/mqtt/handlers/ (ACK/state, parsing topikov i payload).
- service/mqtt/lifecycle.py teper upravlyaet zapuskom/ostanovkoy podpischikov cherez start/stop fasady.
- server/mqtt_subscriber.py ostavlen kak sovmestimaya prokladka dlya starykh importov i testov.

## [2025-10-26] MQTT service refactor (topics/serialization)

- Vynesli shablony topikov, modeli i serializatsiyu MQTT v server/service/mqtt/ (topics/serialization/interfaces/config), sokhraniv format payload.
- mqtt_subscriber, API i testy perevedeny na novye importy, dobavlen sovmestimyy reeksport server/mqtt_protocol.py.
- Obnovili dostup k MQTT-nastroykam cherez servisnuyu obyortku bez izmeneniya povedeniya initsializatsii.

## [2025-10-26] MQTT lifecycle refactor (publisher)

- Realizovany service/mqtt/client.py (adapter PahoMqttPublisher) i service/mqtt/lifecycle.py s fasadom init/get/shutdown.
- server/mqtt_publisher.py zamenyon tonkoy prokladkoy s TODO na udalenie; prilozhenie i testy ispolzuyut service.mqtt.lifecycle.
- Povedenie publikatsii komand, QoS i obrabotka oshibok podklyucheniya sokhraneny, pytest ostayotsya zelyonym.

## [2025-10-24] MQTT step 4

- Kontroller publikuet tekuschee sostoyanie ustroystva v topik `gh/dev/ESP32_2C294C/state` s `retain=true`: tuda popadaet blok `manual_watering` (status, duration_s, started_at, correlation_id) i versiya proshivki.
- Sostoyanie otpravlyaetsya posle zapuska i ostanovki poliva, posle avto-taymauta i pri uspeshnom rekonnekte MQTT, chtoby server vsegda videl aktualnyy snimok i otmechal ustroystvo kak onlayn.
- Pole `started_at` poka soderzhit zaglushku `"1970-01-01T00:00:00Z"` do integratsii realnogo UTC vremeni (NTP/RTC), zaplanirovannoy na buduschie shagi.
- Retained state ispolzuetsya serverom i frontendom (`/api/manual-watering/status`), chtoby razblokirovat knopki i otobrazhat progress ruchnogo poliva.

## [2025-10-24] MQTT step 3

- Dobavlena publikatsiya ACK v MQTT-topik ustroystva `gh/dev/ESP32_2C294C/ack` posle obrabotki komand ruchnogo poliva.
- Posle `pump.start` i `pump.stop` kontroller otvechaet s `correlation_id`, `result` i `status`, chto dayot serveru podtverzhdenie dlya frontenda cherez `/api/manual-watering/wait-ack`.
- Oshibki formata (naprimer, otsutstvuet `duration_s` ili `type`) teper vozvraschayut `result=error` i pole `reason` s opisaniem problemy.
- ACK publikuetsya bez retain, tak kak eto odnorazovoe podtverzhdenie; publikatsiya sostoyaniya ustroystva (state s retain) budet dobavlena na sleduyuschem shage.

## [2025-10-24] MQTT step 2

- Realizovan razbor JSON-komand `pump.start` i `pump.stop`, dobavleny podrobnye logi dlya otladki tsepochki server -> ustroystvo.
- Nasos upravlyaetsya cherez suschestvuyuschiy kontroller rele, sokhranyaetsya lokalnoe sostoyanie (running/idle, duration_s, correlation_id) dlya buduschikh ACK/state.
- V `loop()` dobavlen avtostop po taymeru `duration_s`, chtoby isklyuchit zatyazhnoy poliv pri potere svyazi.
- Publikatsiya ACK i state zaplanirovana na sleduyuschie shagi (shag 3+).

## [2025-10-24] MQTT step 1

- Dobavleno bazovoe podklyuchenie ESP32 k Wi-Fi (STA) i MQTT, ispolzovany login/parol Mosquitto i clientId `ESP32_2C294C`.
- Ustroystvo podpisyvaetsya na komandnyy topik `gh/dev/ESP32_2C294C/cmd` (QoS=1), logiruet vkhodyaschie soobscheniya v Serial i avtomaticheski perepodklyuchaetsya pri obryve.
- Upravlenie nasosom i publikatsiya ack/state namecheny na posleduyuschie shagi.


- chore(debug): vremennye logi po cepochke ACK manual_watering
- chore(debug): vremennye logi na stranice manual_watering
- fix(ui): restore utf-8 text on manual watering frontend
- fix(ui): convert manual_watering text to utf-8
- fix(ui): manual_watering page restored to proper utf-8 text
- chore(debug): ubrany vremennye logi po ACK i debug-blok na stranice manual_watering
- fix(ui): rewrite manual_watering.html with clean utf-8 content
