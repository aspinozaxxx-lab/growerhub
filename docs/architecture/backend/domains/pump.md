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
- варианты `stopSession`, `currentSession`, `listSessions` с `ZigbeeWateringData.Target`
- `hasActiveZigbeeSession(Integer coordinatorId)`
- `pauseSimulatedZigbee` и `deleteSimulatedZigbeeHistory` для жизненного цикла демо
- `listSessions(Integer pumpId, int limit, Long beforeId)`
- `lastCompletedSessionForBox(Integer boxId)`
- `boxStatistics(Integer boxId, String range, int limit, Long beforeId)`
- `boxStatistics(Integer boxId, String range, int limit, Long beforeId, String timezone)`
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
- `getOldestHistoryTimestamp()`
- `compactHistoryDay(LocalDateTime fromTs, LocalDateTime toTs)`

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
- `zigbee` — проверенный исполнитель, живое состояние, ограниченная команда.

## Внешние пользователи домена

- REST adapter `api`.
- MQTT adapter `mqtt`.
- домены `automation`, `device`, `maintenance`.

## Алгоритм работы

Start проверяет владельца, растения и единственный active slot, сохраняет неизменяемую цель и снимок растений. Native сохраняет прежний MQTT-контракт; Zigbee использует координатор, IEEE и свойство через Facade (ADR-007). Zigbee стартует только в timed-режиме, без импульсов; до живого ON после команды показывает starting и не создаёт полив в журнале. Worker ведёт running/pause/stopping, защитные остановки и восстановление по БД; Zigbee start никогда не повторяется. Живой OFF подтверждает завершение; неизвестная остановка остаётся stopping. Завершение идемпотентно создаёт записи snapshot-растениям, объём рассчитывается по скорости, без неё остаётся null. История сохраняет переходы; календарная статистика использует timezone сценария. Физический worker исключает SIMULATED; demo вызывает тот же жизненный цикл и очищает только свои сессии (ADR-006).

## Ограничения

Pump не читает automation JPA и не публикует MQTT напрямую. Одновременно на физическом устройстве активна одна сессия. Новый start не прерывает текущую сессию. Паузы pulse не входят в длительность. При неизвестной скорости объём остаётся `null`; метрика объёма не создаётся. Переходы состояния не удаляются. Defaults и лимиты задаются конфигурацией.
