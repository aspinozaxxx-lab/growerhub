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
- `getResourceSensorHistory(user, resourceId, hours)` — история собственного native/Zigbee-датчика с периодом и единицами.
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

- `ruleSchema`, `validateRuleWatering`, `startRuleWatering`, `ruleWateringExecution`, `stopRuleWatering` — единая схема и устойчивый жизненный цикл полива пользовательского правила.
- `listRules`, `getRule`, `previewRule`, `saveRule`, `toggleRule`, `restoreRule`, `ruleVersions`, `ruleHistory` — декларативные правила ADR-013.
- `ruleFingerprint`, `scenarioFingerprint`, `previewScenario`, `previewDeviceCommand`, `applyDeviceCommand` — проверка подтверждаемых изменений.
- `ruleOwnersDue`, `evaluateRules`, `claimRuleStep`, `executeRuleStep`, `finishRuleStep` — устойчивые запуски без ИИ.
- `listControls`, `getControl`, `setManualControl`, `releaseManualControl` — собственные требования, ACK и ручной срок.
- `controlOwnersDue`, `reconcileControls`, `claimControlCommand`, `executeControlCommand`, `finishControlCommand`, `maintainControls` — арбитр, устойчивая публикация и ограниченная очистка.
- `setManualZigbeeProperty`, `setLegacyManualZigbeeProperty` — общий ручной путь пользовательского и административного адаптеров.

## Публичные контракты

- `AutomationData`, `RuleData`, `ControlData`
- `WateringPlanData`: время, доза, ключ, причины, использованные показания и отклик; ADR-010.
- `WeatherForecastData`, `WeatherForecastGateway`: исходные интервалы прогноза, модель, источник и устойчивое ожидание.
- `PUT /api/automation/scenarios/enabled` принимает обязательный boolean
  `enabled` и возвращает `FarmsOverview`. Одна транзакция выключает все сценарии
  владельца либо включает готовые сценарии активных теплиц и ферм. Существующий
  `config_json` сохраняется; климат фермы синхронизируется с дочерними сценариями.
  Неготовые области пропускаются при включении, ошибки проверки конфигурации
  откатывают всю операцию. Администратор в этом endpoint также ограничен своими фермами.

## Владение данными

Домен владеет фермами automation_rooms, теплицами automation_boxes, размещением растений и rate_ml_per_hour, ресурсными привязками с coordinator_id, настройками, состояниями сценариев и action log. automation_rules, версии и запуски хранят определения, ключи, шаги, паузы и lease. automation_control_demands/channels хранят требования физического канала, победителя, отпечатки, сроки и ключ публикации. Устройства, датчики, насосы, растения, сессии полива и MQTT принадлежат другим доменам.

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

REST строит свои farms → greenhouses. Климат хранит гистерезис независимо от вмешательства; свет учитывает timezone. WATERING сохраняет старые режимы, новые используют расписание или историю местного датчика; прогноз влияет только явно (ADR-010). Pump обеспечивает дозу, импульсы и остановку. Ключ восстанавливает сеанс без повторного старта. Для switch сценарии, правила и ручной срок формируют требования: больший приоритет, затем неизменный порядок создания/ключ. V3 hold ждёт живой ACK нужного свойства; срок не продлевается. Repeat_while хранит период и поколение, пропуски не догоняет. Потеря/устаревание условия снимает своё требование и переоценивает остальные без навязанного OFF. Claim фиксирует ключ до публикации; неизвестный результат только сверяется. Первое подтверждение отправленной команды пополняет историю готового сценария; поллинг не дублирует запись. Preview объясняет конкуренцию. Демо использует общий алгоритм (ADR-013).

## Ограничения

Физический канал занимает один слот. Владение проверяется и для admin. WATER_PUMP принимает native pump или допущенный Zigbee-клапан; switch не обходит защиты полива. Свет/климат: Zigbee LIGHT_SWITCH, EXHAUST_SWITCH, AC_SWITCH. Frontend не вычисляет readiness, automation не пишет чужие таблицы. V1/v2 и прямые set/water сохраняют эксклюзивность; совместное удержание — явно v3. Ручной TTL ограничен; повтор requestId не продлевает срок, release проверяет id/ревизию. Retained и чужая телеметрия не подтверждают действие. Прерванное удержание не считается полным успехом. Неизвестные команды не повторяются (ADR-013).
