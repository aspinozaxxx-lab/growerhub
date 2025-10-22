# Changelog

Фиксируем заметные изменения проекта. Формат вдохновлён Keep a Changelog. Версионирование — SemVer, когда возможно.

## [2025-10-23]

### Добавлено
- Прошивка: загрузка конфигурации из SPIFFS (`firmware/config.ini`).
- Поддержка нескольких Wi‑Fi (до 10) через `WiFiMulti` и фоновые сканы.
- HTTPS/TLS для HTTP‑клиента с поддержкой CA PEM в `config.ini`.
- Автозаливка `config.ini` в SPIFFS после прошивки через `scripts/post_upload.py`.
- Документация: раздел о конфигурации и примеры в `firmware/README.md`.

### Изменено
- `EEPROM_SIZE` увеличен до 1024 байт; `serverURL` больше не хранится в EEPROM и берётся из `config.ini`.
- Обновлён `platformio.ini`: включён SPIFFS (`data_dir`, `extra_scripts`).

### Важно (breaking changes)
- Изменились сигнатуры:
  - `WiFiManager::begin(deviceHostname)` вместо `begin(ssid, password, deviceHostname)`
  - новый метод `WiFiManager::addAccessPoint(ssid, password)`
  - `HTTPClient::begin(url, id, caPem)`
- Требуется перезалить SPIFFS с актуальным `config.ini` после прошивки.

