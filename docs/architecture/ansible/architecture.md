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
