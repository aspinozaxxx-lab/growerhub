"""REST API для ручного управления насосом: команды pump.start, pump.stop и статус."""

from __future__ import annotations

from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, conint

from device_shadow import DeviceShadowStore, get_shadow_store
from mqtt_protocol import CmdPumpStart, CmdPumpStop, CommandType, DeviceState
from mqtt_publisher import IMqttPublisher, get_publisher

router = APIRouter()


def get_mqtt_dep() -> IMqttPublisher:
    """Возвращает MQTT-паблишер или отвечает 503, если сервис недоступен."""

    try:
        return get_publisher()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="MQTT publisher unavailable") from exc


def get_shadow_dep() -> DeviceShadowStore:
    """Возвращает стор теневых состояний или 503, если он не инициализирован."""

    try:
        return get_shadow_store()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Device shadow store unavailable") from exc


class ManualWateringStartIn(BaseModel):
    """Входные данные для запуска ручного полива.

    device_id — идентификатор устройства, duration_s — длительность полива в секундах.
    """

    device_id: str
    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class ManualWateringStartOut(BaseModel):
    """Ответ на запуск полива.

    Возвращаем только correlation_id, чтобы фронтенд мог отслеживать подтверждения.
    """

    correlation_id: str


class ManualWateringStopIn(BaseModel):
    """Входные данные для остановки полива."""

    device_id: str


class ManualWateringStopOut(BaseModel):
    """Ответ на остановку полива (возвращаем correlation_id для ack)."""

    correlation_id: str


class ManualWateringStatusOut(BaseModel):
    """Нормализованное состояние ручного полива для фронтенда.

    Структура совпадает с результатом get_manual_watering_view в сторах.
    """

    status: str
    duration_s: int | None
    started_at: str | None
    remaining_s: int | None
    correlation_id: str | None
    updated_at: str
    source: str


class ShadowStateIn(BaseModel):
    """Входные данные для отладочной записи теневого состояния (используется в тестах)."""

    device_id: str
    state: DeviceState


@router.post("/api/manual-watering/start", response_model=ManualWateringStartOut)
async def manual_watering_start(
    payload: ManualWateringStartIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
) -> ManualWateringStartOut:
    """Запускаем ручной полив, публикуя pump.start в MQTT."""

    correlation_id = uuid4().hex
    cmd = CmdPumpStart(
        type=CommandType.pump_start.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
        duration_s=payload.duration_s,
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - отлаживаем через 502
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStartOut(correlation_id=correlation_id)


@router.post("/api/manual-watering/stop", response_model=ManualWateringStopOut)
async def manual_watering_stop(
    payload: ManualWateringStopIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
) -> ManualWateringStopOut:
    """Останавливаем ручной полив, публикуя pump.stop и возвращая correlation_id."""

    correlation_id = uuid4().hex
    cmd = CmdPumpStop(
        type=CommandType.pump_stop.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - отлаживаем через 502
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStopOut(correlation_id=correlation_id)


@router.get("/api/manual-watering/status", response_model=ManualWateringStatusOut)
async def manual_watering_status(
    device_id: str,
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStatusOut:
    """Возвращает нормализованный статус полива для прогресс-бара на фронтенде."""

    view = store.get_manual_watering_view(device_id)
    if view is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Нет данных о состоянии устройства")
    return ManualWateringStatusOut(**view)


@router.post("/_debug/shadow/state")
async def debug_shadow_state(
    payload: ShadowStateIn,
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> dict:
    """Отладочный эндпоинт для тестов: сохраняет состояние в теневом сторе."""

    store.update_from_state(payload.device_id, payload.state)
    return {"ok": True}
