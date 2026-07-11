# Домен plant

## Назначение

Управляет растениями, группами, жизненным циклом, сбором урожая и историей метрик растения, включая известный объём полива.

## Публичный Facade

`PlantFacade`

- `listGroups(AuthenticatedUser user)`
- `createGroup(String name, AuthenticatedUser user)`
- `updateGroup(Integer groupId, String name, AuthenticatedUser user)`
- `deleteGroup(Integer groupId, AuthenticatedUser user)`
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

## Публичные контракты

- `AdminPlantInfo`
- `PlantGroupInfo`
- `PlantInfo`
- `PlantMetricBucketPoint`
- `PlantMetricPoint`
- `PlantMetricType`

## Владение данными

Домен владеет растениями, группами и samples метрик растения. Скорость полива в конкретном боксе принадлежит automation; журнал и pump sessions принадлежат своим доменам.

## Используемые домены

- `journal`.
- `sensor`.
- `user`.

## Внешние пользователи домена

- REST adapter `api`.
- домены `advisor`, `device`, `journal`, `pump`, `sensor`.

## Алгоритм работы

Facade выполняет CRUD групп и растений, проверяет владение, отдаёт admin views и историю. Метрики создаются по показаниям датчиков и по событиям полива только с известным объёмом. При сборе урожая растение обновляется и создаётся запись журнала.

## Ограничения

Plant не владеет журналом, насосами, датчиками, скоростью полива и пользователями. Неизвестный объём сессии не преобразуется в ноль и не создаёт WATERING_VOLUME sample. История ограничивается настройками конфигурации. Composite-сценарии с журналом остаются внутри Facade.
