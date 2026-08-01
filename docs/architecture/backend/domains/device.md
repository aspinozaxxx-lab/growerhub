# Домен device

## Назначение

Управляет устройствами, их настройками, online/shadow состоянием, ACK, последним state и service events.

## Публичный Facade

`DeviceFacade`

- `findDeviceId(String deviceId)`
- `authenticateDevice(String deviceId, String rawToken)`
- `canUserAccessDevice(String deviceId, Integer userId, boolean admin)`
- `rotateDeviceCredential(Integer devicePk, Integer userId, boolean admin)`
- `provisionMqttDevice(String requestedDeviceId, boolean rotate)`
- `claimDevice(String requestedDeviceId, Integer userId)`
- `getDeviceSummary(Integer deviceId)`
- `getFirmwareStatus(String deviceId)`
- `markFirmwareUpdate(String deviceId, String version, String firmwareUrl)`
- `handleState(String deviceId, DeviceShadowState state, LocalDateTime now)`
- `handleAck(String deviceId, String correlationId, String result, String status, Map<String, Object> payloadMap, LocalDateTime receivedAt, LocalDateTime expiresAt)`
- `handleServiceEvent(String deviceId, DeviceServiceEventData event, LocalDateTime receivedAt)`
- `cleanupExpiredAcks()`
- `touchLastSeen(String deviceId, LocalDateTime now)`
- `getShadowState(String deviceId)`
- `updateManualWateringState(String deviceId, DeviceShadowState.ManualWateringState manualState, LocalDateTime updatedAt)`
- `getManualWateringView(String deviceId)`
- `getSettings(String deviceId)`
- `updateSettings(String deviceId, DeviceSettingsUpdate update)`
- `listDevices()`
- `listMyDevices(Integer userId)`
- `listAdminDevices()`
- `listRecentServiceEventsByDeviceIds(List<Integer> deviceIds, int limitPerDevice)`
- `unassignForUser(Integer deviceId, Integer userId, boolean isAdmin)`
- `unassignForUserAggregate(Integer deviceId, Integer userId, boolean isAdmin)`
- `adminAssign(Integer deviceId, Integer userId)`
- `adminUnassign(Integer deviceId)`
- `deleteDevice(String deviceId)`
- `unassignDevicesForUser(Integer userId)`

## Публичные контракты

- `DeviceAckStore`
- `DeviceAggregate`
- `DeviceFirmwareStatus`
- `DeviceCredential`
- `DeviceMqttCredential`
- `DeviceBrokerCredentialGateway`
- `DeviceServiceEventData`
- `DeviceServiceEventType`
- `DeviceServiceEventView`
- `DeviceSettingsData`
- `DeviceSettingsUpdate`
- `DeviceShadowState`
- `DeviceStatusUpdate`
- `DeviceSummary`

## Владение данными

Домен владеет устройствами, хешем device token, временем MQTT-подготовки, состоянием ограничений привязки, последним state, ACK и service events. Датчики, насосы и растения не являются его данными; при обработке state домен делегирует запись показаний, историю растений и полив соответствующим доменам.

## Используемые домены

- `plant`
- `pump`
- `sensor`

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`
- домены `firmware`, `pump`, `sensor`, `user`

## Алгоритм работы

Facade принимает state, ack и events от адаптеров, обновляет device records и shadow, вызывает нужные домены для насосов, датчиков и растений. Административная подготовка атомарно создаёт непривязанное устройство, через broker gateway создаёт отдельный Dynamic Security client с фиксированным client ID и изолированным namespace. Wildcard подписки разрешаются через `subscribePattern`/`unsubscribePattern`; ротация одновременно восстанавливает полный ACL роли. Backend сохраняет только SHA-256 и один раз возвращает открытый пароль. Повторная подготовка требует явной ротации; удаление устройства отзывает broker credentials.

Пользовательская привязка принимает только печатный `device_id`. Свободное устройство назначается текущему пользователю в транзакции; собственное возвращается идемпотентно; занятое другим пользователем возвращает явный конфликт. Неудачные корректно сформированные ID учитываются отдельно по аккаунту: десятая ошибка включает блокировку на 60 минут, затем действует ограничение две попытки в скользящий час до успешной привязки ранее свободного устройства. Блокировки пользователя и устройства не допускают конкурентного захвата.

## Ограничения

Device не должен напрямую владеть JPA других доменов. MQTT parsing и Dynamic Security transport остаются в adapter. Формат shadow является контрактом. Настройки устройства и интервалы online должны приходить из конфигурации. Пользовательские операции требуют JWT и владения; provisioning и выдача нового device token доступны только администратору. Открытый MQTT-пароль не хранится и не возвращается повторно.
