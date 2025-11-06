"""Modul hranit in-memory storagi ACK i shadow dlya MQTT integracii API."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from threading import RLock
from typing import Dict, Optional, Tuple

from .config import get_mqtt_settings

logger = logging.getLogger(__name__)
try:
    if get_mqtt_settings().debug:
        logger.setLevel(logging.DEBUG)
except Exception:
    pass

# Publikuem obshchie klassy i helpery dlya drugih modulей
__all__ = [
    "AckStore",
    "DeviceShadowStore",
    "init_ack_store",
    "get_ack_store",
    "shutdown_ack_store",
    "init_shadow_store",
    "get_shadow_store",
    "shutdown_shadow_store",
    "get_settings",
]


class AckStore:
    """Potokobezopasnyy sklad ACK-soobshcheniy po correlation_id."""

    def __init__(self) -> None:
        """Inicializiruet pamyat s RLock dlya bezopasnogo dostupa iz raznyh potokov."""

        self._lock = RLock()
        self._storage: Dict[str, Tuple[str, "Ack", datetime]] = {}

    def put(self, device_id: str, ack: "Ack") -> None:
        """Zapisyvaet ACK s privyazkoy k ustroystvu i metke vremeni."""

        inserted_at = datetime.utcnow()
        logger.debug(
            "[ACKDBG] ack-store put correlation_id=%s device_id=%s inserted_at=%s",
            ack.correlation_id,
            device_id,
            inserted_at.isoformat(),
        )
        with self._lock:
            self._storage[ack.correlation_id] = (device_id, ack, inserted_at)

    def get(self, correlation_id: str) -> Optional["Ack"]:
        """Vozvrashaet ACK po correlation_id ili None, esli on ne nayden."""

        with self._lock:
            entry = self._storage.get(correlation_id)
            if not entry:
                logger.debug(
                    "[ACKDBG] ack-store get correlation_id=%s found=%s",
                    correlation_id,
                    False,
                )
                return None
            logger.debug(
                "[ACKDBG] ack-store get correlation_id=%s found=%s",
                correlation_id,
                True,
            )
            return entry[1]

    def cleanup(self, max_age_seconds: int = 300) -> int:
        """Udalyayet ustarevshie ACK starshe max_age_seconds i vozvrashaet kolichestvo."""

        with self._lock:
            before_count = len(self._storage)
        threshold = datetime.utcnow() - timedelta(seconds=max_age_seconds)
        removed = 0
        with self._lock:
            keys_to_remove = [
                correlation_id
                for correlation_id, (_device_id, _ack, inserted_at) in self._storage.items()
                if inserted_at < threshold
            ]
            for correlation_id in keys_to_remove:
                self._storage.pop(correlation_id, None)
                removed += 1
            after_count = len(self._storage)
        logger.debug(
            "[ACKDBG] ack-store cleanup before=%s after=%s ttl_s=%s",
            before_count,
            after_count,
            max_age_seconds,
        )
        return removed


# Singleton dlya obshchego dostupа k AckStore
_ack_store: Optional[AckStore] = None


def init_ack_store() -> None:
    """Sozdaet globalnyy AckStore, esli on eshche ne initsializirovan."""

    global _ack_store
    if _ack_store is None:
        _ack_store = AckStore()


def get_ack_store() -> AckStore:
    """Vozvrashaet globalnyy AckStore ili podnimaet oshibku esli ego net."""

    if _ack_store is None:
        raise RuntimeError("Ack store not initialised")
    return _ack_store


def shutdown_ack_store() -> None:
    """Sbrosyvaet singleton AckStore dlya chistoy zaversheniya testov/servisa."""

    global _ack_store
    _ack_store = None


class DeviceShadowStore:
    """Potokobezopasnyy sklad device shadow sostoyanii s RLock."""

    def __init__(self) -> None:
        """Inicializiruet pamat i blokirovku dlya sostoyanii ustroystv."""

        self._lock = RLock()
        self._storage: Dict[str, Tuple["DeviceState", datetime]] = {}

    def update_from_state(self, device_id: str, state: "DeviceState") -> None:
        """Sohranyaet poslednee sostoyanie ustroystva i vremya obnovleniya."""

        with self._lock:
            self._storage[device_id] = (state, datetime.utcnow())

    def get_last_state(self, device_id: str) -> Optional["DeviceState"]:
        """Vozvrashaet poslednee sostoyanie ustroystva ili None, esli dannyh net."""

        with self._lock:
            entry = self._storage.get(device_id)
            if not entry:
                return None
            return entry[0]

    def debug_dump(self, device_id: str) -> Optional[dict]:
        """Formiruet syroe sostoyanie dlya debug-endpointov (JSON + vremya)."""

        with self._lock:
            entry = self._storage.get(device_id)
            if not entry:
                return None
            state, updated_at = entry
        return {
            "state": state.model_dump(mode="json"),
            "updated_at": _isoformat_utc(updated_at),
        }

    def get_manual_watering_view(
        self,
        device_id: str,
        now: Optional[datetime] = None,
    ) -> Optional[dict]:
        """Sobiraet prezentacionnye dannye dlya REST-endpointa statusa poliva."""

        with self._lock:
            entry = self._storage.get(device_id)

        if not entry:
            return None

        state, updated_at = entry
        mw_state = state.manual_watering
        mqtt_settings = get_settings()

        if hasattr(mw_state.status, "value"):
            status_enum = mw_state.status
            status = mw_state.status.value
        else:
            status = str(mw_state.status)
            try:
                status_enum = ManualWateringStatus(status)
            except ValueError:
                status_enum = None
        duration_s = mw_state.duration_s
        started_at = mw_state.started_at
        correlation_id = mw_state.correlation_id

        remaining_s: Optional[int] = None
        if (
            status_enum is ManualWateringStatus.running
            and duration_s is not None
            and started_at is not None
        ):
            current_time = _as_utc(now or datetime.utcnow())
            started_utc = _as_utc(started_at)
            elapsed = int((current_time - started_utc).total_seconds())
            remaining_s = max(0, duration_s - elapsed)

        started_iso = _isoformat_utc(started_at) if started_at else None
        observed_at = _as_utc(updated_at)
        current_utc = _as_utc(now or datetime.utcnow())
        threshold = getattr(
            mqtt_settings,
            "device_online_threshold_s",
            getattr(mqtt_settings, "DEVICE_ONLINE_THRESHOLD_S", 60),
        )
        is_online = (current_utc - observed_at).total_seconds() <= threshold
        last_seen_iso = _isoformat_utc(observed_at)

        return {
            "status": status,
            "duration_s": duration_s,
            "started_at": started_iso,
            "remaining_s": remaining_s,
            "correlation_id": correlation_id,
            "updated_at": last_seen_iso,
            "last_seen_at": last_seen_iso,
            "is_online": is_online,
            "source": "calculated",
        }


# Singleton dlya obshchego dostupа k DeviceShadowStore
_shadow_store: Optional[DeviceShadowStore] = None


def init_shadow_store() -> None:
    """Sozdaet globalnyy DeviceShadowStore, esli on ne byl sozdan ran'she."""

    global _shadow_store
    if _shadow_store is None:
        _shadow_store = DeviceShadowStore()


def get_shadow_store() -> DeviceShadowStore:
    """Vozvrashaet globalnyy shadow store ili podnimaet oshibku pri otsutstvii."""

    if _shadow_store is None:
        raise RuntimeError("Device shadow store not initialised")
    return _shadow_store


def shutdown_shadow_store() -> None:
    """Sbrosyvaet singleton DeviceShadowStore dlya chistoy ostanovki servisa."""

    global _shadow_store
    _shadow_store = None


def get_settings():  # pragma: no cover - compatibility shim for old patches
    """Vozvrashaet obekt nastroek MQTT (legacy alias dlya starogo koda)."""

    return get_mqtt_settings()


from .serialization import Ack, DeviceState, ManualWateringStatus  # noqa: E402


def _as_utc(dt: datetime) -> datetime:
    """Privodit datetime k UTC s sohraneniem informacii o vremeni."""

    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _isoformat_utc(dt: datetime) -> str:
    """Prevrashchaet datetime v stroku ISO-8601 bez mikrosekund i so znakom Z."""

    return _as_utc(dt).replace(microsecond=0).isoformat().replace("+00:00", "Z")

