# Домен auth

## Назначение

Отвечает за аутентификацию, JWT, refresh tokens, локальный вход, SSO и управление методами входа пользователя.

## Публичный Facade

`AuthFacade`

- `me(AuthenticatedUser user)`
- `login(String email, String password, HttpServletRequest request, HttpServletResponse response)`
- `ssoLogin(String provider, String redirectPath, String authorization, String accept)`
- `ssoCallback(String provider, String code, String state, HttpServletRequest request, HttpServletResponse response)`
- `refresh(HttpServletRequest request, HttpServletResponse response)`
- `logout(HttpServletRequest request, HttpServletResponse response)`
- `updateProfile(AuthenticatedUser user, String email, String username)`
- `changePassword(AuthenticatedUser user, String currentPassword, String newPassword)`
- `authMethods(AuthenticatedUser user)`
- `configureLocal(AuthenticatedUser user, String email, String password)`
- `deleteMethod(AuthenticatedUser user, String provider)`
- `createLocalIdentity(Integer userId, String passwordHash, LocalDateTime now)`
- `deleteIdentities(Integer userId)`
- `parseUserId(String token)`

## Публичные контракты

- `AuthMethodLocal`
- `AuthMethodProvider`
- `AuthMethods`
- `AuthTokens`
- `AuthUserProfile`

## Владение данными

Домен владеет auth identity и refresh token таблицами. Профиль пользователя принадлежит домену `user`; auth получает и создает пользователя через публичный Facade домена `user`.

## Используемые домены

- `user`

## Внешние пользователи домена

- REST adapter `api`
- security filter в `common.config.security`
- домен `user`

## Алгоритм работы

Facade принимает сценарий входа, проверки, обновления или выхода. Engine валидирует учетные данные, работает с identity и refresh token, выпускает access token и управляет cookie. SSO flow строит redirect, проверяет state, получает профиль провайдера и связывает identity с пользователем.

SSO callback не передаёт access token через URL. После проверки провайдера он устанавливает защищённую refresh-cookie и перенаправляет только на разрешённый путь внутри `/app`; frontend восстанавливает access token отдельным refresh-запросом.

## Ограничения

Auth не владеет профилем пользователя. Секреты, TTL, cookie settings и provider URL должны приходить из конфигурации. Парсинг токена не должен открывать доступ к данным без проверки пользователя. В production refresh-cookie обязана иметь `HttpOnly`, `Secure` и `SameSite=Lax`.
