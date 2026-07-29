# Домен automation

## Назначение

Хранит одну принадлежащую пользователю ферму, её теплицы-зоны, размещение растений и скорость полива, слоты ресурсов, настройки сценариев, runtime state и диагностический журнал worker. Внутри каждой зоны сохраняется один скрытый box, необходимый действующему worker. Домен формирует цели полива и текущее состояние датчиков для домена `pump`.

## Публичный Facade

`AutomationFacade`

- `getFarmOverview(AuthenticatedUser user)`
- `createFarm(AuthenticatedUser user, SaveFarmRequest request)`
- `updateFarm(AuthenticatedUser user, SaveFarmRequest request)`
- `createFarmZone(AuthenticatedUser user, SaveRoomRequest request)`
- `updateFarmZone(AuthenticatedUser user, Integer zoneId, SaveRoomRequest request)`
- `deleteFarmZone(AuthenticatedUser user, Integer zoneId)`
- `replaceFarmZoneSlots(AuthenticatedUser user, Integer zoneId, SaveZoneSlotsRequest request)`
- `replaceFarmZonePlants(AuthenticatedUser user, Integer zoneId, SavePlantsRequest request)`
- `replaceFarmZoneScenarios(AuthenticatedUser user, Integer zoneId, SaveScenariosRequest request)`
- `getPlantZones(AuthenticatedUser user, List<Integer> plantIds)`
- `getPlantZone(AuthenticatedUser user, Integer plantId)`
- `createPlantWithPlacement(AuthenticatedUser user, PlantCreateCommand command, Integer zoneId)`
- `updatePlantWithPlacement(AuthenticatedUser user, Integer plantId, PlantUpdateCommand command, boolean zoneProvided, Integer zoneId)`
- `deletePlantWithPlacement(AuthenticatedUser user, Integer plantId)`
- `getOverview(AuthenticatedUser user)`
- `createRoom(AuthenticatedUser user, SaveRoomRequest request)`
- `updateRoom(AuthenticatedUser user, Integer roomId, SaveRoomRequest request)`
- `deleteRoom(AuthenticatedUser user, Integer roomId)`
- `createBox(AuthenticatedUser user, Integer roomId, SaveBoxRequest request)`
- `updateBox(AuthenticatedUser user, Integer boxId, SaveBoxRequest request)`
- `deleteBox(AuthenticatedUser user, Integer boxId)`
- `replaceBoxPlants(AuthenticatedUser user, Integer boxId, SavePlantsRequest request)`
- `replaceRoomResources(AuthenticatedUser user, Integer roomId, SaveResourcesRequest request)`
- `replaceBoxResources(AuthenticatedUser user, Integer boxId, SaveResourcesRequest request)`
- `replaceRoomScenarios(AuthenticatedUser user, Integer roomId, SaveScenariosRequest request)`
- `replaceBoxScenarios(AuthenticatedUser user, Integer boxId, SaveScenariosRequest request)`
- `getManualWateringOverview()`
- `startManualWatering(Integer pumpId, ManualWateringStartRequest request, AuthenticatedUser user)`
- `startUserManualWatering(Integer pumpId, UserManualWateringStartRequest request, AuthenticatedUser user)`
- `stopManualWatering(Integer pumpId, AuthenticatedUser user)`
- `getManualWateringSessions(Integer pumpId, int limit, Long beforeId)`
- `getManualWateringBoxStatistics(Integer boxId, String range, int limit, Long beforeId)`
- `evaluateAll()`
- `evaluateActiveWateringSessions()`

## Публичные контракты

- `AutomationData`

## Владение данными

Домен владеет `automation_farms`, rooms с обязательными `user_id` и `farm_id`, единственным box каждой зоны, box plants со скоростью `rate_ml_per_hour`, resource bindings, scenario configs, scenario states и action log. Zigbee-привязка содержит внутренний `coordinator_id`. Иерархия automation является источником размещения растений и целей для настроенного насоса. Устройства, датчики, насосы, растения, сессии полива и MQTT принадлежат другим доменам.

## Используемые домены

- `device` — каталог native devices и shadow.
- `sensor` — native sensors и последние значения.
- `pump` — запуск, остановка, worker сессий, история и статистика.
- `plant` — каталог растений.
- `zigbee` — Zigbee metadata, state и команды.

## Внешние пользователи домена

- REST adapter `api`.
- Scheduled worker `automation.engine`.

## Алгоритм работы

Пользовательский REST публикует только проекцию `farm → zones`; чужой идентификатор зоны или Zigbee-ресурса выглядит как отсутствующий. Создание и изменение растения вместе с nullable `zone_id` выполняется одной транзакцией. Удаление зоны оставляет растения без размещения и сохраняет данные растения. Слот принимает только один физический канал, а свойство комбинированного Zigbee-датчика считается отдельным каналом; занятый канал переносится только при `reassign=true`. Кондиционер хранится на room, остальные семь слотов — на скрытом box. Климат зоны атомарно сохраняет `ROOM_CLIMATE` и `BOX_CLIMATE`; свет и полив используют действующие сценарии. Readiness и причины недоступности вычисляет backend. Статус связи выводится из source state и `last_seen_at`, без отдельного хранения. Обзор ручного полива использует внутреннюю модель `насос → boxes → растения и датчики`. Worker, MQTT namespace и сценарная логика не меняются.

## Ограничения

У пользователя не более одной фермы, у зоны ровно один внутренний box, физический канал не может занимать два слота. Публичный пользовательский API не раскрывает box и не содержит старых room/section endpoints; admin API сохраняет внутреннюю модель для диагностики. Привязки редактируются только в automation. `WATER_PUMP` принимает native pump, switch-роли — Zigbee writable state, `LEAK_SENSOR` — Zigbee readable property. Leak срабатывает на `true`, `ON` или `value_on`; доступным считается только при Zigbee2MQTT availability `online`. `WATERING.stop_mode` поддерживает `fixed_duration` и `until_drain`, pulse задаётся run/pause в минутах; паузы не входят в лимит. Для `until_leak` достаточно одного доступного leak-сенсора всех боксов насоса. Frontend не вычисляет readiness или eligibility. Automation не исполняет фазы и не пишет журнал растений.
