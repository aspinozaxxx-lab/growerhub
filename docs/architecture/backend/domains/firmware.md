# Домен firmware

## Назначение

Хранит бинарники firmware, проверяет доступность обновления и запускает OTA-обновление устройства.

## Публичный Facade

`FirmwareFacade`

- `checkFirmwareUpdate(String deviceId)`
- `uploadFirmware(MultipartFile file, String version, String hardwareProfile)`
- `triggerLatestUpdate(String deviceId)`
- `triggerUpdate(String deviceId, String version)`
- `listFirmwareVersions()`
- `toVersionResponse(FirmwareVersionInfo info)`

## Публичные контракты

- `FirmwareCheckResult`
- `FirmwareHardwareProfile`
- `FirmwareTriggerResult`
- `FirmwareUpdateGateway`
- `FirmwareUploadResult`
- `FirmwareVersionInfo`

## Владение данными

Домен владеет файловым хранилищем firmware binaries. Статус firmware update хранится в домене `device`; firmware получает и обновляет его через публичный Facade `device`.

## Используемые домены

- `device`

## Внешние пользователи домена

- REST adapter `api`
- MQTT adapter `mqtt` через `FirmwareUpdateGateway`

## Алгоритм работы

Facade сохраняет загруженный бинарник с обязательным аппаратным профилем, строит
отдельный список доступных версий каждого профиля по времени публикации и
сравнивает последнюю совместимую версию с фактическим `fw_ver` устройства.
При запуске он проверяет владение, online и отсутствие выполняющегося обновления,
считает SHA-256, до публикации фиксирует `QUEUED` в домене `device`, затем
отправляет OTA-команду через шлюз. ACK переводит состояние в `DOWNLOADING`,
`RESTARTING` или `ERROR`; только state с целевым `fw_ver` переводит его в
`SUCCESS`. Не подтверждённое за настроенный интервал обновление становится
`ERROR`.

CI с одной версией собирает два бинарника: `esp32dev` и `esp32c3_supermini`,
кладёт их и manifest с отдельными SHA-256 в backend jar. При запуске backend
атомарно публикует оба бинарника в устойчивое файловое хранилище. После deploy CI
скачивает оба публичных URL и сверяет их SHA-256. Файлы без аппаратного профиля в
имени не участвуют в автоматическом выборе обновления.

## Ограничения

Firmware не владеет устройствами и не публикует MQTT напрямую. Публичный базовый URL, директория бинарников, timeout подтверждения и настройки шлюза должны быть конфигурацией. Пользователь может запускать только последнюю опубликованную версию своего аппаратного профиля для принадлежащего ему устройства; административный endpoint с явной версией сохраняется для диагностики. Если устройство ещё не сообщило `hw_profile`, OTA запрещено до первичной прошивки по USB.
