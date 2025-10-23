# Changelog

Обновления фиксируют развитие проекта. Следуем формату Keep a Changelog и Semantic Versioning.

## [2025-10-24]

### Добавлено
- Эндпоинт `POST /api/manual-watering/stop` с публикацией команды `pump.stop` (`server/api_manual_watering.py`).
- Тест `test_manual_watering_stop` с фейковым MQTT-паблишером без выхода в сеть (`server/tests/test_api_manual_watering_stop.py`).
- Теневое хранилище состояний устройств (`server/device_shadow.py`) и расчёт remaining_s на сервере.
- Эндпоинт `GET /api/manual-watering/status` для фронтенда с прогресс-баром.
- Временный отладочный эндпоинт `POST /_debug/shadow/state` для тестов и локальной отладки.

### Изменено
- Расширены русские докстроки и комментарии в модулях MQTT-паблишера и ручного полива (`server/mqtt_publisher.py`, `server/api_manual_watering.py`).
- Дополнены комментариями интеграционные тесты ручного полива (`server/tests/test_api_manual_watering_start.py`).
- Прогресс расчёта `remaining_s` теперь всегда выполняется на сервере по `duration_s` и `started_at`.

## [2025-10-23]

### ���������
- ��訢��: ����㧪� ���䨣��樨 �� SPIFFS (`firmware/config.ini`).
- �����প� ��᪮�쪨� Wi?Fi (�� 10) �१ `WiFiMulti` � 䮭��� ᪠��.
- HTTPS/TLS ��� HTTP?������ � �����প�� CA PEM � `config.ini`.
- ��⮧������ `config.ini` � SPIFFS ��᫥ ��訢�� �૧ `scripts/post_upload.py`.
- ���㬥����: ࠧ��� � ���䨣��樨 � �ਬ��� � `firmware/README.md`.

### ��������
- `EEPROM_SIZE` 㢥��祭 �� 1024 ����; `serverURL` ����� �� �࠭���� � EEPROM � ������� �� `config.ini`.
- ������� `platformio.ini`: ������ SPIFFS (`data_dir`, `extra_scripts`).

### ����� (breaking changes)
- ���������� ᨣ������:
  - `WiFiManager::begin(deviceHostname)` ����� `begin(ssid, password, deviceHostname)`
  - ���� ��⮤ `WiFiManager::addAccessPoint(ssid, password)`
  - `HTTPClient::begin(url, id, caPem)`
- �ॡ���� ��૧����� SPIFFS � ���㠫�� `config.ini` ��᫥ ��訢��.
*** End Patch
