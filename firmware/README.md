# Firmware: конфигурация через SPIFFS, multi‑WiFi и TLS

Данный модуль прошивки использует файловую систему SPIFFS для загрузки конфигурации из `firmware/config.ini`. Это позволяет обновлять настройки (сервер, список Wi‑Fi, CA‑сертификат) без перекомпиляции прошивки.

## Ключевые изменения
- Поддержка SPIFFS: `config.ini` автоматически копируется в `data/` и заливается после прошивки.
- Multi‑WiFi: устройство хранит до 10 Wi‑Fi сетей и подключается к лучшей доступной.
- HTTPS/TLS: при наличии CA PEM в конфиге используется проверка сертификата сервера.

## Где лежит конфиг
- Исходник: `firmware/config.ini` (в репозитории).
- На устройстве: SPIFFS (`/config.ini`).
- При прошивке PlatformIO выполняет скрипт, который копирует `firmware/config.ini` в `firmware/data/` и запускает `uploadfs` для заливки файловой системы.

## Формат `config.ini`

Минимальные секции: `[server]`, `[wifi]`. Опционально `[tls]`.

```ini
[server]
base_url=https://growerhub.ru

[wifi]
; До 10 записей формата ap=SSID:PASSWORD
ap=HomeWiFi:supersecret
ap=Office:12345678

[tls]
; Блок PEM (многострочный) между ca_pem_begin и ca_pem_end
ca_pem_begin
-----BEGIN CERTIFICATE-----
... ваш CA сертификат ...
-----END CERTIFICATE-----
ca_pem_end
```

Пояснения:
- `[server].base_url` — базовый URL API. Не хранится в EEPROM, берётся из `config.ini` при старте.
- `[wifi].ap` — список известных сетей (до 10). Если в EEPROM нет пользовательских сетей, используются значения из `config.ini`.
- `[tls].ca_pem` — корневой/промежуточный сертификат(ы) сервера в формате PEM. Рекомендуется задавать блоком `ca_pem_begin/ca_pem_end`. Если блок не указан, используется встроенный фолбэк CA.

## Сборка и прошивка (PlatformIO)

Команды (в каталоге `firmware`):

```bash
# Сборка
pio run -e <env>

# Прошивка MCU (после неё автоматически зальётся SPIFFS с config.ini)
pio run -t upload -e <env>

# Ручная заливка SPIFFS при необходимости
pio run -t uploadfs -e <env>
```

Технические детали интеграции:
- `platformio.ini` включает `board_build.filesystem = spiffs`, `data_dir = data` и `extra_scripts = scripts/post_upload.py`.
- `scripts/post_upload.py` копирует `firmware/config.ini` → `firmware/data/config.ini` и запускает `uploadfs` после `upload`.

## Работа сети (WiFiMulti)
- Инициализация через `WiFiMulti`: регистрируются все сети из настроек.
- Фоновый асинхронный скан каждые ~20 секунд и попытки переподключения.
- При наличии пользовательских сетей в EEPROM они имеют приоритет над дефолтами из `config.ini`.

## Безопасность (TLS)
- `HTTPClient` использует `WiFiClientSecure` и при наличии CA PEM в `config.ini` валидирует сертификат сервера.
- Рекомендуется указывать актуальный корневой/промежуточный сертификат вашего домена в `[tls]`.
- При отсутствии PEM используется встроенный фолбэк CA, что может перестать быть валидным со временем.

## Миграция и обратная совместимость
- EEPROM увеличен до 1024 байт и больше не хранит `serverURL` — адрес сервера читается из `config.ini`.
- Хранилище Wi‑Fi в EEPROM: до 10 сетей. `factory reset` очищает пользовательские Wi‑Fi и пересобирает `deviceID` из MAC.
- Сигнатуры изменены:
  - `WiFiManager::begin(deviceHostname)` вместо `begin(ssid, password, deviceHostname)`
  - `WiFiManager::addAccessPoint(ssid, password)` — регистрация сетей
  - `HTTPClient::begin(url, id, caPem)` — TLS с опциональным PEM

Если используете сторонний код, обновите вызовы и перезалейте SPIFFS.

## Отладка и подсказки
- Если `config.ini` не подхватывается: проверьте, что после `upload` выполняется `uploadfs` (смотрите логи `post_upload.py`). При необходимости выполните `pio run -t uploadfs -e <env>` вручную.
- Проверьте, что `firmware/config.ini` сохранён в UTF‑8 (PEM блок должен быть без изменений строк).
- Сообщения в `post_upload.py` могут отображаться с неправильной кодировкой — это не влияет на работу. Можно пересохранить файл в UTF‑8 без BOM и поправить тексты сообщений.
