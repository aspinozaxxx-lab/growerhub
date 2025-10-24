# Backend / FastAPI

## Структура каталога server/
`	ext
server/
  app/
    main.py          # точка входа FastAPI
    core/database.py # SQLAlchemy engine и сессии
    models/database_models.py
    api/             # (пока пусто) место для роутеров
    services/        # задел под бизнес-логику
  api_manual_watering.py # ручной полив и ack-flow
  mqtt_protocol.py       # схемы топиков и payload
  mqtt_publisher.py      # отправка команд
  mqtt_subscriber.py     # подписчики ACK и state
  ack_store.py           # in-memory хранилище ACK
  device_shadow.py       # кэш состояния устройств
  tests/                 # pytest-набор
`
Дополнительно: config.py читает окружение, httpx.py содержит адаптеры для внешних вызовов, irmware_binaries/ хранит OTA.

## Быстрый старт
`ash
cd server
python -m venv .venv
. .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload
`
После запуска API доступно на http://127.0.0.1:8000, статика — по /static.

## Основные REST-эндпоинты
| Метод | Путь | Назначение | Примечания |
|-------|------|------------|------------|
| GET | / | Отдает index.html из static/ | Используется SPA панели.
| GET | /api/devices | Список устройств с онлайном и настройками | DeviceInfo вычисляет is_online по last_seen < 3 мин.
| POST | /api/device/{device_id}/status | Прием телеметрии и обновление устройства | Создает устройство, если не найдено, логирует сенсоры.
| GET | /api/device/{device_id}/settings | Актуальные настройки полива и света | Создает пустое устройство при первом запросе.
| PUT | /api/device/{device_id}/settings | Обновление таргетной влажности, таймаутов | Требует существующего устройства.
| DELETE | /api/device/{device_id} | Удаление устройства и истории | Чистит sensor_data и watering_logs.
| GET | /api/device/{device_id}/sensor-history | История телеметрии за N часов | Сортировка по 	imestamp.
| GET | /api/device/{device_id}/watering-logs | Журнал поливов за N дней | Возвращает ORM-объекты (JSON сериализация pydantic).
| GET | /api/device/{device_id}/firmware | Проверка наличия обновления | Возвращает irmware_url, если update_available=True.
| POST | /api/upload-firmware | Загрузка бинарника OTA | Сохраняет в irmware_binaries/<version>.bin.
| POST | /api/device/{device_id}/trigger-update | Помечает доступное OTA | Проверяет наличие файла и обновляет запись устройства.
| POST | /api/manual-watering/start | Отправка pump.start в MQTT | Проверяет, не запущен ли уже полив.
| POST | /api/manual-watering/stop | Отправка pump.stop | Возвращает новый correlation_id.
| GET | /api/manual-watering/status | Возвращает сечение DeviceShadowStore | Добавляет offline_reason.
| GET | /api/manual-watering/wait-ack | Long-poll до 15 c ожидания ACK | 408, если таймаут.
| GET | /api/manual-watering/ack | Однократное получение ACK по ID | 404, если запись удалена TTL.
| POST | /_debug/shadow/state | (DEBUG) загрузка тестового state | Доступно при DEBUG=True.

## MQTT-интеграция
- mqtt_publisher.PahoMqttPublisher создает клиента paho-mqtt, публикует команды QoS1.
- Подписчики в mqtt_subscriber.py подключаются к gh/dev/+/ack и gh/dev/+/state, преобразуют payload в Pydantic и кладут в AckStore/DeviceShadowStore.
- AckStore — потокобезопасный словарь с очисткой по TTL (300 секунд), DeviceShadowStore считает оставшееся время полива и флаг онлайна.

## Работа с БД
- pp/core/database.py создаёт engine при старте приложения (create_tables() вызывается в main.py).
- Модели (DeviceDB, SensorDataDB, WateringLogDB) описаны в pp/models/database_models.py.
- Для тестов можно переопределить SQLALCHEMY_DATABASE_URL через переменные окружения.
