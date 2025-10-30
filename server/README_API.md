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

