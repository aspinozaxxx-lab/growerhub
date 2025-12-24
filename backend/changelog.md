# Changelog

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
