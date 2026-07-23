# Connector для существующего Zigbee2MQTT/Home Assistant

Connector не заменяет локальный MQTT и не меняет подключение Home Assistant. Он создаёт отдельный направленный bridge между локальным namespace Zigbee2MQTT и namespace GrowerHub.

Перед запуском:

1. Скопируйте `mosquitto-bridge.conf.example` в `bridge.conf`.
2. Укажите выданные GrowerHub username, password и base topic.
3. Укажите `CHANGE_ME_LOCAL_MQTT_*` для вашего локального broker. При анонимном локальном доступе удалите строки `remote_username` и `remote_password` из секции `local-zigbee2mqtt`. Эти значения остаются только в локальном файле.
4. Ограничьте права файла и запустите `docker compose up -d`.

Два bridge-соединения обмениваются данными только через раздельные внутренние деревья `relay/from-local` и `relay/to-local`. Allowlist направлений исключает петлю: state, availability и служебные ответы идут только наружу; `/set`, `/get` и `bridge/request/*` — только обратно. Перед запуском проверьте итоговый конфиг командой `docker compose run --rm connector mosquitto -c /mosquitto/config/mosquitto.conf -v`.
