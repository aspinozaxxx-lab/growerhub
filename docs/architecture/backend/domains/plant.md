# Домен plant

## Назначение

Управляет растениями, группами, жизненным циклом, сбором урожая и историей метрик растения.

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
- `recordWateringEvent(Integer plantId, double volumeL, LocalDateTime eventAt)`

## Публичные контракты

- `AdminPlantInfo`
- `PlantGroupInfo`
- `PlantInfo`
- `PlantMetricBucketPoint`
- `PlantMetricPoint`
- `PlantMetricType`

## Владение данными

Домен владеет растениями, группами и samples метрик растения. Журнал, датчики, насосы и пользователи остаются в своих доменах и используются через публичные Facade или contracts.

## Используемые домены

- `journal`
- `sensor`
- `user`

## Внешние пользователи домена

- REST adapter `api`
- домены `advisor`, `device`, `journal`, `pump`, `sensor`

## Алгоритм работы

Facade выполняет CRUD групп и растений, проверяет владение, отдает admin views и историю. Метрики пишутся по показаниям датчиков и событиям полива. При сборе урожая растение обновляется и создается запись журнала.

## Ограничения

Plant не владеет журналом, насосами, датчиками и пользователями. История должна ограничиваться настройками конфигурации. Composite-сценарии с журналом должны оставаться внутри Facade.
