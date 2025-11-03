"""REST API dlya manualnogo poliva: komandy pump.start, pump.stop i statusy."""

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
    """Vozvrashaet MQTT publisher ili podnimaet HTTP 503 esli nedostupen."""

    try:
        return get_publisher()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="MQTT publisher unavailable") from exc


def get_shadow_dep() -> DeviceShadowStore:
    """Vozvrashaet device shadow store ili podnimaet HTTP 503 esli net singltona."""

    try:
        return get_shadow_store()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Device shadow store unavailable") from exc


def get_ack_dep() -> AckStore:
    """Vozvrashaet AckStore ili podnimaet HTTP 503 pri oshibke."""

    try:
        return get_ack_store()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Ack store unavailable") from exc


if settings.DEBUG:
    @router.get("/_debug/manual-watering/config")
    async def manual_watering_debug_config():
        """Vozvrashaet tekushchie nastroiki MQTT dlya debug endpointa."""

        return {
            "mqtt_host": settings.MQTT_HOST,
            "mqtt_port": settings.MQTT_PORT,
            "mqtt_username": settings.MQTT_USERNAME,
            "mqtt_tls": settings.MQTT_TLS,
            "debug": settings.DEBUG,
        }


class ManualWateringStartIn(BaseModel):
    """Vhodnie dannye dlya starta manualnogo poliva.
    
        device_id - identifikator ustroystva, duration_s - dlitelnost v sekundah."""

    device_id: str
    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class ManualWateringStartOut(BaseModel):
    """Otvet posle starta poliva s novym correlation_id."""

    correlation_id: str


class ManualWateringStopIn(BaseModel):
    """Vhodnie dannye dlya ostanovki poliva."""

    device_id: str


class ManualWateringStopOut(BaseModel):
    """Otvet ostanovki poliva s correlation_id dlya ozhidaniya ack."""

    correlation_id: str


class ManualWateringStatusOut(BaseModel):
    """Model dlya otobrazheniya statusa manualnogo poliva i svyazannyh poley."""

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
    """Dannie dlya debug zapisi sostoyaniya ustroystva v shadow (DEBUG tolko)."""

    device_id: str
    state: DeviceState


class ManualWateringAckOut(BaseModel):
    """Otvetnye dannye ack dlya klienta manualnogo poliva."""

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
    """Zapusk manualnogo poliva, proverki sostoyaniya i otpravka komandy."""

    # Ten - istochnik istiny: esli vidim status running, ustroystvo uzhe zanato i povtornyi start narushit biznes-pravilo.
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
    except Exception as exc:  # pragma: no cover - produkcionnye 502 obrabatyvayutsya infra-strukturoy
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStartOut(correlation_id=correlation_id)


@router.post("/api/manual-watering/stop", response_model=ManualWateringStopOut)
async def manual_watering_stop(
    payload: ManualWateringStopIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStopOut:
    """Ostanovka manualnogo poliva i otpravka komandy stop."""

    # Bez statusa running v teni net smysla otpravlyat stop: operator libo oshibsya, libo komanda uzhe vypolnilas.
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
    except Exception as exc:  # pragma: no cover - produkcionnye 502 obrabatyvayutsya infra-strukturoy
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStopOut(correlation_id=correlation_id)


@router.get("/api/manual-watering/status", response_model=ManualWateringStatusOut)
async def manual_watering_status(
    device_id: str,
    store: DeviceShadowStore = Depends(get_shadow_dep),
) -> ManualWateringStatusOut:
    """Vozvrashaet tekushchee sostoyanie poliva iz shadow po device_id."""

    view = store.get_manual_watering_view(device_id)
    if view is None:
        # Vozvrashaem 200 i zapolnyaem pustoy otvet, chtoby frontend mog zablokirovat knopki
        # do pervogo poyavleniya ustroystva, ne polagayas na 404.
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
    """Vozvrashaet dannye ack iz store po correlation_id."""

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
    """Ozhidanie ack v stile long-poll s pereborom do deadline."""

    # Takoy podhod pozvolyaet frontu vyzvat endpoint srazu posle otpravki komandy i spokoyno dozhdatsya rezultata,
    # ne zanimaya ochered postoyannymi oprosami.
    deadline = time.monotonic() + timeout_s
    # Ogranichenie v 15 sekund uderzhivaet soedinenie v razumnyh ramkah: dalshe frontu proshe povtorit zapros.

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

        # Shag ozhidaniya 0.5 s: dostatochno chasto chtoby ne podvisat, no dostatochno redko chtoby ne zhech CPU vpustuyu.
        await asyncio.sleep(0.5)


if settings.DEBUG:
    @router.post("/_debug/shadow/state")
    async def debug_shadow_state(
        payload: ShadowStateIn,
        store: DeviceShadowStore = Depends(get_shadow_dep),
    ) -> dict:
        """Debug endpoint dlya zapisiy sostoyaniya v shadow v DEBUG rezhime."""

        # V produkshene endpoint vyklyuchen, chtoby nikto ne podmenil tenevoe sostoyanie cherez HTTP.
        store.update_from_state(payload.device_id, payload.state)
        return {"ok": True}

    @router.get("/_debug/manual-watering/snapshot")
    async def debug_manual_watering_snapshot(
        device_id: str,
        store: DeviceShadowStore = Depends(get_shadow_dep),
    ) -> dict:
        """Vozvrashaet syroe i agregirovannoe sostoyanie manualnogo poliva dlya otladki.

        offline_reason pomogaet frontendu prinyat reshenie:
        * None - ustroystvo onlayn, knopki mozhno nazimat.
        * "device_offline" - ustroystvo izvestno, no seychas ne na svyazi.
        * "no_state_yet" - server eshche ne videl state ot ustroystva, zhdem pervogo podklyucheniya.
        """

        raw_data = store.debug_dump(device_id)
        view = store.get_manual_watering_view(device_id)
        return {"raw": raw_data, "view": view}

