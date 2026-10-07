# Ansible

Ansible описывает серверную инфраструктуру GrowerHub и не содержит бизнес-логику приложения.

## Структура

- `ansible.cfg` - настройки запуска Ansible.
- `inventory` - список хостов, профили и host vars.
- `group_vars` - общие переменные.
- `playbooks` - сценарии установки и настройки.
- `roles` - переиспользуемые роли.
- `requirements.yml` - внешние зависимости ролей или коллекций.
- `Makefile` - короткие команды запуска.

## Роли

- `java_backend` - backend-сервис systemd из собранного jar.
- `mosquitto` — MQTT broker с TLS listener `8883`, локальным backend listener и Dynamic Security; wildcard namespaces пользовательских клиентов используют pattern ACL для subscribe/unsubscribe.
- `nginx` - reverse proxy и раздача frontend dist. Публичные страницы и HTML кабинета перепроверяются в браузере при загрузке; файлы `/assets/` с хешами имеют длительный immutable-кеш. Маршруты кабинета без отдельного HTML используют `/app/index.html` с `noindex`, чтобы публичная SSR-главная не попадала в кабинет.
- `gh_db_postgresql` - PostgreSQL.
- `pgadmin` - администрирование БД.

## Профили развертывания

- `legacy` использует существующий `inventory/hosts.ini`, существующие host vars и профильные playbook без изменения топологии.
- `vps` использует `inventory/vps/hosts.ini`; один хост одновременно входит в группы `web`, `application`, `database` и `mqtt`.
- `playbooks/vps.yml` устанавливает только runtime-компоненты, принимает готовые JAR и frontend dist с локального контроллера и не выполняет серверную сборку.
- `playbooks/vps-tls.yml` запускается после переключения DNS, проверяет DNS на локальном контроллере, выпускает сертификат и включает HTTPS и публичный MQTTS.

Локальный Ansible запускается из venv. Команды `make legacy-*` явно используют старый inventory, команды `make vps-*` — VPS inventory, локальный SSH-ключ и ignored-файл пароля Vault.

Секреты VPS находятся только в зашифрованном `inventory/vps/group_vars/all/vault.yml`. Пароль Vault и приватные SSH-ключи не входят в репозиторий.

Исключение для ключа ПушОка по ADR-008: `PUSHOK_ENCRYPTION_KEY` генерируется на
VPS и хранится в root-only runtime env с отдельной защищённой резервной копией.
CI/CD сохраняет этот env. При повторном provisioning через Ansible ключ и
`PUSHOK_ENABLED` нужно передать через `java_backend_env_extra` из внешнего
хранилища секретов; генерировать новый ключ поверх существующих привязок нельзя.

## Правила

Playbook должен быть идемпотентным. Секреты и host-specific значения хранятся в inventory vars или внешнем secret-хранилище, а не в задачах роли. Mosquitto использует запрет ACL по умолчанию, отдельные минимальные роли backend и provisioning, отдельную роль с буквальным namespace для каждого координатора и каждой серийной Grovika, а также certbot deploy hook с безопасным reload. Публичный `1883` не открывается; временный legacy-брокер Grovika удалён и при применении роли останавливается вместе со своим общим password file.

## Telegram (ADR-011)

`TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_BOT_USERNAME` и
`TELEGRAM_ENABLED` передаются в защищённый runtime env сервиса. CI/CD сохраняет
его; повторный provisioning получает эти значения через `java_backend_env_extra`.
При входящей доставке webhook использует точный HTTPS-адрес `/api/notifications/telegram/webhook`.
По умолчанию доставка выключена, существующие аккаунты не подписываются.

При недоступности прямого Bot API `TELEGRAM_PROXY_HOST` и `TELEGRAM_PROXY_PORT`
задают HTTP CONNECT-прокси только клиенту `messaging`; TLS проверяется обычным
способом, общие настройки JVM и остальных интеграций не меняются. На VPS
`growerhub-telegram-proxy.service` использует существующий sing-box и копию
выхода `foreign` из защищённой конфигурации роутера. Вход — только
`127.0.0.1:18090`, разрешён только `api.telegram.org:443`. Конфигурация с ключами
находится в `/etc/growerhub-telegram-proxy/config.json` (root, 0600), передаётся
изолированному процессу через systemd `LoadCredential` и не входит в репозиторий.
При смене доступа зарубежного VPS обновляется эта копия и перезапускается только
сервис прокси. Конфигурация, firewall и процесс основного роутера не изменяются.

На текущем VPS входящие соединения Telegram тоже завершаются таймаутом, поэтому
`TELEGRAM_UPDATES_ENABLED=true` включает получение команд через `getUpdates` тем
же прокси. Перед переключением webhook удаляется через Bot API с
`drop_pending_updates=false`; команды не сбрасываются. Одновременно работает
только один получатель на бота. Обратный переход: выключить получение, затем
восстановить webhook с прежним секретом. Оба режима используют один Facade и
одни проверки владельца. Получение команд имеет собственный поток, лимит
пакета и паузу после ошибок; 429 учитывает `retry_after`.

## Заказы (ADR-012)

`SHOP_ACCEPTING_REQUESTS` задаётся в том же защищённом runtime env. Первый выпуск принимает заявки только в админку: `SHOP_TELEGRAM_CHAT_ID` не задан, внешние уведомления не отправляются. Для последующего включения Telegram потребуется подтверждённый чат владельца; доставка использует существующий бот. Отдельные ключи СДЭК и карты не нужны. Frontend CI ожидает совпадения опубликованного каталога с backend перед переключением релиза. Публичный endpoint создания заявки ограничен в nginx телом 32 КБ с буферизацией до передачи backend; остальные API сохраняют свои лимиты.
