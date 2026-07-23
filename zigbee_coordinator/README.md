# Пакеты подключения Zigbee2MQTT

Каталог содержит два безопасных режима self-service GrowerHub:

- `packages/windows/` — локальный мастер Windows с выбором COM-порта и адаптера `zstack`/`ember`;
- `packages/linux/` — Docker Compose для Raspberry Pi/Linux с USB mapping и постоянным volume;
- `connector/` — направленный MQTT bridge для уже работающего Zigbee2MQTT/Home Assistant.

Персональные MQTT credentials в пакеты и Git не входят. Пользователь создаёт координатор в кабинете GrowerHub и отдельно скачивает `configuration.yaml` и `secret.yaml`. Пароль показывается один раз; при утрате выполняется ротация.

## Новая установка

Zigbee2MQTT подключается напрямую к:

```text
mqtts://growerhub.ru:8883
```

Каждый координатор получает собственные username, client ID и base topic `gh/z2m/<mqtt_username>`. Проверка TLS-сертификата включена. Встроенный frontend слушает только `127.0.0.1:8080`.

1. Скачайте пакет своей платформы из GitHub Releases.
2. Скачайте личные `configuration.yaml` и `secret.yaml` из кабинета.
3. Выберите USB-порт и тип адаптера.
4. Запустите пакет и дождитесь статуса `ONLINE` в GrowerHub.

`data/configuration.yaml` и `data/secret.example.yaml` в репозитории — только шаблоны без рабочих идентификаторов и секретов.

## Существующий Home Assistant

Connector не заменяет локальный MQTT. Он передаёт наружу только состояние, availability, список устройств и ответы Zigbee2MQTT; обратно — только `/set`, `/get` и `bridge/request/*`. Раздельные внутренние деревья исключают циклическую пересылку.

Локальные MQTT credentials вводятся в браузере и попадают только в скачанный локальный `bridge.conf`; GrowerHub их не получает.

## Безопасность

- публичный незащищённый MQTT `1883` не используется;
- `secret.yaml`, данные Zigbee-сети, логи и локальный `bridge.conf` игнорируются Git;
- неизвестные namespace и архивные координаторы backend игнорирует;
- старые общие credentials отзываются только после переноса legacy-координатора и end-to-end smoke-теста.
