# Домен user

## Назначение

Управляет пользователями, профилями, часовым поясом, постоянным признаком
завершения первичной настройки, ролями, активностью и admin CRUD.

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
- `deleteUser(Integer userId)`

## Публичные контракты

- `UserProfile`
- `AuthUser`
- `ProductAnalyticsSnapshot`

## Владение данными

Домен владеет таблицей пользователей. Auth identities и refresh tokens принадлежат домену `auth`; устройства принадлежат домену `device`.

## Используемые домены

- `auth`
- `device`

## Внешние пользователи домена

- REST adapter `api`
- security filter в `common.config.security`
- домены `auth`, `automation`, `onboarding`, `plant`

## Алгоритм работы

Facade читает пользователей, создаёт локальных и внешних пользователей,
валидирует IANA timezone, обновляет профиль и admin-поля. При создании
локального пользователя вызывает auth для identity. При удалении отвязывает
устройства и удаляет auth identities. Завершение первичной настройки
записывается один раз и не откатывается при временной потере связи или удалении
ресурса.

## Ограничения

User не хранит auth credentials. Удаление пользователя должно координироваться
через Facade других доменов. Значение timezone должно быть валидным
идентификатором IANA; значение для нового пользователя приходит из
конфигурации.
