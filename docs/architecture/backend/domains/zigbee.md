# Домен zigbee

## Назначение

Хранит пользовательские координаторы, изолированные snapshot Zigbee2MQTT, последний state, историю примитивных свойств и последний ответ на команду.

## Публичный Facade

`ZigbeeFacade`

- `getOverview()`
- `wateringCapabilities`, `resolveWateringExecutor`, `wateringState`
- `startWatering`, `stopWatering`, `isSimulatedCoordinator`
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
- `getHistoryForAdmin(UUID coordinatorPublicId, String ieeeAddress, String property, Integer hours)`
- `getPowerStatisticsForAutomation(Integer coordinatorId, String ieeeAddress, String stateProperty, String onValue, Integer hours, String timezone)`
- `handleMqttSnapshot(ZigbeeMqttSnapshotMessage message)`
- `seedSimulatedHistory(Integer coordinatorId, String friendlyName, Map<LocalDateTime, Map<String, Object>> history)` — порция истории DEMO/SIMULATED в порядке времени, с однократным чтением snapshot, общим отбором свойств и checkpoint, записью свойств одним параметризованным INSERT. События и свойства сохраняются в общей транзакции.
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
- `ZigbeeWateringData` — цель, проверенные возможности, исполнитель и живое состояние.
- `ZigbeeOverviewData`
- `ZigbeePowerStatistics`

## Владение данными

Домен владеет координатором пользователя и техническим snapshot Zigbee2MQTT: bridge info/state, список устройств, raw state, availability, command response, raw state events и индексированной историей примитивных свойств. Все записи адресуются через внутренний `coordinator_id`; IEEE и friendly name уникальны только внутри координатора. Домен не создаёт native-устройства GrowerHub и не владеет растениями.

## Используемые домены

- `pump` — проверка активной сессии перед архивированием и ротацией координатора.

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`
- домен `maintenance`
- домены `automation`, `pump`, `demo`

## Алгоритм работы

MQTT передаёт координатор, payload и RETAIN. Raw snapshot обновляется всегда; для полива отдельно сохраняются только живой JSON и время без RETAIN. История хранит события, дискретные переходы и числовые значения по настройкам отбора; maintenance прореживает числа и сохраняет переходы. Статистика мощности, времени включения и энергии учитывает единицы и timezone. REST проверяет владельца и отправляет команды через gateway. Допуск полива требует флага и серверной записи физической проверки: UUID+IEEE+канал, hash definition, версия прошивки, локальный таймер и запрет продления повтором (ADR-007). Start — единый payload длительности и ON, QoS 0 без retain; stop доступен после отзыва допуска. Активный полив запрещает архивирование/ротацию. SIMULATED не имеет broker credentials, не принимает обычный MQTT ingress; команды идут в demo, snapshots проходят общий разбор (ADR-006).

## Ограничения

Frontend не подключается к MQTT напрямую. Переименование выполняется только через Zigbee2MQTT. В history индексируются только осмысленные примитивные свойства верхнего уровня; сложные значения остаются в raw JSON snapshot. Текущее состояние и вход автоматизаций не зависят от частоты history. MQTT-пароль не хранится и возвращается один раз. Чужой UUID или IEEE возвращает тот же `404`, что неизвестный объект.
