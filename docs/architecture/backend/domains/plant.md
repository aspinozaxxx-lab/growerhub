# Домен plant

## Назначение

Управляет растениями, жизненным циклом, сбором урожая и историей метрик растения, включая известный объём полива.

## Публичный Facade

`PlantFacade`

- `listPlants(AuthenticatedUser user)`
- `createPlant(PlantCreateCommand command, AuthenticatedUser user)`
- `getPlant(Integer plantId, AuthenticatedUser user)`
- `updatePlant(Integer plantId, PlantUpdateCommand command, AuthenticatedUser user)`
- `deletePlant(Integer plantId, AuthenticatedUser user)`
- `harvestPlant(Integer plantId, PlantHarvestCommand command, AuthenticatedUser user)`
- `listAdminPlants(AuthenticatedUser user)`
- `getHistory(Integer plantId, Integer hours, String metrics, AuthenticatedUser user)`
- `getBucketedHistory(Integer plantId, AuthenticatedUser user, List<PlantMetricType> metricTypes, LocalDateTime since, Duration bucketDuration)`
- `requireOwnedPlantInfo(Integer plantId, AuthenticatedUser user)`
- `getPlantInfoById(Integer plantId)`
- `recordFromSensorBindings(List<SensorReadingSummary> summaries)`
- `recordWateringEvent(Integer plantId, Double volumeL, LocalDateTime eventAt)`
- `getOldestHistoryTimestamp()`
- `compactHistoryDay(LocalDateTime fromTs, LocalDateTime toTs)`

## Публичные контракты

- `AdminPlantInfo`
- `PlantInfo`
- `PlantMetricBucketPoint`
- `PlantMetricPoint`
- `PlantMetricType`

## Владение данными

Домен владеет растениями и samples метрик растения. Размещение растения в теплице (`automation_box`) и скорость полива принадлежат automation; журнал и pump sessions принадлежат своим доменам.

## Используемые домены

- `journal`.
- `sensor`.
- `user`.

## Внешние пользователи домена

- REST adapter `api`.
- домены `advisor`, `automation`, `device`, `journal`, `maintenance`, `pump`, `sensor`.

## Алгоритм работы

Facade выполняет CRUD растений, проверяет владение, отдаёт admin views и историю. `AutomationFacade` вызывает публичные операции растения и в своей транзакции одновременно сохраняет размещение. Метрики создаются по показаниям датчиков и по событиям полива только с известным объёмом. Выбор точек и временных bucket выполняется в БД. По запросу maintenance старые непрерывные метрики прореживаются до одной точки в час; события `WATERING_VOLUME_L` сохраняются полностью. При сборе урожая растение обновляется и создаётся запись журнала.

## Ограничения

Plant не владеет журналом, насосами, датчиками, размещением, скоростью полива и пользователями. Групп растений в домене и REST-контракте нет. Неизвестный объём сессии не преобразуется в ноль и не создаёт WATERING_VOLUME sample. Сырые непрерывные метрики за период retention не прореживаются. Composite-сценарии с журналом остаются внутри Facade.
