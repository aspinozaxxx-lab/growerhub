### fix(mqtt): fw_ver only in state

- fix(firmware): state payload teper' tol'ko s fw_ver; ubrany fw/fw_name/fw_build i svyazannye define.
- fix(backend): DeviceState DTO i testy obnovleny pod tol'ko fw_ver.

### fix(ci): ispravlen env dlya platformio testov

- fix(ci): pio test teper' zapuskaetsya s env "wifi_service_test_legacy" v CI.

### chore(nginx): logi i tls nastrojki dlya medlennogo pervogo zaprosa

- chore(nginx): dobavlen log_format timed s request_time/upstream/ssl v access log dlya growerhub.ru.
- chore(nginx): TLS tolko 1.2/1.3 + session cache/timeout + OCSP stapling + resolver.
- chore(nginx): dobavleny keepalive_timeout/keepalive_requests/tcp_nodelay v http{}.

﻿### fix(backend): testy startuyut na H2 bez sql init

- fix(backend): otklyuchen spring.sql.init v testovom profile, chtoby schema ne dublirovalas' pri ddl-auto=create-drop.

### feat(auth): refresh tokeny + auto-refresh na fronte

- feat(server): dobavleny refresh tokeny v BD (hash + expires/revoked) i cookie `gh_refresh_token` (httpOnly).
- feat(server): dobavleny endpointy `POST /api/auth/refresh` (rotaciya refresh + novyj `access_token`) i `POST /api/auth/logout` (revok + ochistka cookie).
- feat(front): dobavlen edinyj fetch-wrapper `frontend/src/api/client.js` s avtomaticheskim refresh+retry pri 401 i zashchitoj ot parallel'nyh refresh.
- fix(front): pri istekshey sessii pokaz oshibok 401 podavlyaetsya (SESSION_EXPIRED), prilozhenie uhodit na login bez krasnyh plashchek.

### feat: dobavlen polnyj razdel rasteniĭ na fronte i rasshirena backend-model'

- Dobavleny novye polya u modeli rastenija: `plant_type`, `strain`, `growth_stage`.
- Dobavlena alembic-migracija dlya polej v tablice `plants`.
- fix(server): POST/PATCH `/api/plants` teper' sohranyaet i vozvrashchaet `plant_type`, `strain`, `growth_stage`.
- refactor(front): stadii rastenij teper' pokazyvayutsya po-russki cherez edinyj modul (select/avatary/kartochki).
- fix(front): dashboard beret `growth_stage`/`plant_type` esli zadany, inache fallback na avto-stadiyu po vozrastu.
- fix(front): vremya zhurnala/poliva formatiruetsya v Europe/Moscow; UTC datetime bez timezone interpretiruetsya korrektno.
- fix(front): status aktivnogo poliva vosstanavlivaetsya pri zagruzke dashborda cherez `/api/manual-watering/status`.
- refactor(front): vveden `frontend/src/domain/plants` kak edinaya tochka pravdy dlya tipov/stadiy/labelov/auto-stage/avatars.
- refactor(front): avatary stadiy pereneseny v `frontend/src/domain/plants/avatars` s fallback (type/stage -> type/default -> generic).
- feat(front): dobavleny tipy `flowering_plants`, `houseplant`, `leafy_greens`, `fruiting_veg` i stadii `mature`, `fruit_set`.
- feat(front): dobavleny tipy `succulents_cacti`, `herbs_spices` i stadiya `bolting` (strelkovanie).
- chore(front): obnavleny porogi auto-stadii po vozrastu dlya vseh tipov; dobavleny avatary dlya novyh tipov.
- chore(front): obnovlen title vkladki brauzera: "GrowerHub - uhod za rasteniyami".
- Dobavlen PATCH dlya pereimenovanija grupp rastenij (`/api/plant-groups/{id}`).
- Rasshireny Pydantic-shemy `PlantCreate`, `PlantUpdate`, `PlantOut`, dobavlena `PlantGroupUpdate`.

### feat(front): API-klient dlja rastenij i grupp

- Rasshiren `frontend/src/api/plants.js`: dobavleny metody
  `fetchPlant`, `createPlant`, `updatePlant`, `deletePlant`,
  `fetchPlantGroups`, `createPlantGroup`, `updatePlantGroup`, `deletePlantGroup`.
- Realizovany polnye payloady so sootvetstviem backend-shemam.

### feat(front): komponenty dlya raboty s rastenijami

- Dobavlen novyj komponent `PlantCard` (otobrazhenie rastenija):
  avatar, tip, strain, gruppa, vozrast, spisok ustrojstv (DeviceCard variant="plant"),
  knopki "Zhurnal" i "Redaktirovat'".
- Dobavlen `PlantEditDialog`:
  - polnyj CRUD rastenija (create/update/delete);
  - redaktirovanie poly `name`, `plant_type`, `strain`, `growth_stage`, `planted_at`, `plant_group_id`;
  - upravlenie gruppami: sozdanie/pereimenovanie/udalenie vnutri dialoga;
  - privjazka i otvjazka ustrojstv (assign/unassign) v rezhime `edit`;
  - obrabotka oshibok, lokal'noe sostojanie formy.
- feat: dobavlena prokrutka dialoga redaktirovanija rastenija i adaptivnaja dvuhkolonochnaia setka polej na desktopen

### feat(front): stranica `/app/plants`

- `AppPlants` prevrashchena iz zaglushki v polnoceNNuju stranicu:
  - zagruzka `plants`, `plantGroups`, `devices`;
  - knopka "Dobavit' rastenie";
  - otkrytie dialoga v rezhime create/edit;
  - posle sohraneniya/udalenija vypolnyaetsya polnyj refetch dannyh (plants + groups + devices).

### refactor(front): komponent DeviceCard

- Dobavlen prop `variant="plant"` dlya kompaktnogo otobrazhenija ustrojstva v kartochke rastenija.

- PlantAvatar teper' ispol'zuet rastrovye PNG po stadiyam, kartinki masshtabiruyutsya akkyratno v ramke
- PlantAvatar uproshchen do staticheskih SVG po tipu i stadii
- Ubrany staryj SVG-dvizhok i JSON-pak stadii flowering
- Dashboard ispol'zuet uproshchennyj PlantAvatar bez environment
- Dobavlen layoutEngine dlya PlantAvatar, raschety geometrii pereneseny v odno mesto
- Sloi gorshka, pochvy, steblya, list'ev i cvetov teper' ispol'zuyut obshchuyu geometriju i vyrovneny po centru kadra
п»ї- Dobavlena palitra yarkih cvetov dlya PlantAvatar (SVG)
- Sloi gorshka, pochvy, steblya, list'ev i cvetov pererisovany v bolee vyrazitelnom SVG-stile s novoj palitroj
- Chto pererisovany formy gorshka, pochvy, steblja i list'ev dlya PlantAvatar, teper' avatarnye rasteniya vyglyadyat akuratnee i blizhe k normal'nym kartinkam

- Dobavlen SVG-renderer s sloyami dlya PlantAvatar
- PlantAvatar risuet uproshchennuyu SVG kompoziciyu v zavisimosti ot stadii
- Dobavlen helper dlya opredeleniya stadii rasteniya po vozrastu
- PlantAvatar na dashborde poluchaet stadiyu po vozrastu, a ne hardsodom
- Dobavlen JSON-pak stadii flowering dlya PlantAvatar i loader
- PlantAvatar teper' mozhet brat konfiguraciyu stadii iz paka (v t. ch. data-stage)
- Dobavlen bazovyj komponent PlantAvatar i vnedren v dashboard dlya kartochek rastenij
- Ispravlen defoltnyi redirect posle logina na /app
- Dobavleno chtenie access_token iz URL posle SSO i sohranenie v localStorage
- Izmenen defoltnyi redirect dlya SSO login na /app na backende
- Avtorizovannye polzovateli avtomaticheski pereadresuyutsya s /app/login na /app

- dobavlena ansible-rol dlya ustanovki pgAdmin na gh-tools.
- sozdan playbook dlya gruppy gh_tools.
- zahardkozhena dev-konfiguraciya podklyucheniya k gh_db.

- dlya prod-fastapi dobavlen postgresql-drajver psycopg2-binary v server/requirements.txt.
- dlya prod-fastapi teper' ispol'zuetsya novyj PostgreSQL host gh-db i peremennaya DATABASE_URL prokladyvaetsya cherez ansible.
- Dobavlena ansible-rol dlya nastrojki PostgreSQL na gh-db (db-admin, baza gh_db, pg_hba i UFW).
- fix(docs): perenes redoc.standalone.js v pravilnyj static vendor dlya raboty /redoc na prod.

feat(server): dobavil firmware_version v /api/devices (fw_ver ili "old")

## 2025-11-07 РІР‚вЂќ OTA trigger cherez MQTT

- fix(server): ispol'zovan FIRMWARE_BINARIES_DIR iz nastroek v trigger-update; testy obnovleny dlya novogo puti.
- fix(server): firmware router teper' poluchaet nastrojki cherez Depends(get_settings), testi podmenyayut zavisimost'.

- feat(server): trigger-update teper' publikuet MQTT `cmd/ota` s HTTPS-url i sha256 kontrol'noj summoj.
- tests(server): dobavleny testy na 202/404/503 dlya firmware trigger-update.
- docs: README_API dopolnen opisaniem zapuska OTA.
- fix(server): upload_firmware pishet v absolyutnyj katalog iz nastroek, dobavleny logi/proverki i testy servinga.
- fix(server): perenesen defoltnyy katalog firmware v server/firmware_binaries; logiruem effektivnyy put' i mount.
- feat(server): dobavlen API `/api/firmware/versions` i UI zagruka realnyh versij bez hardkoda; dobavleny testi.

## 2025-11-06 РІР‚вЂќ Uchet MQTT soobshcheniy (ACK) v online-status

**Chto sdelano**
- Dobavlen metod `touch()` v `DeviceStateLastRepository` dlya probytiya "pul'sa" ustroystva cherez `device_state_last.updated_at` bez izmeneniya `state_json`.
- ACK-hendler (`server/app/mqtt/handlers/ack.py`) posle uspeshnogo `put_ack` vyzyvaet `touch(device_id)`, chtoby ACK prodleval onlayn-okno.
- Dobavleny testy, podtverzhdayushchie, chto ACK obnavlyaet `updated_at` i vliyaet na `is_online` v `/api/devices` i `/api/manual-watering/status`.

**Pochemu eto nuzhno**
- Ranee onlayn-status uchityvalsya glavno po HTTP i `state`. Teper' lyuboe prinyatoe ACK vydajot priznak zhizni, chtoby ne lomat' UX pri komandnyh scenariyah.

**Detali realizacii**
- `DeviceDB.last_seen` iz MQTT ne izmenyaetsya РІР‚вЂќ vse okruzheno vokrug `device_state_last.updated_at`.
- TTL/okna onlajna ne menyalis' (3 min v `/api/devices`, znachenie iz `config` v manual-watering).
- Migracii shemy ne trebuetsya.

**Testy**
- `server/tests/test_ack_affects_online_status.py`:
  - `test_ack_updates_device_state_last_when_no_prior_state`
  - `test_ack_extends_online_window_in_devices_endpoint`
  - `test_ack_extends_online_in_manual_watering_status`

**Riski i zametki**
- Pri otkaze BD pri obrabotke ACK online-status mojet ne obnovit'sya, hotya ACK budet prinyat.
- Parallel'nye testy trebuyut akkurnogo reseta in-memory storov (ustraneno fixtures).

## [Feature] Persist device state and ACK in DB

- Dobavleny modeli i tablicy device_state_last i mqtt_ack.
- MQTT state i ack teper sohranyayutsya v BD s TTL i fonovoy ochistkoy.
- API manual-watering i devices vosstanavlivayut sostoyaniya iz BD posle restarta.
- Dobavlen test persistence i repo testy.
- Front teper vidit statusy bez ozhidaniya MQTT.

РїВ»С—# Changelog

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

## [2025-11-06] DB state persistence

- Dobavleny ORM modeli `device_state_last` i `mqtt_ack` dlya sohraneniya poslednih sostoyanii i ACK v baze bez izmeneniy API.
- Sozdan repository sloy s upsert/cleanup logikoy i modulnymi testami na in-memory SQLite (repo pokryty bazovymi scenariyami).
- Startup prodolzhaet sozdat tablicy cherez `Base.metadata.create_all`, povedenie MQTT/REST ne menyalos.

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
- fix(front): vosstanovleno obyavlenie WAIT_ACK_TIMEOUT_S na stranice manual_watering (defolt 5s)
- fix(ui): rewrite manual_watering.html with clean utf-8 content
## 2025-11-27 РІР‚вЂќ Novyj frontend i Markdown statji
- dobavlen novyj frontend na React (Vite) v `frontend/` s marshrutami `/`, `/articles`, `/articles/:slug`, `/about` i linken na staroe prilozhenie cherez `/static/index.html`.
- vvedena struktura kontenta: `frontend/content/pages/` dlya leninga i about, `frontend/content/articles/` s Markdown statyami.
- statji pereneseny iz JSON v Markdown (YAML front matter + markdown telo), zagruzka cherez import.meta.glob s parserom front matter.
- nginx perenastroen: kornevoy `root` teper' `~/growerhub/frontend/dist`, spa-fallback `index.html`, `/static/` ostalsya alias na staroe prilozhenie.
- CI/CD: v job `deploy` dobavlena sborka fronta (Node 18, `npm ci`, `npm run build` v `frontend/` pered rsync), dist uezhaet na server v `~/growerhub/frontend/dist`.
- dobavlena skorost' poliva watering_speed_lph v modeli/ API nastroek ustrojstv + migraciya.
- manual-watering: raschet dlitel'nosti po obemu, zapis' v watering_logs i zhurnal rastenij, status vozvrashchaet start_time/duration.
- manual-watering: detali poliva vyneseny v plant_journal_watering_details, watering_logs udaleny, istorija polivov stroit'sya po zhurnalu, poliv bez privyazki k rasteniyam zapreshchen.
- Istorija polivov teper ispolzuet obem vody kak osnovnuyu metriku i v saidebare vidno ph i sostav udobrenij.
- Dobavlena polnocennaja stranica zhurnala rastenija: prosmotr, dobavlenie, izmenenie, udalenie zhjurnalnyh zapisej. Fiks bitogo teksta v istorii polivov, normalnye podpisi.
- sso callback: po umolchaniyu vozvrashchaet redirect s tokenom v URL, link-mode uhodit na `/static/profile.html`.
