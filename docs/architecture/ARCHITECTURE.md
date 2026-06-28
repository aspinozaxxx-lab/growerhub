# Архитектура GrowerHub

GrowerHub состоит из backend, frontend, firmware, ansible-инфраструктуры, базы данных, MQTT broker и устройств. Backend является источником правды для пользователей, конфигурации, истории и устойчивого состояния. Устройство является источником фактического состояния железа. Frontend показывает производное состояние и не хранит бизнес-истину.

## Состав проекта

- `backend` - Java backend, REST API, MQTT-адаптеры, домены, миграции БД.
- `frontend` - React-приложение и клиентские API-модули.
- `firmware` - прошивка устройства на C++ и тесты PlatformIO.
- `ansible` - инвентаризация, playbook и роли для серверной инфраструктуры.
- `docs/architecture` - единственная архитектурная документация.
- `configs` - конфигурационные файлы окружения.
- `hardware` - материалы по железу.
- `scripts` - вспомогательные скрипты проекта.
- `server`, `static` - серверные и статические артефакты.
- `.github` - CI/CD workflows.

## Компоненты и каналы

- Frontend обращается к backend через REST.
- Backend обращается к устройствам через MQTT.
- Backend хранит устойчивые данные в БД.
- Firmware публикует состояние устройства и принимает команды через MQTT.

MQTT-топики:

- `gh/dev/<device_id>/state` - текущее состояние устройства.
- `gh/dev/<device_id>/state/ack` - подтверждения команд.
- `gh/dev/<device_id>/events` - служебные события устройства.

## Общие принципы

- REST и MQTT являются тонкими адаптерами.
- Бизнес-логика backend находится в доменах.
- Бизнес-логика устройства находится в модулях firmware.
- Форматы REST и MQTT-сообщений являются контрактом системы.

## MQTT-контракт датчиков

Пользовательский текущий статус датчика передается в state. Допустимые значения: `OK`, `DISCONNECTED`, `ERROR`.

Для air используется общий физический статус DHT, который отображается в логические датчики `AIR_TEMPERATURE` и `AIR_HUMIDITY`. Для soil статус задается отдельно по каждому `port/channel`.

Служебные события:

- `SENSOR_READ_ERROR` - одноразовое событие ошибки чтения датчика.
- `DEVICE_REBOOT_SENSOR_FAILURE` - одноразовое событие перезагрузки устройства из-за сбоя датчика.

После успешного восстановления устройство публикует новый state, а текущий пользовательский статус должен перейти в `OK`.

## Архитектурные документы

Разрешенный состав архитектурной документации строгий:

- `AGENTS.md`
- `docs/architecture/architecture.md`
- `docs/architecture/backend/architecture.md`
- `docs/architecture/backend/domain_rules.md`
- `docs/architecture/backend/domains/advisor.md`
- `docs/architecture/backend/domains/auth.md`
- `docs/architecture/backend/domains/device.md`
- `docs/architecture/backend/domains/firmware.md`
- `docs/architecture/backend/domains/journal.md`
- `docs/architecture/backend/domains/plant.md`
- `docs/architecture/backend/domains/pump.md`
- `docs/architecture/backend/domains/sensor.md`
- `docs/architecture/backend/domains/user.md`
- `docs/architecture/frontend/architecture.md`
- `docs/architecture/frontend/module_rules.md`
- `docs/architecture/firmware/architecture.md`
- `docs/architecture/firmware/module_rules.md`
- `docs/architecture/ansible/architecture.md`
- `docs/architecture/adr/ADR-*.md`

Другие markdown-файлы внутри `docs/architecture` запрещены.

ADR создается только для исключения из архитектурных правил, важного компромисса или изменения системного контракта. ADR содержит контекст, решение и последствия.
