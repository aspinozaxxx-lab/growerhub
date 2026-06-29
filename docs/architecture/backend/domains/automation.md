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

Admin REST сохраняет помещения, боксы, растения, resource bindings и scenario configs. Frontend работает только через REST. Worker каждые 30 секунд пропускает disabled/unready/stale сценарии, оценивает climate/light/watering и пишет action log. Исполнительные команды уходят через существующие фасады: Zigbee switch commands публикуются в `zigbee2growerhub/<friendly_name>/set`, native watering идет через `PumpFacade`.

## Ограничения

Все сценарии по умолчанию выключены и начинают отправлять команды только после явного включения. `LIGHT_SWITCH` в v1 принимает только Zigbee-устройство с writable `state`; native light relay не используется. `WATER_PUMP` в v1 принимает только native pump, чтобы сохранить существующий MQTT/journal/safety путь. Сложная manual override-интеграция с внешними ручными командами не является источником истины v1.
