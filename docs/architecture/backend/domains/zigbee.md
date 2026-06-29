# Домен zigbee

## Назначение

Хранит админский snapshot Zigbee2MQTT bridge, координатора, Zigbee-устройств, последнего state и последнего ответа на команду.

## Публичный Facade

`ZigbeeFacade`

- `getOverview()`
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

Для Zigbee-устройств Facade строит производную модель из `bridge/devices[].definition.exposes`: `features` содержит плоский список возможностей, `metrics` содержит свойства с access bit `STATE=1`, `controls` содержит свойства с access bit `SET=2`. Access bit `GET=4` означает, что Zigbee2MQTT может запросить значение у устройства, но v1 админки не вызывает `/get`.

Generic command `setDeviceProperty` публикует `{ "<property>": value }` в `zigbee2growerhub/<friendly_name>/set` только если property найден в writable `exposes`. Frontend не отправляет MQTT напрямую и не определяет тип устройства вручную.

## Ограничения

Frontend не подключается к MQTT напрямую. Coordinator read-only для переименования. Переименование устройств выполняется только через Zigbee2MQTT `bridge/request/device/rename`; локальное имя меняется после MQTT snapshot от Zigbee2MQTT. Сложные типы `composite/list/color/climate` в v1 отображаются как capability, но не редактируются через админку.
