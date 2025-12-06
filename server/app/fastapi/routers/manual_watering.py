"""REST API dlya manualnogo poliva: komandy pump.start, pump.stop i statusy."""

from __future__ import annotations

import asyncio
import math
import time
import json
from datetime import datetime, timezone
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field, conint
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.mqtt.store import AckStore, get_ack_store
from config import get_settings
from app.mqtt.store import DeviceShadowStore, get_shadow_store
from app.mqtt.interfaces import IMqttPublisher
from app.mqtt.lifecycle import get_publisher
from app.mqtt.serialization import (
    Ack,
    CmdPumpStart,
    CmdPumpStop,
    CmdReboot,
    CommandType,
    DeviceState,
    ManualWateringState,
    ManualWateringStatus,
)
from app.core.database import get_db
from app.models.database_models import (
    DeviceDB,
    DeviceStateLastDB,
    PlantDB,
    PlantDeviceDB,
    PlantJournalEntryDB,
    PlantJournalWateringDetailsDB,
    UserDB,
)
from app.repositories.state_repo import DeviceStateLastRepository

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


def _ensure_device_access(device_id: str, db: Session, current_user: UserDB) -> DeviceDB:
    """Translitem: proverka prav dostupa k ustrojstvu dlya poliva."""

    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    if current_user.role != "admin" and device.user_id != current_user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="nedostatochno prav dlya etogo ustrojstva")

    return device


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
    duration_s: conint(ge=1, le=3600) | None = None  # type: ignore[valid-type]
    water_volume_l: float | None = Field(default=None, gt=0)
    ph: float | None = None
    fertilizers_per_liter: str | None = None


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
    duration_s: int | None = None
    duration: int | None = None
    started_at: str | None = None
    start_time: str | None = None
    remaining_s: int | None = None
    correlation_id: str | None = None
    updated_at: str | None = None
    last_seen_at: str | None = None
    is_online: bool
    offline_reason: str | None = None
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


class ManualRebootIn(BaseModel):
    """Vhodnye dannye dlya zaprosa reboot komandy."""

    device_id: str = Field(..., min_length=1)  # identifikator ustroystva dlya publikacii komandy


class ManualRebootOut(BaseModel):
    """Otvet posle publikacii reboot komandy v MQTT."""

    correlation_id: str  # korelaciya dlya sledyashego ozhidaniya ACK
    message: str  # kratkij tekst ob uspeshnoj otpravke komandy


@router.post("/api/manual-watering/start", response_model=ManualWateringStartOut)
async def manual_watering_start(
    payload: ManualWateringStartIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    store: DeviceShadowStore = Depends(get_shadow_dep),
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
) -> ManualWateringStartOut:
    """Zapusk manualnogo poliva, proverki sostoyaniya i otpravka komandy."""

    device = _ensure_device_access(payload.device_id, db, current_user)
    owner_user_id = device.user_id or current_user.id
    plants = _get_linked_plants(db, device, owner_user_id)
    if not plants:
        # Translitem: zapreshchaem poliv esli ustroystvo ne privyazano k rasteniyam.
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Устройство не привязано ни k odnomu rasteniyu")

    # Ten - istochnik istiny: esli vidim status running, ustroystvo uzhe zanato i povtornyi start narushit biznes-pravilo.
    view = store.get_manual_watering_view(payload.device_id)
    if view is not None and view.get("status") == "running":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="РџРѕР»РёРІ СѓР¶Рµ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ вЂ” РїРѕРІС‚РѕСЂРЅС‹Р№ Р·Р°РїСѓСЃРє Р·Р°РїСЂРµС‰С‘РЅ.")

    duration_s = payload.duration_s
    calculated_water_used = None
    if payload.water_volume_l is not None and device.watering_speed_lph and device.watering_speed_lph > 0:
        seconds = payload.water_volume_l / device.watering_speed_lph * 3600
        duration_s = max(1, int(math.ceil(seconds)))
        calculated_water_used = payload.water_volume_l

    if duration_s is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="ukazhite water_volume_l ili duration_s dlya starta poliva",
        )
    water_volume_l = _resolve_water_volume_l(device, duration_s, calculated_water_used, payload)

    correlation_id = uuid4().hex
    started_at = datetime.utcnow()
    cmd = CmdPumpStart(
        type=CommandType.pump_start.value,
        correlation_id=correlation_id,
        ts=started_at,
        duration_s=duration_s,
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - produkcionnye 502 obrabatyvayutsya infra-strukturoy
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    shadow_state = DeviceState(
        manual_watering=ManualWateringState(
            status=ManualWateringStatus.running,
            duration_s=duration_s,
            started_at=started_at,
            remaining_s=duration_s,
            correlation_id=correlation_id,
        )
    )
    store.update_from_state(payload.device_id, shadow_state)
    shadow_state_payload = shadow_state.model_dump(mode="json")
    existing_state = (
        db.query(DeviceStateLastDB).filter(DeviceStateLastDB.device_id == payload.device_id).first()
    )
    payload_json = json.dumps(shadow_state_payload, ensure_ascii=False)
    if existing_state:
        existing_state.state_json = payload_json
        existing_state.updated_at = started_at
    else:
        db.add(
            DeviceStateLastDB(
                device_id=payload.device_id,
                state_json=payload_json,
                updated_at=started_at,
            )
        )

    _create_watering_journal_and_details(
        db,
        device=device,
        plants=plants,
        current_user=current_user,
        started_at=started_at,
        duration_s=duration_s,
        water_volume_l=water_volume_l,
        payload=payload,
    )
    db.commit()

    return ManualWateringStartOut(correlation_id=correlation_id)


@router.post("/api/manual-watering/stop", response_model=ManualWateringStopOut)
async def manual_watering_stop(
    payload: ManualWateringStopIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    store: DeviceShadowStore = Depends(get_shadow_dep),
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
) -> ManualWateringStopOut:
    """Ostanovka manualnogo poliva i otpravka komandy stop."""

    _ensure_device_access(payload.device_id, db, current_user)

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


@router.post("/api/manual-watering/reboot", response_model=ManualRebootOut)
async def manual_watering_reboot(
    payload: ManualRebootIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
) -> ManualRebootOut:
    """Publikuet komandу reboot dlya ukazannogo ustroystva."""

    _ensure_device_access(payload.device_id, db, current_user)

    correlation_id = uuid4().hex
    issued_at = int(time.time())
    cmd = CmdReboot(
        correlation_id=correlation_id,
        issued_at=issued_at,
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:  # pragma: no cover - proizvodstvennye 502 obrabatyvayutsya vysshe
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual reboot command") from exc

    return ManualRebootOut(correlation_id=correlation_id, message="reboot command published")


@router.get("/api/manual-watering/status", response_model=ManualWateringStatusOut)
async def manual_watering_status(
    device_id: str,
    store: DeviceShadowStore = Depends(get_shadow_dep),
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
) -> ManualWateringStatusOut:
    """Vozvrashaet tekushchee sostoyanie poliva iz shadow po device_id."""

    _ensure_device_access(device_id, db, current_user)

    # Algoritm: s nachala probuem poluchit polnyy nabor poley iz teni, a pri otsutstvii ili probelakh dozapolnyaem dannymi iz Bazy.
    cfg = get_settings()
    threshold = getattr(cfg, "DEVICE_ONLINE_THRESHOLD_S", None)
    if threshold is None:
        threshold = getattr(cfg, "device_online_threshold_s", None)
    if threshold is None:
        threshold = 180

    view = store.get_manual_watering_view(device_id)
    if view is None:
        # Translitem: esli ten pustaya, probuem vosstanovit state iz BD kak osnovnogo hranilishcha.
        state_repo = DeviceStateLastRepository()
        stored_state = state_repo.get_state(device_id)
        if stored_state is not None:
            state_payload = stored_state["state"]
            updated_at = stored_state["updated_at"]
            manual = state_payload.get("manual_watering", {})
            status_value = manual.get("status", "idle")
            duration_s = manual.get("duration_s")
            started_at_raw = manual.get("started_at")
            started_at = _isoformat_utc(started_at_raw) if isinstance(started_at_raw, datetime) else started_at_raw
            correlation_id = manual.get("correlation_id")
            remaining_s = manual.get("remaining_s")
            last_seen_iso = _isoformat_utc(updated_at) if updated_at else None
            offline_reason = None
            is_online = False
            if updated_at is not None:
                now_utc = datetime.utcnow().replace(tzinfo=timezone.utc)
                is_online = (now_utc - _as_utc(updated_at)).total_seconds() <= threshold
                offline_reason = None if is_online else "device_offline"
            if not is_online and offline_reason is None:
                offline_reason = "device_offline"
            return ManualWateringStatusOut(
                status=status_value,
                duration_s=duration_s,
                duration=duration_s,
                started_at=started_at,
                start_time=started_at,
                remaining_s=remaining_s,
                correlation_id=correlation_id,
                updated_at=last_seen_iso,
                last_seen_at=last_seen_iso,
                is_online=is_online,
                offline_reason=offline_reason,
                source="db_state",
            )

        # Vetka fallback: teni net, proveryaem, est li ustroystvo v Baze.
        device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
        if device is None:
            # Vozvrashaem 200 i zapolnyaem pustoy otvet, chtoby frontend mog zablokirovat knopki
            # do pervogo poyavleniya ustroystva, ne polagayas na 404.
            return ManualWateringStatusOut(
                status="idle",
                duration_s=None,
                duration=0,
                started_at=None,
                start_time=None,
                remaining_s=None,
                correlation_id=None,
                updated_at=None,
                last_seen_at=None,
                is_online=False,
                offline_reason="no_state_yet",
                source="fallback",
            )

        last_seen = device.last_seen
        last_seen_iso = _isoformat_utc(last_seen) if last_seen else None
        now_utc = datetime.utcnow().replace(tzinfo=timezone.utc)
        is_online = False
        if last_seen is not None:
            is_online = (now_utc - _as_utc(last_seen)).total_seconds() <= threshold
        offline_reason = None if is_online else "device_offline"
        return ManualWateringStatusOut(
            status="idle",
            duration_s=None,
            duration=0,
            started_at=None,
            start_time=None,
            remaining_s=None,
            correlation_id=None,
            updated_at=last_seen_iso,
            last_seen_at=last_seen_iso,
            is_online=is_online,
            offline_reason=offline_reason,
            source="db_fallback",
        )

    enriched = dict(view)
    has_last_seen = "last_seen_at" in enriched and enriched["last_seen_at"] is not None
    has_is_online = "is_online" in enriched and enriched["is_online"] is not None
    if not has_last_seen or not has_is_online:
        # Dopolnyaem ten dannymi iz Bazy, chtoby offline/online indikator i last_seen sovpadali s osnovnym UI.
        device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
        if device is not None:
            last_seen = device.last_seen
            if not has_last_seen:
                enriched["last_seen_at"] = _isoformat_utc(last_seen) if last_seen else None
                if "updated_at" not in enriched or enriched["updated_at"] is None:
                    enriched["updated_at"] = enriched["last_seen_at"]
            if not has_is_online:
                if last_seen is not None:
                    now_utc = datetime.utcnow().replace(tzinfo=timezone.utc)
                    enriched["is_online"] = (now_utc - _as_utc(last_seen)).total_seconds() <= threshold
                else:
                    enriched["is_online"] = False
            if "updated_at" not in enriched:
                enriched["updated_at"] = enriched.get("last_seen_at")
        else:
            if not has_last_seen:
                enriched["last_seen_at"] = None
            if not has_is_online:
                enriched["is_online"] = False
            enriched.setdefault("updated_at", enriched.get("last_seen_at"))

    offline_reason = None if enriched.get("is_online") else "device_offline"
    enriched.setdefault("updated_at", None)
    enriched.setdefault("last_seen_at", None)
    enriched.setdefault("start_time", enriched.get("started_at"))
    enriched.setdefault("duration", enriched.get("duration_s"))
    enriched["offline_reason"] = offline_reason
    return ManualWateringStatusOut(**enriched)


def _get_linked_plants(db: Session, device: DeviceDB, owner_user_id: int | None) -> list[PlantDB]:
    """Translitem: vozvrashaet spisok rastenij, privyazannyh k ustrojstvu."""

    query = (
        db.query(PlantDB)
        .join(PlantDeviceDB, PlantDeviceDB.plant_id == PlantDB.id)
        .filter(PlantDeviceDB.device_id == device.id)
    )
    if owner_user_id:
        query = query.filter(PlantDB.user_id == owner_user_id)
    return query.all()


def _resolve_water_volume_l(
    device: DeviceDB,
    duration_s: int,
    water_used: float | None,
    payload: ManualWateringStartIn,
) -> float:
    """Translitem: opredelyaet obem vody na osnove payload i skorosti nasosa."""

    if water_used is not None:
        return water_used
    if payload.water_volume_l is not None:
        return payload.water_volume_l
    if device.watering_speed_lph:
        return device.watering_speed_lph * duration_s / 3600.0
    return 0.0


def _create_watering_journal_and_details(
    db: Session,
    device: DeviceDB,
    plants: list[PlantDB],
    current_user: UserDB,
    started_at: datetime,
    duration_s: int,
    water_volume_l: float,
    payload: ManualWateringStartIn,
) -> None:
    """Translitem: sohranyaet zapisi zhurnala poliva i detali po kazhdomu rasteniyu."""

    if not plants:
        return

    owner_user_id = device.user_id or current_user.id
    text = _build_watering_journal_text(duration_s, water_volume_l, payload)
    for plant in plants:
        entry = PlantJournalEntryDB(
            plant_id=plant.id,
            user_id=owner_user_id,
            type="watering",
            text=text,
            event_at=started_at,
        )
        db.add(entry)
        details = PlantJournalWateringDetailsDB(
            journal_entry=entry,
            water_volume_l=water_volume_l,
            duration_s=duration_s,
            ph=payload.ph,
            fertilizers_per_liter=payload.fertilizers_per_liter,
        )
        db.add(details)


def _build_watering_journal_text(duration_s: int, water_volume_l: float, payload: ManualWateringStartIn) -> str:
    """Translitem: sobiraet opisanie poliva dlya zhurnala rastenij."""

    parts: list[str] = []
    parts.append(f"obem_vody={water_volume_l:.2f}l")
    parts.append(f"dlitelnost={duration_s}s")
    if payload.ph is not None:
        parts.append(f"ph={payload.ph}")
    if payload.fertilizers_per_liter:
        parts.append(f"udobreniya_na_litr={payload.fertilizers_per_liter} (udobreniya ukazany na litr)")
    return "; ".join(parts)


@router.get("/api/manual-watering/ack", response_model=ManualWateringAckOut)
async def manual_watering_ack(
    correlation_id: str,
    store: AckStore = Depends(get_ack_dep),
    current_user: UserDB = Depends(get_current_user),
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
    current_user: UserDB = Depends(get_current_user),
) -> ManualWateringAckOut:
    """Ozhidanie ack v stile long-poll s pereborom do deadline."""

    # Takoy podhod pozvolyaet frontu vyzvat endpoint srazu posle otpravki komandy i spokoyno dozhdatsya rezultata,
    # ne zanimaya ochered postoyannymi oprosami.
    deadline = time.monotonic() + timeout_s

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
            raise HTTPException(status_code=status.HTTP_408_REQUEST_TIMEOUT, detail="ACK ??%?' ???? ?????>?????? ?? ???????????????? ?????????")

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

def _as_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _isoformat_utc(dt: datetime | None) -> str | None:
    if dt is None:
        return None
    value = _as_utc(dt).replace(microsecond=0)
    return value.isoformat().replace("+00:00", "Z")




