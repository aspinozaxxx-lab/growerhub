import logging
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.routers import devices as devices_router
from app.api.routers import firmware as firmware_router
from app.api.routers import history as history_router
from app.api.routers import manual_watering as manual_watering_router
from app.core.database import create_tables
from app.mqtt.lifecycle import (
    init_ack_subscriber,
    init_mqtt_stores,
    init_publisher,
    init_state_subscriber,
    shutdown_ack_subscriber,
    shutdown_mqtt_stores,
    shutdown_publisher,
    shutdown_state_subscriber,
    start_ack_subscriber,
    start_state_subscriber,
    stop_ack_subscriber,
    stop_state_subscriber,
)

logger = logging.getLogger(__name__)

# === Базовые пути приложения ===
BASE_DIR = Path(__file__).resolve().parent.parent  # -> ~/growerhub/server/app -> parent -> server
SITE_DIR = (BASE_DIR.parent / "static").resolve()  # -> ~/growerhub/static
FIRMWARE_DIR = BASE_DIR / "firmware_binaries"

# Создаём каталог с прошивками, если его ещё нет
FIRMWARE_DIR.mkdir(exist_ok=True, parents=True)

app = FastAPI(title="GrowerHub")


@app.on_event("startup")
async def _startup_mqtt() -> None:
    # Инициализация БД перенесена в startup для безопасности импортов и тестов
    try:
        create_tables()
    except Exception as exc:  # pragma: no cover - защитный вызов на случай гонок
        logger.warning("create_tables skipped on startup (вероятно, таблицы уже существуют): %s", exc)
    # Настраиваем MQTT-компоненты: сторажи, подписчиков и паблишер
    init_mqtt_stores()
    init_state_subscriber()
    try:
        start_state_subscriber()
    except RuntimeError:
        logger.warning("MQTT state subscriber is not initialised")
    init_ack_subscriber()
    try:
        start_ack_subscriber()
    except RuntimeError:
        logger.warning("MQTT ack subscriber is not initialised")
    init_publisher()


@app.on_event("shutdown")
async def _shutdown_mqtt() -> None:
    # Корректно останавливаем подписчиков, паблишер и очищаем сторажи
    stop_state_subscriber()
    shutdown_state_subscriber()
    stop_ack_subscriber()
    shutdown_ack_subscriber()
    shutdown_publisher()
    shutdown_mqtt_stores()


# === Маршруты для статики и прошивок ===
app.mount("/static", StaticFiles(directory=SITE_DIR), name="static")
app.mount("/firmware", StaticFiles(directory=FIRMWARE_DIR), name="firmware")

app.include_router(manual_watering_router.router, tags=["Manual watering"])
app.include_router(devices_router.router, tags=["Devices"])
app.include_router(history_router.router, tags=["History"])
app.include_router(firmware_router.router, tags=["Firmware"])


@app.get("/")
async def read_root():
    return FileResponse(SITE_DIR / "index.html")
