# Домен zigbee

## Назначение

Хранит пользовательские координаторы, изолированные snapshot Zigbee2MQTT, последний state, историю примитивных свойств и последний ответ на команду.

## Публичный Facade

`ZigbeeFacade`

- `getOverview()`
- `createCoordinator(AuthenticatedUser user, String name)`
- `listCoordinators(AuthenticatedUser user)`
- `getCoordinator(AuthenticatedUser user, UUID coordinatorPublicId)`
- `rotateCoordinatorCredentials(AuthenticatedUser user, UUID coordinatorPublicId)`
- `archiveCoordinator(AuthenticatedUser user, UUID coordinatorPublicId)`
- `getOverview(AuthenticatedUser user, UUID coordinatorPublicId)`
- `getHistory(AuthenticatedUser user, UUID coordinatorPublicId, String ieeeAddress, String property, Integer hours)`
- `permitJoin(AuthenticatedUser user, UUID coordinatorPublicId, Integer seconds)`
- `setDeviceProperty(AuthenticatedUser user, UUID coordinatorPublicId, String ieeeAddress, String property, Object value)`
- `renameDevice(AuthenticatedUser user, UUID coordinatorPublicId, String ieeeAddress, String friendlyName)`
- `getDevicesForUser(AuthenticatedUser user)`
- `getDevicesForAutomation()`
- `getHistory(String ieeeAddress, String property, Integer hours)`
- `handleMqttSnapshot(ZigbeeMqttSnapshotMessage message)`
- `permitJoin(Integer seconds)`
- `setDeviceState(String ieeeAddress, String state)`
- `setDeviceProperty(String ieeeAddress, String property, Object value)`
- `renameDevice(String ieeeAddress, String friendlyName)`

## Публичные контракты

- `ZigbeeBridgeData`
- `ZigbeeBrokerCredentialGateway`
- `ZigbeeCommandGateway`
- `ZigbeeCommandPublishResult`
- `ZigbeeCommandResponseData`
- `ZigbeeCoordinatorData`
- `ZigbeeCoordinatorCreated`
- `ZigbeeCoordinatorSetup`
- `ZigbeeCoordinatorStatus`
- `ZigbeeCoordinatorSummary`
- `ZigbeeDeviceData`
- `ZigbeeFeatureData`
- `ZigbeeHistoryPoint`
- `ZigbeeMqttMessageType`
- `ZigbeeMqttSnapshotMessage`
- `ZigbeeOverviewData`

## Владение данными

Домен владеет координатором пользователя и техническим snapshot Zigbee2MQTT: bridge info/state, список устройств, raw state, availability, command response, raw state events и индексированной историей примитивных свойств. Все записи адресуются через внутренний `coordinator_id`; IEEE и friendly name уникальны только внутри координатора. Домен не создаёт native-устройства GrowerHub и не владеет растениями.

## Используемые домены

Нет.

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`

## Алгоритм работы

MQTT adapter классифицирует legacy topics `zigbee2growerhub/#` и пользовательские `gh/z2m/<mqtt_username>/#`, затем передаёт identity координатора вместе со snapshot-сообщением в Facade. Неизвестный или архивный namespace игнорируется. Facade обновляет raw JSON snapshot; для device state пишет raw event и примитивные свойства верхнего уровня в history. REST adapter проверяет владельца координатора и публикует команды только в его base topic через `ZigbeeCommandGateway`; итог команды приходит позже через `bridge/response/*`. Features строятся из `bridge/devices[].definition.exposes`.

## Ограничения

Frontend не подключается к MQTT напрямую. Переименование устройств выполняется только через Zigbee2MQTT `bridge/request/device/rename`. В history для графиков индексируются только примитивные свойства верхнего уровня state payload; сложные `composite/list/color/climate` остаются в raw JSON. MQTT-пароль формируется криптографически, не сохраняется и возвращается только в ответе создания или ротации с запретом кэширования. Чужой UUID или IEEE возвращает тот же `404`, что неизвестный объект.
