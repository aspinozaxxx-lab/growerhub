# Домен automation

## Назначение

Хранит модель автоматизации помещений и боксов: помещения, боксы, растения в боксах, привязки ресурсов, настройки сценариев, runtime state и журнал действий worker.

## Публичный Facade

`AutomationFacade`

- `getOverview()`
- `createRoom(SaveRoomRequest request)`
- `updateRoom(Integer roomId, SaveRoomRequest request)`
- `deleteRoom(Integer roomId)`
- `createBox(Integer roomId, SaveBoxRequest request)`
- `updateBox(Integer boxId, SaveBoxRequest request)`
- `deleteBox(Integer boxId)`
- `replaceBoxPlants(Integer boxId, SavePlantsRequest request)`
- `replaceRoomResources(Integer roomId, SaveResourcesRequest request)`
- `replaceBoxResources(Integer boxId, SaveResourcesRequest request)`
- `replaceRoomScenarios(Integer roomId, SaveScenariosRequest request)`
- `replaceBoxScenarios(Integer boxId, SaveScenariosRequest request)`
- `evaluateAll()`
- `evaluateActiveWateringSessions()`

## Публичные контракты

- `AutomationData`

## Владение данными

Домен владеет только конфигурацией и состоянием автоматизации: rooms, boxes, box plants, resource bindings, scenario configs, scenario states и action log. Он не владеет устройствами, Zigbee snapshots, датчиками, насосами, растениями и MQTT-сообщениями.

## Используемые домены

- `device` - каталог native devices и shadow.
- `sensor` - native sensors и последние значения.
- `pump` - native pump command path.
- `plant` - каталог растений.
- `zigbee` - Zigbee metadata, state и команды через Zigbee2MQTT.

## Внешние пользователи домена

- REST adapter `api`.
- Scheduled worker `automation.engine`.

## Алгоритм работы

Admin REST сохраняет помещения, боксы, растения, resource bindings и scenario configs. Frontend работает только через REST. Worker каждые 30 секунд пропускает disabled/unready/stale сценарии, оценивает climate/light/watering и пишет action log. Отдельный частый worker оценивает только активные watering-сессии из `automation_scenario_states.runtime_json`.

Ресурсы automation хранятся в `automation_resource_bindings`. Для бокса доступны `AIR_TEMPERATURE_SENSOR`, `SOIL_MOISTURE_SENSOR`, `LEAK_SENSOR`, `LIGHT_SWITCH`, `EXHAUST_SWITCH`, `WATER_PUMP`; для помещения доступен `AC_SWITCH`. `LEAK_SENSOR` принимает Zigbee device с readable property, по умолчанию `water_leak`. Сработка протечки определяется значением `true`, строкой `"true"`, `value_on` или `ON`.

`WATERING.config` поддерживает `stop_mode=fixed_duration` и `stop_mode=until_drain`. В `fixed_duration` используется `run_seconds`. В `until_drain` старт происходит по тем же условиям влажности и интервалов, но остановка выполняется по сработке `LEAK_SENSOR` или по обязательному лимиту `max_run_minutes`. Сценарий `until_drain` нельзя сохранить без валидного `LEAK_SENSOR`. Импульсный режим задается в минутах через `pulse_enabled`, `pulse_run_minutes`, `pulse_pause_minutes`; лимит `max_run_minutes` считает только фактическое время работы насоса, паузы не учитываются. При сработке протечки в любой фазе сессия сразу отправляет `PumpFacade.stop`, очищает runtime и пишет action log с причиной `drenazh`; при достижении лимита причина `limit`.

Исполнительные команды уходят через существующие фасады: Zigbee switch commands публикуются в `zigbee2growerhub/<friendly_name>/set`, native watering идет через `PumpFacade`.

Для ресурсов в API отдается производный статус связи. Если `last_seen_at` отсутствует или старше `automation.resourceOfflineMinutes`, response содержит `connection_status=warning` и `connection_message="нет связи"`. Для native sensor `last_seen_at` берется от родительского устройства, а `last_ts` остается временем последнего значения; статус `DISCONNECTED`/`ERROR` также дает warning. Это только представление для UI, отдельного хранения статуса связи в automation нет.

## Ограничения

Все сценарии по умолчанию выключены и начинают отправлять команды только после явного включения. `LIGHT_SWITCH` в v1 принимает только Zigbee-устройство с writable `state`; native light relay не используется. `WATER_PUMP` в v1 принимает только native pump, чтобы сохранить существующий MQTT/journal/safety путь. `LEAK_SENSOR` в v1 принимает только Zigbee-устройство, потому что текущий native sensor contract не содержит отдельный тип протечки. Сложная manual override-интеграция с внешними ручными командами не является источником истины v1.
