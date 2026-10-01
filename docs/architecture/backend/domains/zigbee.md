# Домен zigbee

## Назначение

Хранит пользовательские координаторы, изолированные snapshot Zigbee2MQTT, последний state, историю примитивных свойств и последний ответ на команду.

## Публичный Facade

`ZigbeeFacade`

- `getOverview()`
- `wateringCapabilities`, `resolveWateringExecutor`, `wateringState`
- `startWatering`, `stopWatering`, `isSimulatedCoordinator`
- `createCoordinator(AuthenticatedUser user, String name)`
- `isPushokAvailable`, `createPushokCoordinator`, `retryPushokPairing`
- `getPushokConnections`, `reportPushokPairing` — внутренний контракт технического worker; результат устаревшей попытки не принимается.
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
- `PushokCredentialGateway`, `PushokConnection` — создание зашифрованного доступа и конфигурация облачного worker.
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
- технический adapter `pushok`
- домен `maintenance`
- домены `automation`, `pump`, `demo`

## Алгоритм работы

MQTT передаёт identity, payload и RETAIN; конверт relay v1 разобран адаптером (ADR-009). Полное имя разрешается по текущему bridge/devices; неизвестные и неоднозначные маршруты отклоняются. Retained обновляет raw snapshot, но не живое состояние, время связи или историю. История отбирает числа и переходы; статистика учитывает единицы и timezone. REST проверяет владельца, команды идут через gateway. Поливной канал определяется по exposes или записи проверки; общий ON и таймер запрещены, OFF доступен. Допуск требует UUID+IEEE+канал, hash definition, прошивку, локальный таймер и запрет продления повтором (ADR-007). Start объединяет длительность и ON, QoS 0 без retain; stop доступен после отзыва. Активный полив запрещает архивирование/ротацию. SIMULATED не имеет broker credentials и внешнего MQTT ingress; команды идут в demo, snapshots проходят общий разбор (ADR-006).

## Ограничения

Frontend не подключается к MQTT напрямую. Для ПушОка добавление и переименование выполняются в «Управляторе». В history индексируются примитивные свойства; сложные значения остаются в raw snapshot. Zigbee2MQTT получает MQTT-пароль один раз; серверный мост ПушОка хранит его вместе с отдельным ключом хаба только в AES-GCM ciphertext (ADR-008). Подтверждённый хаб принадлежит одному аккаунту. Неудачная непривязанная попытка не резервирует чужой ID бессрочно. Demo не создаёт внешний доступ. Чужой UUID или IEEE возвращает тот же `404`, что неизвестный объект.
