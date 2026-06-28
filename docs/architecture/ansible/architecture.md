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

- `java_backend` - backend-сервис.
- `mosquitto` - MQTT broker.
- `nginx` - reverse proxy и static.
- `gh_db_postgresql` - PostgreSQL.
- `pgadmin` - администрирование БД.
- `fastapi_app`, `growerhub_deploy_agent` - legacy или вспомогательные роли.

## Правила

Playbook должен быть идемпотентным. Секреты и host-specific значения хранятся в inventory vars или внешнем secret-хранилище, а не в задачах роли.
