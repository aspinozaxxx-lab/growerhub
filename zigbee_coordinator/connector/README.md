# Локальный мост для существующего Zigbee2MQTT/Home Assistant

Локальный мост не заменяет MQTT-брокер и не меняет подключение Home Assistant. Он создаёт отдельное направленное соединение между деревьями тем Zigbee2MQTT и GrowerHub.

Перед запуском:

1. Скопируйте `mosquitto-bridge.conf.example` в `bridge.conf`.
2. Укажите выданные GrowerHub имя пользователя, пароль и базовую тему.
3. Укажите `CHANGE_ME_LOCAL_MQTT_*` для вашего локального MQTT-брокера. При анонимном локальном доступе удалите строки `remote_username` и `remote_password` из секции `local-zigbee2mqtt`. Эти значения остаются только в локальном файле.
4. Ограничьте права файла и запустите `docker compose up -d`.

Два соединения обмениваются данными только через раздельные внутренние деревья `relay/from-local` и `relay/to-local`. Список разрешённых направлений исключает петлю: состояние, доступность и служебные ответы идут только наружу; `/set`, `/get` и `bridge/request/*` — только обратно. Перед запуском проверьте итоговую конфигурацию командой `docker compose run --rm connector mosquitto -c /mosquitto/config/mosquitto.conf -v`.
