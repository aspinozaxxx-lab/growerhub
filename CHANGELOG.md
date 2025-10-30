# Changelog

## [2025-10-31] FastAPI routers layout
- Вынесены HTTP-эндпойнты в отдельные роутеры: manual_watering.py, devices.py, history.py, firmware.py. В main.py остались только точка входа, события и статика. Контракты и URL без изменений.

## [2025-10-31] FastAPI startup safety net
- Цель: подготовить перенос инициализации БД в `startup`, не ломая текущие тесты и окружение.
- Вставлен дублирующий вызов `create_tables()` в `@app.on_event("startup")` с безопасным логированием при повторе; импорт-time вызов оставлен без изменений.
- Совместимость API и тестов сохранена.

## [2025-10-30] MQTT refactor — summary
- Проведён полный рефакторинг MQTT-подсистемы: код перенесён в `server/service/mqtt/`.
- Разделены уровни:
  - `topics.py`, `serialization.py`, `interfaces.py`, `config.py` — протокол и базовые типы.
  - `client.py`, `lifecycle.py` — публикация и жизненный цикл клиента.
  - `router.py`, `handlers/` — маршрутизация и обработчики подписок.
  - `store.py` — сторы для ACK и теневых состояний.
- Обновлены импорты и тесты, документация `docs/manual_watering_protocol.md`.
- Протокол MQTT и форматы JSON не изменялись.
- Все тесты успешно проходят (`pytest -q` зелёный).

## [2025-10-30] MQTT refactor — step 8
- Удалены совместимые модули server/mqtt_*.py, все импорты переведены на server/service/mqtt.
- Обновлены server/app/main.py, тесты и документация manual_watering на новую архитектуру.
- Цель: завершить миграцию MQTT-слоя, оставить единый сервисный фасад и актуальную документацию.

## [2025-10-26] FastAPI MQTT lifecycle integration
- FastAPI startup/shutdown теперь используют фасады service.mqtt.lifecycle вместо ручного вызова подписчиков.
- API manual_watering переведён на service.mqtt.interfaces.IMqttPublisher и новые сторажи; тесты патчат lifecycle/store.

## [2025-10-26] MQTT serialization cleanup
- Удалены избыточные функции deserialize_* и тесты переведены на прямой model_validate_json.
- Совместимый модуль mqtt_protocol.py и тесты приведены к новым API, строки ответов нормализованы.

## [2025-10-26] MQTT store refactor (Ack/Shadow)
- AckStore и DeviceShadowStore перенесены в service/mqtt/store.py с единым управлением инициализацией.
- lifecycle теперь управляет запуском/остановкой сторажей и подписчиков, API и тесты переведены на новые импорты.
- Доктесты/ожидания обновлены на нормальные строки ошибок и совместимые патчи.

## [2025-10-26] MQTT router refactor (subscribers)

- Логика подписчиков и обработчиков вынесена в service/mqtt/router.py и service/mqtt/handlers/ (ACK/state, парсинг топиков и payload).
- service/mqtt/lifecycle.py теперь управляет запуском/остановкой подписчиков через старт/стоп фасады.
- server/mqtt_subscriber.py оставлен как совместимая прокладка для старых импортов и тестов.

## [2025-10-26] MQTT service refactor (topics/serialization)

- Вынесли шаблоны топиков, модели и сериализацию MQTT в server/service/mqtt/ (topics/serialization/interfaces/config), сохранив формат payload.
- mqtt_subscriber, API и тесты переведены на новые импорты, добавлен совместимый реэкспорт server/mqtt_protocol.py.
- Обновили доступ к MQTT-настройкам через сервисную обёртку без изменения поведения инициализации.

## [2025-10-26] MQTT lifecycle refactor (publisher)

- Реализованы service/mqtt/client.py (адаптер PahoMqttPublisher) и service/mqtt/lifecycle.py с фасадом init/get/shutdown.
- server/mqtt_publisher.py заменён тонкой прокладкой с TODO на удаление; приложение и тесты используют service.mqtt.lifecycle.
- Поведение публикации команд, QoS и обработка ошибок подключения сохранены, pytest остаётся зелёным.

## [2025-10-24] MQTT step 4

- Контроллер публикует текущее состояние устройства в топик `gh/dev/ESP32_2C294C/state` с `retain=true`: туда попадает блок `manual_watering` (status, duration_s, started_at, correlation_id) и версия прошивки.
- Состояние отправляется после запуска и остановки полива, после авто-таймаута и при успешном реконнекте MQTT, чтобы сервер всегда видел актуальный снимок и отмечал устройство как онлайн.
- Поле `started_at` пока содержит заглушку `"1970-01-01T00:00:00Z"` до интеграции реального UTC времени (NTP/RTC), запланированной на будущие шаги.
- Retained state используется сервером и фронтендом (`/api/manual-watering/status`), чтобы разблокировать кнопки и отображать прогресс ручного полива.

## [2025-10-24] MQTT step 3

- Добавлена публикация ACK в MQTT-топик устройства `gh/dev/ESP32_2C294C/ack` после обработки команд ручного полива.
- После `pump.start` и `pump.stop` контроллер отвечает с `correlation_id`, `result` и `status`, что даёт серверу подтверждение для фронтенда через `/api/manual-watering/wait-ack`.
- Ошибки формата (например, отсутствует `duration_s` или `type`) теперь возвращают `result=error` и поле `reason` с описанием проблемы.
- ACK публикуется без retain, так как это одноразовое подтверждение; публикация состояния устройства (state с retain) будет добавлена на следующем шаге.

## [2025-10-24] MQTT step 2

- Реализован разбор JSON-команд `pump.start` и `pump.stop`, добавлены подробные логи для отладки цепочки сервер → устройство.
- Насос управляется через существующий контроллер реле, сохраняется локальное состояние (running/idle, duration_s, correlation_id) для будущих ACK/state.
- В `loop()` добавлен автостоп по таймеру `duration_s`, чтобы исключить затяжной полив при потере связи.
- Публикация ACK и state запланирована на следующие шаги (шаг 3+).

## [2025-10-24] MQTT step 1

- Добавлено базовое подключение ESP32 к Wi-Fi (STA) и MQTT, использованы логин/пароль Mosquitto и clientId `ESP32_2C294C`.
- Устройство подписывается на командный топик `gh/dev/ESP32_2C294C/cmd` (QoS=1), логирует входящие сообщения в Serial и автоматически переподключается при обрыве.
- Управление насосом и публикация ack/state намечены на последующие шаги.



