# Домен user

## Назначение

Управляет пользователями, профилями, часовым поясом, постоянным признаком
завершения первичной настройки, заявкой на пилот ПушОк, ролями, активностью и admin CRUD.

## Публичный Facade

`UserFacade`

- `listUsers()`
- `getUser(Integer userId)`
- `findByEmail(String email)`
- `getAuthUser(Integer userId)`
- `createUser(String email, String username, String role, String password)`
- `createExternalUser(String email, String username)`
- `updateUser(Integer userId, String username, String role, Boolean active)`
- `updateProfile(Integer userId, String email, String username, String timezone)`
- `getTimezone(Integer userId)`
- `getTimezones(Set<Integer> userIds)`
- `markOnboardingCompleted(Integer userId)`
- `startCare(Integer userId)`: независимое начало дневника.
- `deleteUser(Integer userId)`
- `getPushokPilot(Integer userId)`
- `savePushokPilot(Integer userId, ContactMethod method, String contact, String equipment)`
- `withdrawPushokPilot(Integer userId)`
- `listPushokPilots()`
- `markPushokPilotContacted(Integer userId)`

## Публичные контракты

- `UserProfile`
- `AuthUser`
- `ProductAnalyticsSnapshot`
- `PushokPilot`: способ связи, заявка и административная запись.

## Владение данными

Домен владеет таблицей пользователей. `care_started_at` хранит начало дневника отдельно от завершения настройки оборудования; оно не учитывается как подключение. В ней хранится одна текущая заявка ПушОк на аккаунт: способ связи, контакт, необязательное описание оборудования, время заявки и отметка обработки. Auth identities и refresh tokens принадлежат домену `auth`; устройства принадлежат домену `device`.

## Используемые домены

- `demo`
- `auth`
- `device`

## Внешние пользователи домена

- REST adapter `api`
- security filter в `common.config.security`
- домены `auth`, `automation`, `journal`, `onboarding`, `plant`, `notification`

## Алгоритм работы

Facade читает пользователей, создаёт локальных и внешних пользователей,
валидирует IANA timezone, обновляет профиль и admin-поля. При создании
локального пользователя вызывает auth для identity. При удалении отвязывает
устройства и удаляет auth identities. Завершение первичной настройки
записывается один раз и не откатывается при временной потере связи или удалении
ресурса.

Технический владелец демофермы имеет account_kind=DEMO, не имеет email и не участвует в обычном входе, списке аккаунтов и продуктовой статистике. Создание и удаление доступны только через Facade; удаление аккаунта сначала удаляет его сохранённую демоферму (ADR-006).

Engine валидирует заявку ПушОк и сохраняет её под блокировкой пользователя. Одинаковый повтор не меняет время; изменение сбрасывает отметку обработки. Отзыв очищает все поля заявки. Координатор и MQTT не создаются.

## Ограничения

User не хранит auth credentials. Удаление пользователя должно координироваться
через Facade других доменов. Значение timezone должно быть валидным
идентификатором IANA; значение для нового пользователя приходит из
конфигурации. Заявки доступны только обычному аккаунту; список и отметка обработки — администратору через REST. Контакты не входят в продуктовую аналитику. Пилот не подтверждает готовую интеграцию.
