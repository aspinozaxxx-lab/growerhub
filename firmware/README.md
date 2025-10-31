# Firmware: встроенные настройки сети, Multi-WiFi и TLS

Прошивка больше не зависит от `config.ini` и SPIFFS. Все значения по умолчанию (список точек доступа, URL сервера и корневой сертификат) зашиты в код и доступны через структуру `BUILTIN_NETWORK_DEFAULTS` в `src/System/SettingsManager.h`.

## Основные возможности
- **Встроенные дефолты**: SSID, пароли, URL сервера и CA-сертификат загружаются напрямую из констант при старте.
- **Multi-WiFi**: устройство хранит пользовательские сети в EEPROM и дополняет их встроенным списком, выбирая точку доступа с лучшим сигналом.
- **HTTPS/TLS**: встроенный сертификат используется автоматически; если в настройках сохранён пользовательский CA, он подставляется вместо стандартного.

## Где заданы дефолты

```cpp
constexpr DefaultNetworkProfile BUILTIN_NETWORK_DEFAULTS = {
    3,
    {
        {"JR", "qazwsxedc"},
        {"AKADO-E84E", "90838985"},
        {"TP-LINK_446C", "70863765"},
        {"", ""}, {"", ""}, {"", ""}, {"", ""}, {"", ""}, {"", ""}, {"", ""}
    },
    "https://growerhub.ru",
    R"(-----BEGIN CERTIFICATE-----
MIIEVzCCAj+gAwIBAgIRAKp18eYrjwoiCWbTi7/UuqEwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjQwMzEzMDAwMDAw
WhcNMjcwMzEyMjM1OTU5WjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg
RW5jcnlwdDELMAkGA1UEAxMCRTcwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARB6AST
CFh/vjcwDMCgQer+VtqEkz7JANurZxLP+U9TCeioL6sp5Z8VRvRbYk4P1INBmbef
QHJFHCxcSjKmwtvGBWpl/9ra8HW0QDsUaJW2qOJqceJ0ZVFT3hbUHifBM/2jgfgw
gfUwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcD
ATASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSuSJ7chx1EoG/aouVgdAR4
wpwAgDAfBgNVHSMEGDAWgBR5tFnme7bl5AFzgAiIyBpY9umbbjAyBggrBgEFBQcB
AQQmMCQwIgYIKwYBBQUHMAKGFmh0dHA6Ly94MS5pLmxlbmNyLm9yZy8wEwYDVR0g
BAwwCjAIBgZngQwBAgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3gxLmMubGVu
Y3Iub3JnLzANBgkqhkiG9w0BAQsFAAOCAgEAjx66fDdLk5ywFn3CzA1w1qfylHUD
aEf0QZpXcJseddJGSfbUUOvbNR9N/QQ16K1lXl4VFyhmGXDT5Kdfcr0RvIIVrNxF
h4lqHtRRCP6RBRstqbZ2zURgqakn/Xip0iaQL0IdfHBZr396FgknniRYFckKORPG
yM3QKnd66gtMst8I5nkRQlAg/Jb+Gc3egIvuGKWboE1G89NTsN9LTDD3PLj0dUMr
OIuqVjLB8pEC6yk9enrlrqjXQgkLEYhXzq7dLafv5Vkig6Gl0nuuqjqfp0Q1bi1o
yVNAlXe6aUXw92CcghC9bNsKEO1+M52YY5+ofIXlS/SEQbvVYYBLZ5yeiglV6t3S
M6H+vTG0aP9YHzLn/KVOHzGQfXDP7qM5tkf+7diZe7o2fw6O7IvN6fsQXEQQj8TJ
UXJxv2/uJhcuy/tSDgXwHM8Uk34WNbRT7zGTGkQRX0gsbjAea/jYAoWv0ZvQRwpq
Pe79D/i7Cep8qWnA+7AE/3B3S/3dEEYmc0lpe1366A/6GEgk3ktr9PEoQrLChs6I
tu3wnNLB2euC8IKGLQFpGtOO/2/hiAKjyajaBP25w1jF0Wl8Bbqne3uZ2q1GyPFJ
yRmT7/OXpmOH/FVLtwS+8ng1cAmpCujPwteJZNcDG0sF2n/sc0+SQf49fdyUK0ty
+VUwFj9tmWxyR/M=
-----END CERTIFICATE-----)"
};
```

### Как изменить дефолты
- Отредактируйте структуру `BUILTIN_NETWORK_DEFAULTS` и пересоберите проект.
- Перепрошивка SPIFFS больше не требуется: достаточно `pio run -t upload`.

## Сборка и прошивка (PlatformIO)
```bash
pio run -e <env>
pio run -t upload -e <env>
```

## Тесты
- Требования: установлен Python 3.11+ и PlatformIO (`pip install platformio`).
- Проверка версии и запуск базового набора:

```bash
pio --version
pio test -e wifi_service_test
```

- Дополнительно можно ограничить тесты и включить подробный вывод:

```bash
pio test -e wifi_service_test -f wifi_service_basic -v
```

## Поведение Wi-Fi
- WiFiMulti регистрирует все пользовательские сети и встроенные дефолты.
- Асинхронное сканирование каждые ~20 секунд ищет более сильные точки доступа.
- Если в EEPROM есть пользовательские записи, они имеют приоритет над встроенными.

## Поведение TLS
- `HTTPClient` использует `WiFiClientSecure` со встроенным CA.
- Сохранённый пользователем сертификат заменяет стандартный без перепрошивки.

## EEPROM и сохранность настроек
- EEPROM объёмом 1024 байта позволяет хранить до 10 пользовательских сетей и прочие параметры.
- `factory reset` очищает пользовательские сети и пересобирает `deviceID` на основе MAC-адреса.
- `serverURL` всегда берётся из встроенных значений и не хранится в EEPROM.
