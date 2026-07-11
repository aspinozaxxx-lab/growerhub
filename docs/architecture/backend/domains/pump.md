# Домен pump

## Назначение

Управляет насосами и командами, compatibility-привязками, фактической историей состояния и персистентными логическими сессиями полива для ручного и автоматического запуска.

## Публичный Facade

`PumpFacade`

- `updateBindings(Integer pumpId, List<PumpBindingItem> items, AuthenticatedUser user)`
- `start(Integer pumpId, PumpWateringRequest request, AuthenticatedUser user)`
- `stop(Integer pumpId, AuthenticatedUser user)`
- `reboot(Integer pumpId, AuthenticatedUser user)`
- `status(Integer pumpId, AuthenticatedUser user)`
- `getHistory(Integer pumpId, Integer hours, AuthenticatedUser user)`
- `sessionDefaults()`
- `startSession(PumpSessionData.Start request, AuthenticatedUser user)`
- `stopSession(Integer pumpId, AuthenticatedUser user)`
- `currentSession(Integer pumpId)`
- `listSessions(Integer pumpId, int limit, Long beforeId)`
- `lastCompletedSessionForBox(Integer boxId)`
- `boxStatistics(Integer boxId, String range, int limit, Long beforeId)`
- `listActiveSessionProbes()`
- `advanceSession(Long sessionId, PumpSessionData.LeakProbe probe, LocalDateTime now)`
- `syncAutomationBindings(Integer pumpId, List<PumpSessionData.BoxTarget> targets)`
- `recordStateByDeviceId(Integer devicePk, DeviceShadowState state, LocalDateTime now)`
- `getAck(String correlationId)`
- `finalizeWateringByDeviceId(String deviceId, LocalDateTime now)`
- `listByDeviceId(Integer deviceId, DeviceShadowState state)`
- `listByPlantId(Integer plantId)`
- `listByPlantIdLight(Integer plantId)`
- `ensureDefaultPump(Integer deviceId)`
- `deleteByDeviceId(Integer deviceId)`

## Публичные контракты

- `PumpSessionData`
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

Домен владеет насосами, legacy compatibility-привязками, историей фактического состояния, сессиями полива и snapshot-строками боксов, растений и leak-сенсоров. Snapshot неизменяем после старта. Иерархия automation, журнал растений, растения и device shadow остаются во внешних доменах.

## Используемые домены

- `device`.
- `journal`.
- `plant`.

## Внешние пользователи домена

- REST adapter `api`.
- MQTT adapter `mqtt`.
- домены `automation`, `device`.

## Алгоритм работы

Start валидирует насос, targets и единственный active slot физического устройства, сохраняет snapshot и отправляет ограниченную по времени MQTT-команду. Worker по probe ведёт `running/pause/stopping`, считает только активное время, останавливает по duration, leak, limit, manual, ошибке устройства или потере всех leak-сенсоров. Завершение идемпотентно создаёт журнал каждому snapshot-растению; объём равен `rate × active time` и округляется до 0,001 л. Sessions и box statistics включают `admin_manual`, `automation`, `user_manual`. Legacy start использует automation targets при наличии, иначе compatibility fallback.

## Ограничения

Pump не читает automation JPA и не публикует MQTT напрямую. Одновременно на физическом устройстве активна одна сессия. Новый start не прерывает текущую сессию. Паузы pulse не входят в длительность. При неизвестной скорости объём остаётся `null`; метрика объёма не создаётся. Defaults и лимиты задаются конфигурацией.
