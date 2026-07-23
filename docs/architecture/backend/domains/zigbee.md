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
- `getOldestHistoryTimestamp()`
- `compactHistoryDay(LocalDateTime fromTs, LocalDateTime toTs)`

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
- домен `maintenance`

## Алгоритм работы

MQTT adapter классифицирует legacy и пользовательские topics и передаёт identity координатора со snapshot-сообщением в Facade. Facade всегда обновляет текущий raw JSON snapshot. В history записываются изменения дискретных свойств, каждое явное событие и числовые значения по интервалу либо при значимом изменении; служебные настройки остаются только в snapshot. Интервалы, пороги и списки свойств задаются конфигурацией, checkpoint хранится в snapshot. Maintenance удаляет старые служебные значения, оставляет числовую точку в час, дискретные переходы и все события. REST проверяет владельца и публикует команды только в base topic через gateway.

## Ограничения

Frontend не подключается к MQTT напрямую. Переименование выполняется только через Zigbee2MQTT. В history индексируются только осмысленные примитивные свойства верхнего уровня; сложные значения остаются в raw JSON snapshot. Текущее состояние и вход автоматизаций не зависят от частоты history. MQTT-пароль не хранится и возвращается один раз. Чужой UUID или IEEE возвращает тот же `404`, что неизвестный объект.
