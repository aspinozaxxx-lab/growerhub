"""REST API РґР»СЏ СЂСѓС‡РЅРѕРіРѕ СѓРїСЂР°РІР»РµРЅРёСЏ РЅР°СЃРѕСЃРѕРј: РєРѕРјР°РЅРґС‹ pump.start, pump.stop Рё СЃС‚Р°С‚СѓСЃ."""

from __future__ import annotations

import asyncio
import time
from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, conint

from app.mqtt.store import AckStore, get_ack_store
from config import get_settings
from app.mqtt.store import DeviceShadowStore, get_shadow_store
from app.mqtt.interfaces import IMqttPublisher
from app.mqtt.lifecycle import get_publisher
from app.mqtt.serialization import Ack, CmdPumpStart, CmdPumpStop, CommandType, DeviceState

router = APIRouter()

settings = get_settings()


def get_mqtt_dep() -> IMqttPublisher:
    """Р’РѕР·РІСЂР°С‰Р°РµС‚ MQTT-РїР°Р±Р»РёС€РµСЂ РёР»Рё РѕС‚РІРµС‡Р°РµС‚ 503, РµСЃР»Рё СЃРµСЂРІРёСЃ РЅРµРґРѕСЃС‚СѓРїРµРЅ."""

    try:
        return get_publisher()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="MQTT publisher unavailable") from exc


def get_shadow_dep() -> DeviceShadowStore:
    """Р’РѕР·РІСЂР°С‰Р°РµС‚ СЃС‚РѕСЂ С‚РµРЅРµРІС‹С… СЃРѕСЃС‚РѕСЏРЅРёР№ РёР»Рё 503, РµСЃР»Рё РѕРЅ РЅРµ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ."""

    try:
        return get_shadow_store()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Device shadow store unavailable") from exc


def get_ack_dep() -> AckStore:
    """Р’РѕР·РІСЂР°С‰Р°РµС‚ AckStore РёР»Рё 503, РµСЃР»Рё РѕРЅ РЅРµРґРѕСЃС‚СѓРїРµРЅ."""

    try:
        return get_ack_store()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Ack store unavailable") from exc


if settings.DEBUG:
    @router.get("/_debug/manual-watering/config")
    async def manual_watering_debug_config():
        """Р­С‚Р° СЂСѓС‡РєР° СЃСѓС‰РµСЃС‚РІСѓРµС‚ С‚РѕР»СЊРєРѕ РІ РѕС‚Р»Р°РґРѕС‡РЅРѕРј СЂРµР¶РёРјРµ, РІ РїСЂРѕРґРµ РїСЂРё DEBUG=False РѕРЅР° РЅРµ СЃРѕР·РґР°С‘С‚СЃСЏ."""

        return {
            "mqtt_host": settings.MQTT_HOST,
            "mqtt_port": settings.MQTT_PORT,
            "mqtt_username": settings.MQTT_USERNAME,
            "mqtt_tls": settings.MQTT_TLS,
            "debug": settings.DEBUG,
        }


class ManualWateringStartIn(BaseModel):
    """Р’С…РѕРґРЅС‹Рµ РґР°РЅРЅС‹Рµ РґР»СЏ Р·Р°РїСѓСЃРєР° СЂСѓС‡РЅРѕРіРѕ РїРѕР»РёРІР°.

    device_id вЂ” РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ СѓСЃС‚СЂРѕР№СЃС‚РІР°, duration_s вЂ” РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ РїРѕР»РёРІР° РІ СЃРµРєСѓРЅРґР°С….
    """

    device_id: str
    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class ManualWateringStartOut(BaseModel):
    """РћС‚РІРµС‚ РЅР° Р·Р°РїСѓСЃРє РїРѕР»РёРІР°.

    Р’РѕР·РІСЂР°С‰Р°РµРј С‚РѕР»СЊРєРѕ correlation_id, С‡С‚РѕР±С‹ С„СЂРѕРЅС‚РµРЅРґ РјРѕРі РѕС‚СЃР»РµР¶РёРІР°С‚СЊ РїРѕРґС‚РІРµСЂР¶РґРµРЅРёСЏ.
    """

    correlation_id: str


class ManualWateringStopIn(BaseModel):
    """Р’С…РѕРґРЅС‹Рµ РґР°РЅРЅС‹Рµ РґР»СЏ РѕСЃС‚Р°РЅРѕРІРєРё РїРѕР»РёРІР°."""

    device_id: str


class ManualWateringStopOut(BaseModel):
    """РћС‚РІРµС‚ РЅР° РѕСЃС‚Р°РЅРѕРІРєСѓ РїРѕР»РёРІР° (РІРѕР·РІСЂР°С‰Р°РµРј correlation_id РґР»СЏ ack)."""

    correlation_id: str


class ManualWateringStatusOut(BaseModel):
    """РќРѕСЂРјР°Р»РёР·РѕРІР°РЅРЅС‹Р№ РѕС‚РІРµС‚ РґР»СЏ С„СЂРѕРЅС‚РµРЅРґР°: РґР°РЅРЅС‹Рµ РґР»СЏ РєРЅРѕРїРѕРє Рё РїСЂРѕРіСЂРµСЃСЃР°."""

    status: str
    duration_s: int | None
    started_at: str | None
    remaining_s: int | None
    correlation_id: str | None
    updated_at: str | None
    last_seen_at: str | None
    is_online: bool
    offline_reason: str | None = None
    source: str
    source: str


class ShadowStateIn(BaseModel):
    """Р’С…РѕРґРЅС‹Рµ РґР°РЅРЅС‹Рµ РґР»СЏ РѕС‚Р»Р°РґРѕС‡РЅРѕР№ Р·Р°РїРёСЃРё С‚РµРЅРµРІРѕРіРѕ СЃРѕСЃС‚РѕСЏРЅРёСЏ (РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІ С‚РµСЃС‚Р°С…)."""

    device_id: str
    state: DeviceState


class ManualWateringAckOut(BaseModel):
    """РћС‚РІРµС‚ РїСЂРё Р·Р°РїСЂРѕСЃРµ ACK вЂ” С„СЂРѕРЅС‚Сѓ РІР°Р¶РµРЅ РёС‚РѕРі РІС‹РїРѕР»РЅРµРЅРёСЏ РєРѕРјР°РЅРґС‹."""

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
    """Р—Р°РїСѓСЃРєР°РµС‚ СЂСѓС‡РЅРѕР№ РїРѕР»РёРІ, РЅРѕ СЃРїРµСЂРІР° РєРѕРЅСЃСѓР»СЊС‚РёСЂСѓРµС‚СЃСЏ СЃ С‚РµРЅСЊСЋ СѓСЃС‚СЂРѕР№СЃС‚РІР°, Р·Р°С‰РёС‰Р°СЏ РѕС‚ В«РґРІРѕР№РЅРѕРіРѕ РєР»РёРєР°В» Рё Р»РёС€РЅРёС… РєРѕРјР°РЅРґ."""

    # РўРµРЅСЊ вЂ” РёСЃС‚РѕС‡РЅРёРє РёСЃС‚РёРЅС‹: РµСЃР»Рё РІРёРґРёРј СЃС‚Р°С‚СѓСЃ running, Р·РЅР°С‡РёС‚ СѓСЃС‚СЂРѕР№СЃС‚РІРѕ СѓР¶Рµ Р·Р°РЅСЏС‚Рѕ Рё РїРѕРІС‚РѕСЂРЅС‹Р№ СЃС‚Р°СЂС‚ РЅР°СЂСѓС€РёС‚ Р±РёР·РЅРµСЃ-РїСЂР°РІРёР»Рѕ.
    view = store.get_manual_watering_view(payload.device_id)
    if view is not None and view.get("status") == "running":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="РџРѕР»РёРІ СѓР¶Рµ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ вЂ” РїРѕРІС‚РѕСЂРЅС‹Р№ Р·Р°РїСѓСЃРє Р·Р°РїСЂРµС‰С‘РЅ.")

    correlation_id = uuid4().hex
    cmd = CmdPumpStart(
        type=CommandType.pump_start.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
        duration_s=payload.duration_s,
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - пїЅпїЅпїЅпїЅпїЅпїЅпїЅпїЅпїЅпїЅ пїЅпїЅпїЅпїЅпїЅ 502
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStartOut(correlation_id=correlation_id)


@router.post("/api/manual-watering/stop", response_model=ManualWateringStopOut)
async def manual_watering_stop(
    payload: ManualWateringStopIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStopOut:
    """РћСЃС‚Р°РЅР°РІР»РёРІР°РµС‚ СЂСѓС‡РЅРѕР№ РїРѕР»РёРІ С‚РѕР»СЊРєРѕ РєРѕРіРґР° РѕРЅ РґРµР№СЃС‚РІРёС‚РµР»СЊРЅРѕ РёРґС‘С‚, РёР·Р±Р°РІР»СЏСЏ MQTT Рё СѓСЃС‚СЂРѕР№СЃС‚РІРѕ РѕС‚ Р»РёС€РЅРёС… stop-РєРѕРјР°РЅРґ."""

    # Р‘РµР· СЃС‚Р°С‚СѓСЃР° running РІ С‚РµРЅРё РЅРµС‚ СЃРјС‹СЃР»Р° РѕС‚РїСЂР°РІР»СЏС‚СЊ СЃС‚РѕРї: РѕРїРµСЂР°С‚РѕСЂ Р»РёР±Рѕ РѕС€РёР±СЃСЏ, Р»РёР±Рѕ РєРѕРјР°РЅРґР° СѓР¶Рµ РІС‹РїРѕР»РЅРёР»Р°СЃСЊ.
    view = store.get_manual_watering_view(payload.device_id)
    if view is None or view.get("status") != "running":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="РџРѕР»РёРІ РЅРµ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ вЂ” РѕСЃС‚Р°РЅР°РІР»РёРІР°С‚СЊ РЅРµС‡РµРіРѕ.")

    correlation_id = uuid4().hex
    cmd = CmdPumpStop(
        type=CommandType.pump_stop.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - пїЅпїЅпїЅпїЅпїЅпїЅпїЅпїЅпїЅпїЅ пїЅпїЅпїЅпїЅпїЅ 502
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStopOut(correlation_id=correlation_id)


@router.get("/api/manual-watering/status", response_model=ManualWateringStatusOut)
async def manual_watering_status(
    device_id: str,
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStatusOut:
    """Р’РѕР·РІСЂР°С‰Р°РµС‚ РґР°РЅРЅС‹Рµ РґР»СЏ РїСЂРѕРіСЂРµСЃСЃ-Р±Р°СЂР° Рё СЃС‚Р°С‚СѓСЃР° СѓСЃС‚СЂРѕР№СЃС‚РІР°.

    offline_reason РїРѕРјРѕРіР°РµС‚ С„СЂРѕРЅС‚РµРЅРґСѓ РїРѕРЅСЏС‚СЊ, РјРѕР¶РЅРѕ Р»Рё СѓРїСЂР°РІР»СЏС‚СЊ СѓСЃС‚СЂРѕР№СЃС‚РІРѕРј:
      * None вЂ” СѓСЃС‚СЂРѕР№СЃС‚РІРѕ РѕРЅР»Р°Р№РЅ, РєРЅРѕРїРєРё РґРѕСЃС‚СѓРїРЅС‹Рµ.
      * "device_offline" вЂ” СѓСЃС‚СЂРѕР№СЃС‚РІРѕ РёР·РІРµСЃС‚РЅРѕ, РЅРѕ РІСЂРµРјРµРЅРЅРѕ РЅРµРґРѕСЃС‚СѓРїРЅРѕ.
      * "no_state_yet" вЂ” СЃРµСЂРІРµСЂ РµС‰С‘ РЅРё СЂР°Р·Сѓ РЅРµ РІРёРґРµР» state РѕС‚ СѓСЃС‚СЂРѕР№СЃС‚РІР°.
    """

    view = store.get_manual_watering_view(device_id)
    if view is None:
        # Р’РѕР·РІСЂР°С‰Р°РµРј 200 Рё Р·Р°РїРѕР»РЅСЏРµРј РїСѓСЃС‚РѕР№ РѕС‚РІРµС‚, С‡С‚РѕР±С‹ С„СЂРѕРЅС‚РµРЅРґ РјРѕРі Р·Р°Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ РєРЅРѕРїРєРё
        # РґРѕ РїРµСЂРІРѕРіРѕ РїРѕСЏРІР»РµРЅРёСЏ СѓСЃС‚СЂРѕР№СЃС‚РІР°, РЅРµ РїРѕР»Р°РіР°СЏСЃСЊ РЅР° 404.
        return ManualWateringStatusOut(
            status="idle",
            duration_s=None,
            started_at=None,
            remaining_s=None,
            correlation_id=None,
            updated_at=None,
            last_seen_at=None,
            is_online=False,
            offline_reason="no_state_yet",
            source="fallback",
        )

    offline_reason = None if view.get("is_online") else "device_offline"
    enriched = dict(view)
    enriched.setdefault("updated_at", None)
    enriched.setdefault("last_seen_at", None)
    enriched["offline_reason"] = offline_reason
    return ManualWateringStatusOut(**enriched)


@router.get("/api/manual-watering/ack", response_model=ManualWateringAckOut)
async def manual_watering_ack(
    correlation_id: str,
    store: AckStore = Depends(get_ack_dep),
) -> ManualWateringAckOut:
    """Р’РѕР·РІСЂР°С‰Р°РµС‚ ACK РїРѕ correlation_id: С„СЂРѕРЅС‚ РїРѕРєР°Р·С‹РІР°РµС‚ РѕРїРµСЂР°С‚РѕСЂСѓ СЂРµР·СѓР»СЊС‚Р°С‚ РєРѕРјР°РЅРґС‹."""

    ack = store.get(correlation_id)
    if ack is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ACK РµС‰С‘ РЅРµ РїРѕР»СѓС‡РµРЅ РёР»Рё СѓРґР°Р»С‘РЅ РїРѕ TTL")

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
    """Р–РґС‘С‚ РїРѕСЏРІР»РµРЅРёСЏ ACK РІ СЃС‚РѕСЂРµ РјСЏРіРєРёРј long-poll-РѕРј, С‡С‚РѕР±С‹ С„СЂРѕРЅС‚РµРЅРґ РЅРµ СЃРїР°РјРёР» РєРѕСЂРѕС‚РєРёРјРё Р·Р°РїСЂРѕСЃР°РјРё РєР°Р¶РґСѓСЋ СЃРµРєСѓРЅРґСѓ."""

    # РўР°РєРѕР№ РїРѕРґС…РѕРґ РїРѕР·РІРѕР»СЏРµС‚ С„СЂРѕРЅС‚Сѓ РІС‹Р·РІР°С‚СЊ СЌРЅРґРїРѕРёРЅС‚ СЃСЂР°Р·Сѓ РїРѕСЃР»Рµ РѕС‚РїСЂР°РІРєРё РєРѕРјР°РЅРґС‹ Рё СЃРїРѕРєРѕР№РЅРѕ РґРѕР¶РґР°С‚СЊСЃСЏ СЂРµР·СѓР»СЊС‚Р°С‚Р°,
    # РЅРµ Р·Р°РЅРёРјР°СЏ РѕС‡РµСЂРµРґСЊ РїРѕСЃС‚РѕСЏРЅРЅС‹РјРё РѕРїСЂРѕСЃР°РјРё.
    deadline = time.monotonic() + timeout_s
    # РћРіСЂР°РЅРёС‡РµРЅРёРµ РІ 15 СЃРµРєСѓРЅРґ СѓРґРµСЂР¶РёРІР°РµС‚ СЃРѕРµРґРёРЅРµРЅРёРµ РІ СЂР°Р·СѓРјРЅС‹С… СЂР°РјРєР°С…: РґР°Р»СЊС€Рµ С„СЂРѕРЅС‚Сѓ РїСЂРѕС‰Рµ РїРѕРІС‚РѕСЂРёС‚СЊ Р·Р°РїСЂРѕСЃ.

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
            raise HTTPException(status_code=status.HTTP_408_REQUEST_TIMEOUT, detail="ACK РЅРµ РїРѕР»СѓС‡РµРЅ РІ Р·Р°РґР°РЅРЅРѕРµ РІСЂРµРјСЏ")

        # РЁР°Рі РѕР¶РёРґР°РЅРёСЏ 0.5 СЃ: РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ С‡Р°СЃС‚Рѕ С‡С‚РѕР±С‹ РЅРµ РїРѕРґРІРёСЃР°С‚СЊ, РЅРѕ РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ СЂРµРґРєРѕ С‡С‚РѕР±С‹ РЅРµ Р¶РµС‡СЊ CPU РІРїСѓСЃС‚СѓСЋ.
        await asyncio.sleep(0.5)


if settings.DEBUG:
    @router.post("/_debug/shadow/state")
    async def debug_shadow_state(
        payload: ShadowStateIn,
        store: DeviceShadowStore = Depends(get_shadow_dep),
    ) -> dict:
        """РћС‚Р»Р°РґРѕС‡РЅС‹Р№ СЌРЅРґРїРѕРёРЅС‚: РЅР°РїСЂСЏРјСѓСЋ РїРёС€РµС‚ СЃРѕСЃС‚РѕСЏРЅРёРµ СѓСЃС‚СЂРѕР№СЃС‚РІР° РІ СЃС‚РѕСЂ (С‚РѕР»СЊРєРѕ РґР»СЏ С‚РµСЃС‚РѕРІ/Р»РѕРєР°Р»СЊРЅРѕР№ РѕС‚Р»Р°РґРєРё)."""

        # Р’ РїСЂРѕРґР°РєС€РµРЅРµ СЌРЅРґРїРѕРёРЅС‚ РІС‹РєР»СЋС‡РµРЅ, С‡С‚РѕР±С‹ РЅРёРєС‚Рѕ РЅРµ РїРѕРґРјРµРЅРёР» С‚РµРЅРµРІРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ С‡РµСЂРµР· HTTP.
        store.update_from_state(payload.device_id, payload.state)
        return {"ok": True}

    @router.get("/_debug/manual-watering/snapshot")
    async def debug_manual_watering_snapshot(
        device_id: str,
        store: DeviceShadowStore = Depends(get_shadow_dep),
    ) -> dict:
        """РћС‚Р»Р°РґРѕС‡РЅС‹Р№ СЃРЅРёРјРѕРє СЃС‚РѕСЂР°: РїРѕРјРѕРіР°РµС‚ РїРѕСЃРјРѕС‚СЂРµС‚СЊ СЃС‹СЂС‹Рµ РґР°РЅРЅС‹Рµ Рё РІС‹С‡РёСЃР»РµРЅРЅРѕРµ РїСЂРµРґСЃС‚Р°РІР»РµРЅРёРµ.

        РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РІ РѕС‚Р»Р°РґРѕС‡РЅРѕРј РѕРєСЂСѓР¶РµРЅРёРё Рё РЅРёРєРѕРіРґР° РЅРµ РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РґРѕСЃС‚СѓРїРµРЅ РІ РїСЂРѕРґР°РєС€РµРЅРµ.
        """

        raw_data = store.debug_dump(device_id)
        view = store.get_manual_watering_view(device_id)
        return {"raw": raw_data, "view": view}

    """Р’РѕР·РІСЂР°С‰Р°РµС‚ РґР°РЅРЅС‹Рµ РґР»СЏ РїСЂРѕРіСЂРµСЃСЃ-Р±Р°СЂР° Рё СЃС‚Р°С‚СѓСЃР° СѓСЃС‚СЂРѕР№СЃС‚РІР°.

    offline_reason РїРѕРјРѕРіР°РµС‚ С„СЂРѕРЅС‚РµРЅРґСѓ РїСЂРёРЅСЏС‚СЊ СЂРµС€РµРЅРёРµ:
      * None вЂ” СѓСЃС‚СЂРѕР№СЃС‚РІРѕ РѕРЅР»Р°Р№РЅ, РєРЅРѕРїРєРё РјРѕР¶РЅРѕ РЅР°Р¶РёРјР°С‚СЊ.
      * "device_offline" вЂ” СѓСЃС‚СЂРѕР№СЃС‚РІРѕ РёР·РІРµСЃС‚РЅРѕ, РЅРѕ СЃРµР№С‡Р°СЃ РЅРµ РЅР° СЃРІСЏР·Рё.
      * "no_state_yet" вЂ” СЃРµСЂРІРµСЂ РµС‰С‘ РЅРµ РІРёРґРµР» state РѕС‚ СѓСЃС‚СЂРѕР№СЃС‚РІР°, Р¶РґС‘Рј РїРµСЂРІРѕРіРѕ РїРѕРґРєР»СЋС‡РµРЅРёСЏ.
    """
