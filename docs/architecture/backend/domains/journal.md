# Домен journal

## Назначение

Ведёт журнал растения: записи, фото, экспорт и детали полива, включая связь с логической сессией, длительность, режим, рассчитанный объём и причину завершения.

## Публичный Facade

`JournalFacade`

- `listEntries(Integer plantId, AuthenticatedUser user)`
- `getLastWatering(Integer plantId, AuthenticatedUser user)`
- `exportJournal(Integer plantId, String format, AuthenticatedUser user)`
- `createEntry(Integer plantId, AuthenticatedUser user, String type, String text, LocalDateTime eventAt, List<String> photoUrls)`
- `updateEntry(Integer plantId, Integer entryId, AuthenticatedUser user, String type, String text)`
- `deleteEntry(Integer plantId, Integer entryId, AuthenticatedUser user)`
- `getPhoto(Integer photoId, AuthenticatedUser user)`
- `createWateringEntries(List<WateringTarget> targets, AuthenticatedUser user, LocalDateTime eventAt, Double ph, String fertilizersPerLiter)`
- `createSessionWateringEntries(List<SessionWateringTarget> targets, LocalDateTime eventAt, Double ph, String fertilizersPerLiter)`

## Публичные контракты

- `JournalEntry`
- `JournalPhoto`
- `JournalPhotoData`
- `JournalWateringDetails`
- `JournalWateringInfo`

## Владение данными

Домен владеет journal entries, photos и watering details. Детали полива хранят nullable объём, длительность, режим, причину завершения и ссылку на pump session; уникальность session и plant обеспечивает exactly-once. Растения и сессии принадлежат другим доменам.

## Используемые домены

- `plant`.

## Внешние пользователи домена

- REST adapter `api`.
- домены `advisor`, `plant`, `pump`.

## Алгоритм работы

Facade проверяет доступ к растению, читает или изменяет записи, формирует DTO и экспорт. Для полива пакетно создаёт по одной записи каждому доступному target. Повторный запрос той же pump session и растения не создаёт дубль. Неизвестный расход сохраняется как `water_volume_l=null`, а длительность, режим и причина остаются доступными.

## Ограничения

Journal не меняет растение и не исполняет полив. Типы записей являются контрактом домена. Экспорт остаётся представлением журнала. Идемпотентность сессионных записей обеспечивается БД и Facade.
