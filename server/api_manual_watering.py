"""REST API для ручного управления насосом: команды pump.start, pump.stop и статус."""

from __future__ import annotations

import asyncio
import time
from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, conint

from ack_store import AckStore, get_ack_store
from device_shadow import DeviceShadowStore, get_shadow_store
from mqtt_protocol import Ack, CmdPumpStart, CmdPumpStop, CommandType, DeviceState
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


def get_ack_dep() -> AckStore:
    """Возвращает AckStore или 503, если он недоступен."""

    try:
        return get_ack_store()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Ack store unavailable") from exc


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


class ManualWateringAckOut(BaseModel):
    """Ответ при запросе ACK — фронту важен итог выполнения команды."""

    correlation_id: str
    result: str
    reason: str | None = None
    status: str | None = None


@router.post("/api/manual-watering/start", response_model=ManualWateringStartOut)
async def manual_watering_start(
    payload: ManualWateringStartIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStartOut:
    """Запускает ручной полив, но сперва консультируется с тенью устройства, защищая от «двойного клика» и лишних команд."""

    # Тень — источник истины: если видим статус running, значит устройство уже занято и повторный старт нарушит бизнес-правило.
    view = store.get_manual_watering_view(payload.device_id)
    if view is not None and view.get("status") == "running":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Полив уже выполняется — повторный запуск запрещён.")

    correlation_id = uuid4().hex
    cmd = CmdPumpStart(
        type=CommandType.pump_start.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
        duration_s=payload.duration_s,
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - ���������� ����� 502
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStartOut(correlation_id=correlation_id)


@router.post("/api/manual-watering/stop", response_model=ManualWateringStopOut)
async def manual_watering_stop(
    payload: ManualWateringStopIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStopOut:
    """Останавливает ручной полив только когда он действительно идёт, избавляя MQTT и устройство от лишних stop-команд."""

    # Без статуса running в тени нет смысла отправлять стоп: оператор либо ошибся, либо команда уже выполнилась.
    view = store.get_manual_watering_view(payload.device_id)
    if view is None or view.get("status") != "running":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Полив не выполняется — останавливать нечего.")

    correlation_id = uuid4().hex
    cmd = CmdPumpStop(
        type=CommandType.pump_stop.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - ���������� ����� 502
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


@router.get("/api/manual-watering/ack", response_model=ManualWateringAckOut)
async def manual_watering_ack(
    correlation_id: str,
    store: AckStore = Depends(get_ack_dep),
) -> ManualWateringAckOut:
    """Возвращает ACK по correlation_id: фронт показывает оператору результат команды."""

    ack = store.get(correlation_id)
    if ack is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ACK ещё не получен или удалён по TTL")

    return ManualWateringAckOut(
        correlation_id=ack.correlation_id,
        result=ack.result.value if hasattr(ack.result, "value") else ack.result,
        reason=ack.reason,
        status=ack.status.value if ack.status is not None and hasattr(ack.status, "value") else (ack.status if ack.status is not None else None),
    )


@router.get("/api/manual-watering/wait-ack", response_model=ManualWateringAckOut)
async def manual_watering_wait_ack(
    correlation_id: str,
    timeout_s: conint(ge=1, le=15) = 5,
    store: AckStore = Depends(get_ack_dep),
) -> ManualWateringAckOut:
    """Ждёт появления ACK в сторе мягким long-poll-ом, чтобы фронтенд не спамил короткими запросами каждую секунду."""

    # Такой подход позволяет фронту вызвать эндпоинт сразу после отправки команды и спокойно дождаться результата,
    # не занимая очередь постоянными опросами.
    deadline = time.monotonic() + timeout_s
    # Ограничение в 15 секунд удерживает соединение в разумных рамках: дальше фронту проще повторить запрос.

    while True:
        ack = store.get(correlation_id)
        if ack is not None:
            return ManualWateringAckOut(
                correlation_id=ack.correlation_id,
                result=ack.result.value if hasattr(ack.result, "value") else ack.result,
                reason=ack.reason,
                status=ack.status.value if ack.status is not None and hasattr(ack.status, "value") else (ack.status if ack.status is not None else None),
            )

        if time.monotonic() >= deadline:
            raise HTTPException(status_code=status.HTTP_408_REQUEST_TIMEOUT, detail="ACK не получен в заданное время")

        # Шаг ожидания 0.5 с: достаточно часто чтобы не подвисать, но достаточно редко чтобы не жечь CPU впустую.
        await asyncio.sleep(0.5)


@router.post("/_debug/shadow/state")
async def debug_shadow_state(
    payload: ShadowStateIn,
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> dict:
    """Отладочный эндпоинт для тестов: сохраняет состояние в теневом сторе."""

    store.update_from_state(payload.device_id, payload.state)
    return {"ok": True}
