# Ansible

Ansible описывает серверную инфраструктуру GrowerHub и не содержит бизнес-логику приложения.

## Структура

- `ansible.cfg` - настройки запуска Ansible.
- `inventory` - список хостов и host vars.
- `group_vars` - общие переменные.
- `playbooks` - сценарии установки и настройки.
- `roles` - переиспользуемые роли.
- `requirements.yml` - внешние зависимости ролей или коллекций.
- `Makefile` - короткие команды запуска.

## Роли

- `java_backend` - backend-сервис systemd из собранного jar.
- `mosquitto` — MQTT broker с TLS listener `8883`, локальным backend listener и Dynamic Security.
- `nginx` - reverse proxy и раздача frontend dist.
- `gh_db_postgresql` - PostgreSQL.
- `pgadmin` - администрирование БД.

## Правила

Playbook должен быть идемпотентным. Секреты и host-specific значения хранятся в inventory vars или внешнем secret-хранилище, а не в задачах роли. Mosquitto использует запрет ACL по умолчанию, отдельные минимальные роли backend/provisioning/координатора и certbot deploy hook с безопасным reload. Публичный `1883` не открывается.
