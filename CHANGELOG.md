# Changelog

В этом файле фиксируются изменения проекта. Используем формат Keep a Changelog и Semantic Versioning.

## [2025-10-24]

### Добавлено
- Добавлены проверки состояния DeviceShadowStore перед start/stop в API ручного полива (409 при некорректном статусе).
- Добавлены юнит-тесты для новых правил.
- Эндпоинт POST /api/manual-watering/stop с публикацией команды pump.stop (server/api_manual_watering.py).
- Тест test_manual_watering_stop с фейковым MQTT-паблишером (server/tests/test_api_manual_watering_stop.py).
- Теневое хранилище состояний устройств (server/device_shadow.py) с расчётом remaining_s на сервере.
- Эндпоинт GET /api/manual-watering/status для прогресс-бара на фронтенде.
- Вспомогательный эндпоинт POST /_debug/shadow/state для локальной отладки и тестов.
- Фоновый MQTT-подписчик состояний устройств (server/mqtt_subscriber.py), интегрированный в запуск и останов FastAPI.
- Потокобезопасный AckStore и MQTT-подписчик подтверждений (server/ack_store.py, server/mqtt_subscriber.py).
- API GET /api/manual-watering/ack для оперативного получения результата команды.
- Тесты обработчиков state/ack и REST-эндпоинтов (server/tests/test_mqtt_subscriber_handler.py, server/tests/test_mqtt_ack_and_api.py).
- Демонстрационный HTML-интерфейс /static/manual_watering.html для проверки цепочки ручного полива.

### Изменено
- Расширены русские комментарии и докстроки в модулях MQTT-паблишера и ручного полива (server/mqtt_publisher.py, server/api_manual_watering.py).
- Интеграционные тесты ручного полива дополнены подробными пояснениями (server/tests/test_api_manual_watering_start.py).
- Расчёт remaining_s теперь полностью выполняется на сервере по duration_s и started_at.
- Теневой стор и AckStore обновляются на основе реальных MQTT-сообщений.
- Демо-страница отображает прогресс и результаты ack-команд.

### Примечание
- Отладочный эндпоинт /_debug/shadow/state остаётся доступным до включения подписчика в продакшн.

## [2025-10-23]

### Добавлено
- Начальная конфигурация прошивки и инструментов развёртывания (firmware/config.ini, scripts/post_upload.py и др.).

### Изменено
- Обновлены параметры EEPROM и настройки PlatformIO для работы со SPIFFS.

### Важные изменения
- Обновлён API Wi-Fi менеджера и обработка конфигурации SPIFFS.

