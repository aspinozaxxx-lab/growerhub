# Changelog

## 2026-01-09
- Plants: dobavlen harvested_at, endpoint harvest i tip zhurnala harvest dlya rastenij.
- Frontend: knopka sbora s modal'koi i sekciya arhiva rastenij s mini-kartochkami i history.
- Dashboard: status poliva pokazivaetsya dlya vseh rastenij s privyazannymi pumpami; plashki sensors/pumps renderjatsya nezavisimo.
- Dashboard UI: kartochki rastenij kompaktnee, gruppirovka po plant_group, zagolovok udaljen, sidebar sticky bez prizhima k nizhu.
- Plants UI: karandash na knopke redaktirovaniya i tekst 'Idet poliv...' na kartochkah pump.
- Backend/testy: pump.is_running beretsya iz shadow dlya spiska plants; dobavleny integracionnye proverki bindings i running.

## 2026-01-08
- DeviceShadowStore rasshiren do snapshot(meta+state) s cold start iz device_state_last.
- DeviceService stal edinym provodnikom dlya dashboard response i manual_watering status.
- /api/devices/my i /api/plants teper' berut state iz shadow, a ne iz devices polya.
- Manual watering kontroli perevedeny na DeviceService, dobavleny debug wrappery.
- Integracionnye testy obnovleny: MQTT/HTTP status i cold start dlya air_temperature.
- Vnedrena novaya schema sensors/pumps s M:N bindings, sensor_readings i plant_metric_samples; udaleny sensor_data i plant_devices.
- Perestrukturirovany device/sensor/pump/plant/journal domeny i API: novye DTO i endpoints dlya bindings i history.
- Manual'nyj poliv pereveden v pump domen, zhurnaly i metrika poliva zapisivayutsya po bindings.
- Frontend peredelan pod sensors/pumps, novye grafiki plant/sensor history i UI privyazok.
- Firmware API vosstanovlen pod /api/device/*/firmware i /api/upload-firmware; dobavlen storage dlya .bin.
- Integracionnye testy obnovleny pod novye domennye servisy i MQTT ingest.
- Fiksy UI/ingestion: ok-otvety dlya bindings, stabil'nyj parsing, chips s gruppoi/vozrastom, ubiranie free devices, avatar vsegda, HTTP state v soil/air, plus testy.

## 2025-12-31
- MQTT packet size 1024, QoS0 state i diagnostika publish rezultatov.
- RTC DS3231: presence check, chtenie epoch i warmup 10x1s bez blokirovki.
- TimeService teper' ispol'zuet RTC kak istochnik vremeni, a "[no watch]" tol'ko pri otsutstvii validnogo vremeni.
- Ruchnoi poliv: started_at i timestampy sobytii teper' iz TimeService (epoch ms), bez "1970".
- State: dobavlen status rele nasosa i garantirovany porty pochvy, DHT/soil otrazhajut dostupnost'.
- Backend: DTO dlya v2 state, shadow i sensor_data rasshireny pod soil1/soil2 i statusy rele.
- RTC: ubrany logi v chtenii RTC, chtoby izbezhat' rekurcii Logger/TimeService i panic.

## 2025-12-30
- MQTT state teper' zapisivaet istoriyu sensorov v sensor_data pri nalichii znacheniy.
- Obrabotka state unificirovana cherez DeviceService.handleState, vkljuchaya HTTP status i MQTT.

## 2025-12-29
- MQTT state teper' obnovlyaet devices.last_seen posle uspeshnogo parse.

## 2025-12-25
- MQTT state obrabotka teper' delegiruetsya v device domen, shadow store perenesen v ru.growerhub.backend.device.
- Avtoregistraciya ustroystv pri MQTT state i REST init perenesena v DeviceService.
- Avtoregistraciya ustroystv pri MQTT state soobshcheniyah: device sozdaetsya avtomaticheski pri pervom state.
- Ispravlen mapping device_state_last.state_json na TEXT, chtoby JSON ne chitalsya kak chislo/LOB; dobavleny integracionnye testy dlya /api/devices/my i /api/plants.
- Ispravlen mapping text v plant_journal_entries na TEXT i dobavlen integracionnyj test na /api/plants/{plant_id}/journal.
- Ispravlen mapping fertilizers_per_liter v plant_journal_watering_details na TEXT dlya korrektnogo chteniya stroki v zhurnale.

## 2025-12-24
- Dobavlen MQTT runtime na Paho s QoS=1, resubscribe i obrabotchikami state/ack.
- Vnedreny in-memory AckStore/DeviceShadowStore s TTL i onlayn-raschetom, plus upsert v device_state_last i mqtt_ack.
- Dobavlen ack cleanup worker i touch last_seen po MQTT ACK.
- Firmware sklad: init direktorii, static /firmware, upload/list/trigger OTA s sha256.
- Konfiguraciya privyazana k env paritetu dlya mqtt/ack/firmware.
- Dobavleny unit i integracionnye testy dlya storov, mqtt obrabotchikov i firmware/manual_watering.
- FastAPI wait-ack 408 detail v ishodnike povrezhden; v Java ispolzuem normalizovannoe soobshchenie 'ACK ne poluchen v zadannoe vremya', status i smysl sovpadayut.


## 2025-12-23
- Dobavleny domeny users, devices i manual_watering s polnym sovpadeniyem kontraktov FastAPI.
- Dobavleny in-memory storazhi shadow i ack, plus debug endpointy manual watering pri DEBUG=true.
- Dobavlena validaciya zaprosov s 422 i obrabotka oshibok dlya sootvetstviya kontraktu.
- Dobavleny integracionnye testy dlya users/devices/manual_watering i debug vetok.
- Obnovlena sborka dlya BOM-istochnikov cherez stripBom pered kompilaciey.
- Dobavleny domeny firmware i history s polnym sovmestimost'yu FastAPI i staticheskoy vydachey firmware.
- Dobavleny integracionnye testy dlya firmware i history, vklyuchaya multipart upload i fil'try istorii.
- Dobavlen domen plants (gruppy, privyazki, zhurnal, export, foto) s integracionnymi testami.










