# Домен device

## Назначение

Управляет устройствами, их настройками, online/shadow состоянием, ACK, последним state и service events.

## Публичный Facade

`DeviceFacade`

- `findDeviceId(String deviceId)`
- `authenticateDevice(String deviceId, String rawToken)`
- `canUserAccessDevice(String deviceId, Integer userId, boolean admin)`
- `rotateDeviceCredential(Integer devicePk, Integer userId, boolean admin)`
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
- `assignToUser(Integer deviceId, Integer userId)`
- `unassignForUser(Integer deviceId, Integer userId, boolean isAdmin)`
- `assignToUserAggregate(Integer deviceId, Integer userId)`
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
- `DeviceServiceEventData`
- `DeviceServiceEventType`
- `DeviceServiceEventView`
- `DeviceSettingsData`
- `DeviceSettingsUpdate`
- `DeviceShadowState`
- `DeviceStatusUpdate`
- `DeviceSummary`

## Владение данными

Домен владеет устройствами, последним state, ACK и service events. Датчики, насосы и растения не являются его данными; при обработке state домен делегирует запись показаний, историю растений и полив соответствующим доменам.

## Используемые домены

- `plant`
- `pump`
- `sensor`

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt`
- домены `firmware`, `pump`, `sensor`, `user`

## Алгоритм работы

Facade принимает state, ack и events от адаптеров, обновляет device records и shadow, вызывает нужные домены для насосов, датчиков и растений. Для REST отдает summary, агрегаты, настройки и admin views. Удаление устройства очищает связанные данные через публичные Facade других доменов. HTTP-вызовы самого устройства используют отдельный непрозрачный токен: в БД хранится только SHA-256, исходное значение выдаётся один раз при ротации.

## Ограничения

Device не должен напрямую владеть JPA других доменов. MQTT parsing остается в adapter. Формат shadow является контрактом. Настройки устройства и интервалы online должны приходить из конфигурации. Пользовательские операции требуют JWT и владения; HTTP status и чтение device settings/firmware разрешены только самому устройству либо владельцу там, где это явно предусмотрено REST-контрактом.
