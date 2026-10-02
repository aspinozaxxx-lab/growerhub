# Домен journal

## Назначение

Ведёт журнал растения: записи, фото, экспорт и детали полива, включая связь с логической сессией, длительность, режим, рассчитанный объём и причину завершения.

## Публичный Facade

`JournalFacade`

- `listEntries(Integer plantId, AuthenticatedUser user)`
- `getLastWatering(Integer plantId, AuthenticatedUser user)`
- `exportJournal(Integer plantId, String format, String language, AuthenticatedUser user)`
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
- `user`.

## Внешние пользователи домена

- REST adapter `api`.
- домены `advisor`, `plant`, `pump`.

## Алгоритм работы

Facade проверяет доступ к растению, читает или изменяет записи, формирует DTO и экспорт. Markdown использует timezone профиля, явное смещение UTC и язык интерфейса (`ru` либо `en`); текст записей не переводится. Полив создаёт по одной записи каждому доступному target; повтор той же pump session и растения не создаёт дубль. Неизвестный расход остаётся `water_volume_l=null`, длительность, режим и причина доступны. `volume_source` различает отчёт расходомера, расчёт и неизвестный объём также в экспорте. Общий расход линии не дублируется между растениями.

## Ограничения

Journal не меняет растение и не исполняет полив. Типы записей являются контрактом домена. Экспорт остаётся представлением журнала. Идемпотентность сессионных записей обеспечивается БД и Facade.
