import logging
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from starlette.routing import Mount

from app.fastapi.routers import devices as devices_router
from app.fastapi.routers import firmware as firmware_router
from app.fastapi.routers import history as history_router
from app.fastapi.routers import manual_watering as manual_watering_router
from app.core.database import create_tables
from app.mqtt.lifecycle import (
    init_ack_subscriber,
    init_mqtt_stores,
    init_publisher,
    init_state_subscriber,
    start_ack_cleanup_loop,
    shutdown_ack_subscriber,
    shutdown_mqtt_stores,
    shutdown_publisher,
    shutdown_state_subscriber,
    start_ack_subscriber,
    start_state_subscriber,
    stop_ack_cleanup_loop,
    stop_ack_subscriber,
    stop_state_subscriber,
)
from config import Settings, get_settings

logger = logging.getLogger(__name__)

# === Базовые пути приложения ===
BASE_DIR = Path(__file__).resolve().parent.parent  # -> ~/growerhub/server/app -> parent -> server
SITE_DIR = (BASE_DIR.parent / "static").resolve()  # -> ~/growerhub/static

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
    await start_ack_cleanup_loop()


@app.on_event("shutdown")
async def _shutdown_mqtt() -> None:
    # Корректно останавливаем подписчиков, паблишер и очищаем сторажи
    stop_state_subscriber()
    shutdown_state_subscriber()
    stop_ack_subscriber()
    shutdown_ack_subscriber()
    shutdown_publisher()
    shutdown_mqtt_stores()
    await stop_ack_cleanup_loop()


# === Маршруты для статики и прошивок ===
app.mount("/static", StaticFiles(directory=SITE_DIR), name="static")

app.include_router(manual_watering_router.router, tags=["Manual watering"])
app.include_router(devices_router.router, tags=["Devices"])
app.include_router(history_router.router, tags=["History"])
app.include_router(firmware_router.router, tags=["Firmware"])


def remount_firmware_static(settings: Settings | None = None) -> None:
    """Perepodvesit' /firmware s uchetom aktualnyh nastroek."""

    cfg = settings or get_settings()
    _mount_firmware_static(app, cfg)


def _mount_firmware_static(app_obj: FastAPI, settings: Settings) -> None:
    firmware_dir = Path(settings.FIRMWARE_BINARIES_DIR)
    firmware_dir.mkdir(exist_ok=True, parents=True)
    app_obj.router.routes = [
        route
        for route in app_obj.router.routes
        if not (isinstance(route, Mount) and route.path == "/firmware")
    ]
    app_obj.mount(
        "/firmware",
        StaticFiles(directory=str(firmware_dir)),
        name="firmware",
    )


@app.get("/")
async def read_root():
    return FileResponse(SITE_DIR / "index.html")


remount_firmware_static()
