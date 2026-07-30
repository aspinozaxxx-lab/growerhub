# Домен automation

## Назначение

Хранит пользовательские фермы-помещения и дочерние теплицы, размещение
растений, скорость полива, слоты оборудования, настройки и состояния сценариев
и журнал worker. Формирует цели полива для домена `pump`.

## Публичный Facade

`AutomationFacade`

- `getFarmsOverview(AuthenticatedUser user)`
- `createUserFarm(AuthenticatedUser user, SaveRoomRequest request)`
- `updateUserFarm(AuthenticatedUser user, Integer farmId, SaveRoomRequest request)`
- `deleteUserFarm(AuthenticatedUser user, Integer farmId)`
- `createGreenhouse(AuthenticatedUser user, Integer farmId, SaveBoxRequest request)`
- `updateGreenhouse(AuthenticatedUser user, Integer greenhouseId, SaveGreenhouseRequest request)`
- `deleteGreenhouse(AuthenticatedUser user, Integer greenhouseId)`
- `replaceUserFarmSlots(AuthenticatedUser user, Integer farmId, SaveZoneSlotsRequest request)`
- `replaceUserFarmScenarios(AuthenticatedUser user, Integer farmId, SaveScenariosRequest request)`
- `replaceGreenhouseSlots(AuthenticatedUser user, Integer greenhouseId, SaveZoneSlotsRequest request)`
- `replaceGreenhousePlants(AuthenticatedUser user, Integer greenhouseId, SavePlantsRequest request)`
- `replaceGreenhouseScenarios(AuthenticatedUser user, Integer greenhouseId, SaveScenariosRequest request)`
- `getPlantZones(AuthenticatedUser user, List<Integer> plantIds)`
- `getPlantZone(AuthenticatedUser user, Integer plantId)`
- `createPlantWithPlacement(AuthenticatedUser user, PlantCreateCommand command, Integer zoneId)`
- `updatePlantWithPlacement(AuthenticatedUser user, Integer plantId, PlantUpdateCommand command, boolean zoneProvided, Integer zoneId)`
- `deletePlantWithPlacement(AuthenticatedUser user, Integer plantId)`
- совместимые `getFarmOverview`, `createFarm`, `updateFarm` и `*FarmZone*`
- диагностические `getOverview`, `createRoom`, `updateRoom`, `deleteRoom`,
  `createBox`, `updateBox`, `deleteBox`, `replace*Resources`,
  `replace*Scenarios` и `replaceBoxPlants`
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

Домен владеет `automation_rooms` как фермами, `automation_boxes` как
теплицами, размещением растений со скоростью `rate_ml_per_hour`, resource
bindings, scenario configs, scenario states и action log. Zigbee-привязка
содержит внутренний `coordinator_id`. Устройства, датчики, насосы, растения,
сессии полива и MQTT принадлежат другим доменам.

## Используемые домены

- `device` — каталог native devices и shadow.
- `sensor` — native sensors и последние значения.
- `pump` — запуск, остановка, worker сессий, история и статистика.
- `plant` — каталог растений.
- `zigbee` — Zigbee metadata, state и команды.
- `user` — часовой пояс владельца.

## Внешние пользователи домена

- REST adapter `api`.
- Scheduled worker `automation.engine`.

## Алгоритм работы

Self-service REST публикует `farms → greenhouses` и всегда фильтрует данные по
владельцу. `zone_id` растения означает greenhouse id; изменение растения и
размещения выполняется одной транзакцией. Слот занимает один физический канал,
переназначение требует `reassign=true`. `BOX_CLIMATE` по температуре формирует
запрос охлаждения независимо от кондиционеров и обдува. Локальный кондиционер
теплицы обслуживает запрос сам; иначе `ROOM_CLIMATE` агрегирует запрос на ферме
и использует её кондиционер. При отсутствии кондиционера запрос остаётся в
state для дашборда. Свет и полив используют действующие сценарии. Readiness и
связь вычисляет backend. MQTT-контракты не меняются. Расписание света и
граница календарных суток для суточного лимита полива вычисляются в IANA
timezone владельца фермы. Worker загружает часовые пояса владельцев одним
набором перед обработкой боксов.

## Ограничения

Пользователь может иметь несколько ферм и теплиц; теплица принадлежит одной
ферме. Кондиционер допустим на обоих уровнях, остальные роли — на теплице.
Физический канал занимает один слот. Чужой id выглядит отсутствующим даже для
администратора в обычном кабинете. `WATER_PUMP` принимает native pump,
switch-роли — Zigbee writable state, `LEAK_SENSOR` — readable property.
Frontend не вычисляет readiness. Automation не пишет журнал растений.
