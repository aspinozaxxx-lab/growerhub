# Домен firmware

## Назначение

Хранит бинарники firmware, проверяет доступность обновления и запускает OTA-обновление устройства.

## Публичный Facade

`FirmwareFacade`

- `checkFirmwareUpdate(String deviceId)`
- `uploadFirmware(MultipartFile file, String version)`
- `triggerUpdate(String deviceId, String version)`
- `listFirmwareVersions()`
- `toVersionResponse(FirmwareVersionInfo info)`

## Публичные контракты

- `FirmwareCheckResult`
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

Facade сохраняет загруженный бинарник, строит список доступных версий, проверяет статус обновления устройства и при запуске считает sha256, публикует OTA-команду через шлюз, затем отмечает обновление в домене device.

## Ограничения

Firmware не владеет устройствами и не публикует MQTT напрямую. Публичный базовый URL, директория бинарников и настройки шлюза должны быть конфигурацией.
