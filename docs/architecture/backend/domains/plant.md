# Домен plant

## Назначение

Управляет растениями, жизненным циклом, сбором урожая и историей метрик растения, включая известный объём полива.

## Публичный Facade

`PlantFacade`

- `listPlants(AuthenticatedUser user)`
- `createPlant(PlantCreateCommand command, AuthenticatedUser user)`
- `getPlant(Integer plantId, AuthenticatedUser user)`
- `updatePlant(Integer plantId, PlantUpdateCommand command, AuthenticatedUser user)`
- `validatePlantCreate`, `validatePlantUpdate` — проверка карточки без записи.
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

Домен владеет растениями (включая описание, текстовое место и неизвестную дату посадки) и samples метрик растения. Размещение растения в теплице (`automation_box`) и скорость полива принадлежат automation; журнал и pump sessions принадлежат своим доменам.

## Используемые домены

- `journal`.
- `sensor`.
- `user`.

## Внешние пользователи домена

- REST adapter `api`.
- домены `advisor`, `automation`, `device`, `journal`, `maintenance`, `pump`, `sensor`.

## Алгоритм работы

Facade выполняет CRUD с проверкой владельца; создание и изменение берут общий lock аккаунта. Preview использует ту же проверку метаданных и пределов демо без записи. `dateUnknown` оставляет посадку неизвестной; старый вызов без него сохраняет прежнюю семантику. Оборудование необязательно. `AutomationFacade` в своей транзакции сохраняет карточку и размещение. Метрики создаются по датчикам и по поливу только с известным объёмом. Точки и временные bucket выбираются в БД. Maintenance прореживает старые непрерывные метрики до одной точки в час; `WATERING_VOLUME_L` сохраняются полностью. Сбор урожая меняет растение и создаёт запись журнала.

## Ограничения

Plant не владеет журналом, насосами, датчиками, размещением, скоростью полива и пользователями. Групп растений в домене и REST-контракте нет. Неизвестный объём сессии не преобразуется в ноль и не создаёт WATERING_VOLUME sample. Сырые непрерывные метрики за период retention не прореживаются. Composite-сценарии с журналом остаются внутри Facade.
