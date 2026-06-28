# Домен journal

## Назначение

Ведет журнал растения: записи, фото, экспорт и детали полива.

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

## Публичные контракты

- `JournalEntry`
- `JournalPhoto`
- `JournalPhotoData`
- `JournalWateringDetails`
- `JournalWateringInfo`

## Владение данными

Домен владеет journal entries, photos и watering details. Растения не принадлежат journal; доступ к ним и проверка владельца выполняются через публичный Facade домена `plant`.

## Используемые домены

- `plant`

## Внешние пользователи домена

- REST adapter `api`
- домены `advisor`, `plant`, `pump`

## Алгоритм работы

Facade проверяет доступ к растению, читает или изменяет записи журнала, формирует DTO и экспорт. Для ручного полива создает watering entries по списку целей, пропуская недоступные растения.

## Ограничения

Journal не должен напрямую менять растение. Типы записей являются контрактом домена. Экспорт остается представлением журнала, а не отдельным источником данных.
