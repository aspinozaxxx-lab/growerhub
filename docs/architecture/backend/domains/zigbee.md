# Домен zigbee

## Назначение

Хранит админский snapshot Zigbee2MQTT bridge, координатора, Zigbee-устройств, последнего state и последнего ответа на команду.

## Публичный Facade

`ZigbeeFacade`

- `getOverview()`
- `handleMqttSnapshot(ZigbeeMqttSnapshotMessage message)`
- `permitJoin(Integer seconds)`
- `setDeviceState(String ieeeAddress, String state)`
- `renameDevice(String ieeeAddress, String friendlyName)`

## Публичные контракты

- `ZigbeeBridgeData`
- `ZigbeeCommandGateway`
- `ZigbeeCommandPublishResult`
- `ZigbeeCommandResponseData`
- `ZigbeeCoordinatorData`
- `ZigbeeDeviceData`
- `ZigbeeMqttMessageType`
- `ZigbeeMqttSnapshotMessage`
- `ZigbeeOverviewData`

## Владение данными

Домен владеет только техническим snapshot Zigbee2MQTT: bridge info/state, список устройств, последний raw state устройства, availability и последний command response. Он не создает бизнес-устройства GrowerHub и не владеет историями, датчиками, насосами или растениями.

## Используемые домены

Нет.

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`

## Алгоритм работы

MQTT adapter классифицирует topics `zigbee2growerhub/#` и передает snapshot-сообщение в Facade. Facade обновляет raw JSON snapshot в БД. REST adapter отдает overview для админки и публикует команды через `ZigbeeCommandGateway`; итог команды приходит позже через `bridge/response/*`.

## Ограничения

Frontend не подключается к MQTT напрямую. Coordinator read-only для переименования. Переименование устройств выполняется только через Zigbee2MQTT `bridge/request/device/rename`; локальное имя меняется после MQTT snapshot от Zigbee2MQTT.
