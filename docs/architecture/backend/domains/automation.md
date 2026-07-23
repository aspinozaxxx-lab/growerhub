# Домен automation

## Назначение

Хранит принадлежащую пользователю иерархию зон и секций, дополнительные растения и их скорость полива, привязки ресурсов, настройки сценариев, runtime state и диагностический журнал worker. Формирует цели полива и текущее состояние датчиков для домена `pump`.

## Публичный Facade

`AutomationFacade`

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

Домен владеет rooms с обязательным `user_id`, boxes, box plants со скоростью `rate_ml_per_hour`, resource bindings, scenario configs, scenario states и action log. Zigbee-привязка содержит внутренний `coordinator_id`. Иерархия automation является источником целей для настроенного насоса. Устройства, датчики, насосы, растения, сессии полива и MQTT принадлежат другим доменам.

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

Пользовательский REST сохраняет только собственную иерархию; чужой идентификатор зоны, секции или Zigbee-ресурса выглядит как отсутствующий. `SavePlantsRequest.items` задаёт необязательное растение и nullable скорость; legacy `plant_ids` принимается без скорости. Роли секции: air/soil/leak sensors, light/exhaust switches и pump; зоны — AC switch. Статус связи выводится из source state и `last_seen_at`, без отдельного хранения. Обзор ручного полива группирует `насос → все секции → растения и датчики`, вычисляет eligibility и берёт defaults и сессии из `PumpFacade`. Worker раз в секунду передаёт session engine статусы насоса и leak-сенсоров. Автополив использует тот же engine; интервалы и дневной лимит читает из всех завершённых сессий секции. Action log остаётся диагностикой.

## Ограничения

Привязки редактируются только в automation. `WATER_PUMP` принимает native pump, switch-роли — Zigbee writable state, `LEAK_SENSOR` — Zigbee readable property. Leak срабатывает на `true`, `ON` или `value_on`; доступным считается только при Zigbee2MQTT availability `online`. `WATERING.stop_mode` поддерживает `fixed_duration` и `until_drain`, pulse задаётся run/pause в минутах; паузы не входят в лимит. Для `until_leak` достаточно одного доступного leak-сенсора всех боксов насоса. Frontend не вычисляет eligibility. Automation не исполняет фазы и не пишет журнал растений.
