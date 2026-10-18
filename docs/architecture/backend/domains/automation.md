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
- `getWateringPlan(user, greenhouseId)` — предварительное решение только собственной теплицы.
- `updateFarmWeatherLocation(user, farmId, location)` — округлённое необязательное место собственной фермы.

- `listRules`, `getRule`, `previewRule`, `saveRule`, `toggleRule`, `restoreRule`, `ruleVersions`, `ruleHistory` — декларативные правила ADR-013.
- `ruleFingerprint`, `scenarioFingerprint`, `previewScenario`, `previewDeviceCommand`, `applyDeviceCommand` — проверка подтверждаемых изменений.
- `ruleOwnersDue`, `evaluateRules`, `claimRuleStep`, `executeRuleStep`, `finishRuleStep` — устойчивые запуски без ИИ.

## Публичные контракты

- `AutomationData`, `RuleData`
- `WateringPlanData`: время, доза, ключ, причины, использованные показания и отклик; ADR-010.
- `WeatherForecastData`, `WeatherForecastGateway`: исходные интервалы прогноза, модель, источник и устойчивое ожидание.
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
сессии полива и MQTT принадлежат другим доменам. `automation_rules`, версии и запуски хранят декларативные правила, ключи запуска, паузы, lease и результаты.

## Используемые домены

- `device` — каталог native devices и shadow.
- `sensor` — native sensors и последние значения.
- `pump` — запуск, остановка, worker сессий, история и статистика.
- `plant` — каталог растений.
- `zigbee` — Zigbee metadata, state и команды.
- `user` — часовой пояс и блокировка владельца.
- `notification` — постановка разрешённого уведомления в очередь.
- `demo` — моделируемый прогноз через Facade, без внешнего HTTP.

## Внешние пользователи домена

- REST adapter `api`.
- Scheduled worker `automation.engine`.

## Алгоритм работы

REST строит собственные farms → greenhouses; канал занимает один слот. Климат использует гистерезис, свет — timezone. WATERING без trigger_mode сохраняется. Новые режимы: расписание, местный ориентир и прогноз высыхания с фиксированной дозой. Наблюдение не отправляет команд. Динамика требует ориентиров текущего датчика, свежей устойчивой истории и отклика на обычный полив. Worker проверяет оборудование, интервал и лимит суток исполнения. Погода явная: под крышей не влияет; снаружи свежий прогноз переносит слот до неизменного срока. При сбое выбранное правило ждёт либо продолжает основной сценарий. Отмена дождя сохраняет ключ и разрешённые часы; истёкший слот не догоняется. Runtime хранит план, исходный срок и отпечаток конфигурации. Смена настроек, места или канала отменяет ожидание. DST gap пропускается, overlap имеет один ключ. Pump сохраняет ключ до команды. Обычный кабинет исключает чужие id также для admin; физические workers исключают DEMO (ADR-006/007/010).

## Ограничения

Теплица принадлежит одной ферме; физический канал занимает один слот. Обычный кабинет исключает чужие id также для admin. WATER_PUMP принимает native pump либо допущенный Zigbee-клапан; switch-роли — writable state без известных поливных каналов; LEAK_SENSOR — readable property. Статистика доступна Zigbee LIGHT_SWITCH, EXHAUST_SWITCH и AC_SWITCH. Frontend не вычисляет readiness, automation не пишет журнал растений. Правила v1 ограничены декларативным JSON: конфликт каналов блокирует включение, полив ограничен независимо от ИИ, неизвестная отправка не повторяется; детали — ADR-013.
