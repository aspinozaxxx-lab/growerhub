# Домен sensor

## Назначение

Управляет датчиками, их привязками к растениям, текущими значениями, статусами и историей измерений.

## Публичный Facade

`SensorFacade`

- `updateBindings(Integer sensorId, List<Integer> plantIds, AuthenticatedUser user)`
- `getHistory(Integer sensorId, Integer hours, AuthenticatedUser user)`
- `listByDeviceId(Integer deviceId)`
- `listByPlantId(Integer plantId)`
- `listByPlantIdLight(Integer plantId)`
- `deleteByDeviceId(Integer deviceId)`
- `recordMeasurements(String deviceId, List<SensorMeasurement> measurements, LocalDateTime ts)`
- `getPlantIdsBySensorIds(List<Integer> sensorIds)`
- `getOldestHistoryTimestamp()`
- `compactHistoryDay(LocalDateTime fromTs, LocalDateTime toTs)`

## Публичные контракты

- `SensorBoundPlantView`
- `SensorHistoryPoint`
- `SensorMeasurement`
- `SensorReadingSummary`
- `SensorStatus`
- `SensorType`
- `SensorView`

## Владение данными

Домен владеет датчиками, привязками датчиков к растениям и sensor readings. Устройство и растение принадлежат доменам `device` и `plant`.

## Используемые домены

- `device`
- `plant`

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`
- домены `device`, `maintenance`, `plant`

## Алгоритм работы

Facade обновляет привязки, проверяет доступ к датчику, отдаёт списки и историю. При поступлении measurements домен создаёт или обновляет sensor records, сохраняет readings и возвращает summary для записи метрик растения. Выбор точек длинного диапазона выполняется в БД. По запросу maintenance полные старые сутки прореживаются до последней точки каждого датчика в каждом часовом интервале.

## Ограничения

Sensor не владеет устройствами и растениями. Формат sensor status соответствует системному контракту. Сырые данные за период retention не прореживаются. Размер ответа истории ограничивается конфигурацией.
