# Домен zigbee

## Назначение

Хранит админский snapshot Zigbee2MQTT bridge, координатора, Zigbee-устройства, последний state, history primitive-свойств и последний ответ на команду.

## Публичный Facade

`ZigbeeFacade`

- `getOverview()`
- `getHistory(String ieeeAddress, String property, Integer hours)`
- `handleMqttSnapshot(ZigbeeMqttSnapshotMessage message)`
- `permitJoin(Integer seconds)`
- `setDeviceState(String ieeeAddress, String state)`
- `setDeviceProperty(String ieeeAddress, String property, Object value)`
- `renameDevice(String ieeeAddress, String friendlyName)`

## Публичные контракты

- `ZigbeeBridgeData`
- `ZigbeeCommandGateway`
- `ZigbeeCommandPublishResult`
- `ZigbeeCommandResponseData`
- `ZigbeeCoordinatorData`
- `ZigbeeDeviceData`
- `ZigbeeFeatureData`
- `ZigbeeHistoryPoint`
- `ZigbeeMqttMessageType`
- `ZigbeeMqttSnapshotMessage`
- `ZigbeeOverviewData`

## Владение данными

Домен владеет техническим snapshot Zigbee2MQTT: bridge info/state, список устройств, raw state, availability, command response, raw state events и индексированной историей примитивных свойств. Он не создает бизнес-устройства GrowerHub и не владеет датчиками, насосами или растениями.

## Используемые домены

Нет.

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`

## Алгоритм работы

MQTT adapter классифицирует topics `zigbee2growerhub/#` и передает snapshot-сообщение в Facade. Facade обновляет raw JSON snapshot; для device state пишет raw event и примитивные свойства верхнего уровня в history. REST adapter отдает overview/history для админки и публикует команды через `ZigbeeCommandGateway`; итог команды приходит позже через `bridge/response/*`. Features строятся из `bridge/devices[].definition.exposes`.

## Ограничения

Frontend не подключается к MQTT напрямую. Coordinator read-only для переименования. Переименование устройств выполняется только через Zigbee2MQTT `bridge/request/device/rename`. В history для графиков индексируются только примитивные свойства верхнего уровня state payload; сложные `composite/list/color/climate` остаются в raw JSON.
