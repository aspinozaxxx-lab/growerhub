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
- `waterMeterStatistics(user, coordinatorPublicId, ieee, month)` — отчёты расходомера только собственного клапана.
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
- `getAck(String correlationId, AuthenticatedUser user)`
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
- `WaterMeterStatistics`
- `PumpAck` — результат команды с идентификатором устройства для проверки владельца; REST не раскрывает это поле.
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
- `user` — часовой пояс владельца.
- `zigbee` — проверенный исполнитель, живое состояние, ограниченная команда.

## Внешние пользователи домена

- REST adapter `api`.
- MQTT adapter `mqtt`.
- домены `automation`, `device`, `maintenance`.

## Алгоритм работы

Start проверяет владельца, цель, снимок растений и active slot. Уникальный execution key сохраняется до команды: повтор возвращает ту же сессию. Native сохраняет MQTT-контракт; Zigbee использует Facade, starting ждёт живого ON. Worker ведёт running/pause/stopping; start не повторяется. Импульсы требуют отдельной capability: пауза начинается после свежего OFF, пропущенный переход не догоняется. В demo capability подтверждена симулятором. Неизвестное закрытие остаётся stopping. Завершение идемпотентно пишет journal; совпавший отчёт расходомера заменяет оценку. Литры общей линии не дублируются каждому растению. Статистика клапана дедуплицирует завершённые операции по их времени, дневной счётчик не суммируется с ними. Даты используют timezone владельца; без промежуточных отчётов полив относится к дате окончания. Неизвестные и неполные данные явно отмечены. DEMO использует общий жизненный цикл без физических команд (ADR-006/010).

## Ограничения

Pump не читает automation JPA и не публикует MQTT напрямую. Пользовательские команды, статус и привязки требуют владения также для admin; чужой насос возвращает 404. ACK проверяется по текущему владельцу устройства и скрывается как отсутствующий. Административная история и внутренний worker сохраняют отдельный доступ. Одновременно на устройстве активна одна сессия; новый start не прерывает её. Паузы pulse не входят в длительность. При неизвестной скорости объём остаётся `null`; метрика объёма не создаётся. Переходы состояния не удаляются. Defaults и лимиты задаются конфигурацией.
