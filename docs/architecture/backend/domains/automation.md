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

Self-service REST публикует `farms → greenhouses` и всегда фильтрует данные по
владельцу. `zone_id` растения означает greenhouse id; размещение меняется
только транзакционной операцией растения, а отдельный endpoint конструктора
обновляет лишь `rate_ml_per_hour`. Слот занимает один физический канал,
переназначение требует `reassign=true`. `BOX_CLIMATE` использует четыре порога:
включение и выключение обдува, создание и снятие запроса охлаждения. Локальный
кондиционер обслуживает запрос сам; иначе `ROOM_CLIMATE` агрегирует запрос на
ферме. Между парными порогами сохраняется текущее состояние гистерезиса;
температурный тренд, задержки выключения и интервалы переключений не участвуют
в решении. При наличии запросов кондиционер включается, а после снятия
последнего запроса немедленно выключается. Runtime сценария содержит
`ac_control` с фазой, желаемым состоянием, числом запросов и временем последней
команды; без кондиционера запрос сохраняется для дашборда. Свет и полив
используют действующие сценарии. Readiness и связь вычисляет backend.
MQTT-контракты не меняются. Расписание света и суточный лимит полива используют
IANA timezone владельца; worker загружает часовые пояса одним набором.
Статистика розеточного ресурса доступна по id привязки: Facade проверяет
владельца и роль, получает его timezone и запрашивает у `zigbee` единый ответ с
графиком мощности или бинарным fallback, временем включения и энергией за семь
локальных календарных дней.
Пользовательский ручной полив строит topology только из принадлежащих владельцу
ферм, теплиц, насосов и растений. Overview, start, stop, список сессий и
статистика теплицы до обращения к `pump` проверяют владельца; чужие идентификаторы
возвращаются как отсутствующие. Диагностические admin-операции сохраняют полный
межпользовательский обзор, но не публикуются в обычном кабинете.

## Ограничения

Пользователь может иметь несколько ферм и теплиц; теплица принадлежит одной
ферме. Кондиционер допустим на обоих уровнях, остальные роли — на теплице.
Физический канал занимает один слот. Чужой id выглядит отсутствующим даже для
администратора в обычном кабинете. `WATER_PUMP` принимает native pump,
switch-роли — Zigbee writable state, `LEAK_SENSOR` — readable property.
Статистика оборудования разрешена только Zigbee-ролям `LIGHT_SWITCH`,
`EXHAUST_SWITCH` и `AC_SWITCH`; admin может читать чужую привязку только через
admin endpoint. Frontend не вычисляет readiness. Automation не пишет журнал
растений.
