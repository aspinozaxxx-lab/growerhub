# Домен automation

## Назначение

Хранит иерархию помещений и боксов, растения и их скорость полива в боксе, привязки ресурсов, настройки сценариев, runtime state и диагностический журнал worker. Формирует цели полива и текущее состояние датчиков для домена `pump`.

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

Домен владеет rooms, boxes, box plants со скоростью `rate_ml_per_hour`, resource bindings, scenario configs, scenario states и action log. Иерархия automation является источником целей для настроенного насоса. Устройства, датчики, насосы, растения, сессии полива и MQTT принадлежат другим доменам.

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

Admin REST сохраняет иерархию и синхронизирует compatibility-проекцию насоса. `SavePlantsRequest.items` задаёт растение и nullable скорость; legacy `plant_ids` принимается без скорости. Роли бокса: air/soil/leak sensors, light/exhaust switches и pump; помещения — AC switch. Статус связи выводится из source state и `last_seen_at`, без отдельного хранения; air/soil используют отдельный порог карточки. Обзор ручного полива группирует `насос → все боксы → растения и датчики`, вычисляет eligibility и берёт defaults и сессии из `PumpFacade`. Start передаёт immutable snapshot всей иерархии, включая выключенные боксы. Worker раз в секунду передаёт session engine статусы насоса и leak-сенсоров. Автополив использует тот же engine; интервалы и дневной лимит читает из всех завершённых сессий бокса. Action log остаётся диагностикой.

## Ограничения

Привязки редактируются только в automation. `WATER_PUMP` принимает native pump, switch-роли — Zigbee writable state, `LEAK_SENSOR` — Zigbee readable property. Leak срабатывает на `true`, `ON` или `value_on`; доступным считается только при Zigbee2MQTT availability `online`. `WATERING.stop_mode` поддерживает `fixed_duration` и `until_drain`, pulse задаётся run/pause в минутах; паузы не входят в лимит. Для `until_leak` достаточно одного доступного leak-сенсора всех боксов насоса. Frontend не вычисляет eligibility. Automation не исполняет фазы и не пишет журнал растений.
