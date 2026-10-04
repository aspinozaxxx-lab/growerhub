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
- `searchCare`, `careSummaries`: постраничный поиск и обложки.
- `createCareEntry`, `updateCareEntry`, `deleteCareEntry`, `addCarePhoto`, `deleteCarePhoto`: ручной уход и фотографии.
- `careReminders`, `saveCareReminder`, `actCareReminder`, `deleteCareReminder`: планы ухода и выполнение конкретного экземпляра.
- `createWateringEntries(List<WateringTarget> targets, AuthenticatedUser user, LocalDateTime eventAt, Double ph, String fertilizersPerLiter)`
- `createSessionWateringEntries(List<SessionWateringTarget> targets, LocalDateTime eventAt, Double ph, String fertilizersPerLiter)`

## Публичные контракты

- `CareData`: записи, поиск, сводки и напоминания.
- `JournalEntry`
- `JournalPhoto`
- `JournalPhotoData`
- `JournalWateringDetails`
- `JournalWateringInfo`

## Владение данными

Домен владеет journal entries, приватными photos, планами ручного ухода и watering details. Клиентский ключ записи и хеш фото защищают повтор сохранения; ключ экземпляра напоминания — повтор выполнения. Детали полива хранят nullable объём, длительность, режим, причину завершения и ссылку на pump session; уникальность session и plant обеспечивает exactly-once. Растения и сессии принадлежат другим доменам.

## Используемые домены

- `plant`.
- `user`.

## Внешние пользователи домена

- REST adapter `api`.
- домены `advisor`, `plant`, `pump`, `notification`.

## Алгоритм работы

Facade проверяет владельца растения. Поиск по тексту, месту и имени, типу и дате ограничен аккаунтом и разбит на страницы. Список фото читает метаданные без байтов. API нормализует ограниченный JPEG/PNG, удаляет метаданные; домен ограничивает число фото и не загружает произвольные URL. Ручную запись можно исправить вместе с датой, запись работы оборудования доступна только для чтения. Выполнение текущего экземпляра дела создаёт одну ручную запись; перенос и пропуск её не создают. Повтор по календарю учитывает IANA timezone, пропущенные дни не размножаются. Экспорт сохраняет язык интерфейса, timezone и фактические параметры полива. Автоматические сессии дедуплицируются по session + plant; неизвестный расход остаётся null.


## Ограничения

Journal не меняет растение и не исполняет полив. Типы записей являются контрактом домена. Экспорт остаётся представлением журнала. Идемпотентность сессионных записей обеспечивается БД и Facade.
