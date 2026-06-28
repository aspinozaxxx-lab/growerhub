# Домен user

## Назначение

Управляет пользователями, профилями, ролями, активностью и admin CRUD.

## Публичный Facade

`UserFacade`

- `listUsers()`
- `getUser(Integer userId)`
- `findByEmail(String email)`
- `getAuthUser(Integer userId)`
- `createUser(String email, String username, String role, String password)`
- `createExternalUser(String email, String username)`
- `updateUser(Integer userId, String username, String role, Boolean active)`
- `updateProfile(Integer userId, String email, String username)`
- `deleteUser(Integer userId)`

## Публичные контракты

Пакет `contract` отсутствует. Публичные records объявлены внутри Facade:

- `UserProfile`
- `AuthUser`

## Владение данными

Домен владеет таблицей пользователей. Auth identities и refresh tokens принадлежат домену `auth`; устройства принадлежат домену `device`.

## Используемые домены

- `auth`
- `device`

## Внешние пользователи домена

- REST adapter `api`
- security filter в `common.config.security`
- домены `auth`, `plant`

## Алгоритм работы

Facade читает пользователей, создает локальных и внешних пользователей, обновляет профиль и admin-поля. При создании локального пользователя вызывает auth для identity. При удалении отвязывает устройства и удаляет auth identities.

## Ограничения

User не хранит auth credentials. Удаление пользователя должно координироваться через Facade других доменов. Публичные user DTO должны быть вынесены в `contract` при следующем изменении публичной поверхности.
