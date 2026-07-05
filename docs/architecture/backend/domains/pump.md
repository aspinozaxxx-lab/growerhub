# Домен pump

## Назначение

Управляет насосами, привязками к растениям, ручным поливом, статусом полива, историей фактического состояния насоса и командами stop/reboot.

## Публичный Facade

`PumpFacade`

- `updateBindings(Integer pumpId, List<PumpBindingItem> items, AuthenticatedUser user)`
- `start(Integer pumpId, PumpWateringRequest request, AuthenticatedUser user)`
- `stop(Integer pumpId, AuthenticatedUser user)`
- `reboot(Integer pumpId, AuthenticatedUser user)`
- `status(Integer pumpId, AuthenticatedUser user)`
- `getHistory(Integer pumpId, Integer hours, AuthenticatedUser user)`
- `recordStateByDeviceId(Integer devicePk, DeviceShadowState state, LocalDateTime now)`
- `getAck(String correlationId)`
- `finalizeWateringByDeviceId(String deviceId, LocalDateTime now)`
- `listByDeviceId(Integer deviceId, DeviceShadowState state)`
- `listByPlantId(Integer plantId)`
- `listByPlantIdLight(Integer plantId)`
- `ensureDefaultPump(Integer deviceId)`
- `deleteByDeviceId(Integer deviceId)`

## Публичные контракты

- `PumpAck`
- `PumpBoundPlantView`
- `PumpCommandGateway`
- `PumpHistoryPoint`
- `PumpRebootResult`
- `PumpRunningStatusProvider`
- `PumpStartResult`
- `PumpStatusResult`
- `PumpStopResult`
- `PumpView`

## Владение данными

Домен владеет насосами, привязками насосов к растениям и историей фактического состояния насоса. Состояние устройства, растения и журнал полива принадлежат другим доменам и используются через публичные Facade или contracts.

## Используемые домены

- `device`
- `journal`
- `plant`

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`
- домен `device`

## Алгоритм работы

Facade обновляет привязки, отдает списки насосов, создает насос по умолчанию, запускает и останавливает ручной полив через шлюз команд, читает ACK и завершает полив по state устройства. При поступлении native state от `device` домен записывает фактическое состояние насоса в history. При успешном поливе создаются записи журнала и метрики растения.

## Ограничения

Pump не публикует MQTT напрямую, а использует `PumpCommandGateway`. Проверка доступа к устройству и растению выполняется через соответствующие домены. Параметры полива, ACK wait и history должны приходить из конфигурации. History отражает фактический state насоса, а не журнал команд автоматики.
