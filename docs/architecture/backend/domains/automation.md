# Домен automation

## Назначение

Хранит пользовательские фермы-помещения и дочерние теплицы, размещение
растений, скорость полива, слоты оборудования, настройки и состояния сценариев
и журнал worker. Формирует цели полива для домена `pump`.

## Публичный Facade

`AutomationFacade`

- `getFarmsOverview(AuthenticatedUser user)`
- `setUserScenariosEnabled(AuthenticatedUser user, SetScenariosEnabledRequest request)`
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
- `updateGreenhousePlantWateringRate(AuthenticatedUser user, Integer greenhouseId, Integer plantId, UpdateWateringRateRequest request)`
- `replaceGreenhouseScenarios(AuthenticatedUser user, Integer greenhouseId, SaveScenariosRequest request)`
- `getResourceStatistics(AuthenticatedUser user, Integer resourceId, Integer hours)`
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
- `getManualWateringOverview(AuthenticatedUser user)`
- `startManualWatering(Integer pumpId, ManualWateringStartRequest request, AuthenticatedUser user)`
- `startUserManualWatering(Integer pumpId, UserManualWateringStartRequest request, AuthenticatedUser user)`
- `startUserManualWateringSession(Integer pumpId, ManualWateringStartRequest request, AuthenticatedUser user)`
- `startResourceWatering`, `stopResourceWatering`, `resourceWateringSessions` — Zigbee-полив по id привязки с проверкой владельца.
- `stopManualWatering(Integer pumpId, AuthenticatedUser user)`
- `stopUserManualWatering(Integer pumpId, AuthenticatedUser user)`
- `getManualWateringSessions(Integer pumpId, int limit, Long beforeId)`
- `getUserManualWateringSessions(Integer pumpId, int limit, Long beforeId, AuthenticatedUser user)`
- `getManualWateringBoxStatistics(Integer boxId, String range, int limit, Long beforeId)`
- `getUserManualWateringBoxStatistics(Integer boxId, String range, int limit, Long beforeId, AuthenticatedUser user)`
- `evaluateAll()`
- `evaluateActiveWateringSessions()`

## Публичные контракты

- `AutomationData`
- `PUT /api/automation/scenarios/enabled` принимает обязательный boolean
  `enabled` и возвращает `FarmsOverview`. Одна транзакция выключает все сценарии
  владельца либо включает готовые сценарии активных теплиц и ферм. Существующий
  `config_json` сохраняется; климат фермы синхронизируется с дочерними сценариями.
  Неготовые области пропускаются при включении, ошибки проверки конфигурации
  откатывают всю операцию. Администратор в этом endpoint также ограничен своими фермами.

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

REST строит farms → greenhouses по владельцу; zone_id растения означает теплицу. Канал занимает один слот, перенос требует reassign. BOX_CLIMATE использует четыре порога гистерезиса; локальный кондиционер обслуживает запрос сам, иначе его агрегирует ROOM_CLIMATE. Runtime ac_control показывает запрос и состояние. Свет и дневной лимит полива используют timezone владельца. Readiness и статистика ресурсов вычисляются backend через Facade. Ручной полив строит topology собственных растений и передаёт снимок в pump. WATER_PUMP допускает native либо проверенный Zigbee-исполнитель (ADR-007); start блокирует привязку, переназначение ждёт завершения. Zigbee адресуется id привязки, сессия хранит неизменяемый канал. Обычный кабинет, включая admin, отвергает чужие id. Физические workers исключают DEMO, demo вызывает те же расчёты отдельно по владельцу; режим ресурса и владельца обязан совпадать (ADR-006).

## Ограничения

Пользователь может иметь несколько ферм и теплиц; теплица принадлежит одной
ферме. Кондиционер допустим на обоих уровнях, остальные роли — на теплице.
Физический канал занимает один слот. Чужой id выглядит отсутствующим даже для
администратора в обычном кабинете. `WATER_PUMP` принимает native pump либо допущенный Zigbee-клапан,
switch-роли — Zigbee writable state, `LEAK_SENSOR` — readable property.
Статистика оборудования разрешена только Zigbee-ролям `LIGHT_SWITCH`,
`EXHAUST_SWITCH` и `AC_SWITCH`; admin может читать чужую привязку только через
admin endpoint. Frontend не вычисляет readiness. Automation не пишет журнал
растений.
