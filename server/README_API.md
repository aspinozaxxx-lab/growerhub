# API сервера GrowerHub

## Структура роутеров

Эндпойнты FastAPI теперь разложены по модулям `app/api/routers/`:

| Роутер | Файл | Основные пути |
| --- | --- | --- |
| Manual watering | `app/api/routers/manual_watering.py` | `/api/manual-watering/*`, `_debug/manual-watering/*`, `_debug/shadow/state` |
| Devices | `app/api/routers/devices.py` | `/api/device/{device_id}/status`, `/api/device/{device_id}/settings`, `/api/devices`, `/api/device/{device_id}` |
| History | `app/api/routers/history.py` | `/api/device/{device_id}/sensor-history`, `/api/device/{device_id}/watering-logs` |
| Firmware | `app/api/routers/firmware.py` | `/api/upload-firmware`, `/api/device/{device_id}/firmware`, `/api/device/{device_id}/trigger-update` |

URL и поведение эндпойнтов не менялись при реорганизации.

## Запуск dev-сервера

```bash
cd server
uvicorn app.main:app --reload
```

Статика доступна в каталоге `static/`, прошивки — в `server/firmware_binaries/`.
.mount(`/static`) и .mount(`/firmware`) настроены в `app.main`.

## Настройки и DEBUG

Конфигурация берётся из `server/config.py` (`get_settings()` возвращает dataclass `Settings`). Флаг `DEBUG` управляет доступностью `_debug/*` эндпойнтов manual watering.

## Сопоставление эндпойнтов и файлов

| HTTP метод | Путь | Файл-реализация |
|-------------|------|----------------|
| GET | /api/manual-watering/status | `app/api/routers/manual_watering.py` |
| POST | /api/manual-watering/start | `app/api/routers/manual_watering.py` |
| POST | /api/manual-watering/stop | `app/api/routers/manual_watering.py` |
| GET | /api/device/{device_id}/settings | `app/api/routers/devices.py` |
| PUT | /api/device/{device_id}/settings | `app/api/routers/devices.py` |
| POST | /api/device/{device_id}/status | `app/api/routers/devices.py` |
| GET | /api/device/{device_id}/sensor-history | `app/api/routers/history.py` |
| GET | /api/device/{device_id}/watering-logs | `app/api/routers/history.py` |
| POST | /api/upload-firmware | `app/api/routers/firmware.py` |
| GET | /api/device/{device_id}/firmware | `app/api/routers/firmware.py` |
| POST | /api/device/{device_id}/trigger-update | `app/api/routers/firmware.py` |
| GET | /_debug/manual-watering/* | `app/api/routers/manual_watering.py` (DEBUG only) |

Все эндпойнты зарегистрированы в `app.main` через `include_router(...)`, а теги OpenAPI помогают быстро найти соответствующий раздел в Swagger UI.

