"""In-memory stores used by the MQTT service (ACKs and device shadow)."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from threading import RLock
from typing import Dict, Optional, Tuple

from .config import get_mqtt_settings

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
    """In-memory storage for ACK messages indexed by correlation_id."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._storage: Dict[str, Tuple[str, "Ack", datetime]] = {}

    def put(self, device_id: str, ack: "Ack") -> None:
        """Store ACK together with device id and insertion timestamp."""

        inserted_at = datetime.utcnow()
        with self._lock:
            self._storage[ack.correlation_id] = (device_id, ack, inserted_at)

    def get(self, correlation_id: str) -> Optional["Ack"]:
        """Return ACK by correlation id or None when missing."""

        with self._lock:
            entry = self._storage.get(correlation_id)
            if not entry:
                return None
            return entry[1]

    def cleanup(self, max_age_seconds: int = 300) -> int:
        """Remove ACK records older than max_age_seconds, return count."""

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
        return removed


_ack_store: Optional[AckStore] = None


def init_ack_store() -> None:
    """Ensure singleton AckStore is created."""

    global _ack_store
    if _ack_store is None:
        _ack_store = AckStore()


def get_ack_store() -> AckStore:
    """Return singleton AckStore or raise if not initialised."""

    if _ack_store is None:
        raise RuntimeError("Ack store not initialised")
    return _ack_store


def shutdown_ack_store() -> None:
    """Dispose singleton AckStore."""

    global _ack_store
    _ack_store = None


class DeviceShadowStore:
    """In-memory storage for device shadow states."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._storage: Dict[str, Tuple["DeviceState", datetime]] = {}

    def update_from_state(self, device_id: str, state: "DeviceState") -> None:
        """Store latest DeviceState and timestamp."""

        with self._lock:
            self._storage[device_id] = (state, datetime.utcnow())

    def get_last_state(self, device_id: str) -> Optional["DeviceState"]:
        """Return last DeviceState for device or None."""

        with self._lock:
            entry = self._storage.get(device_id)
            if not entry:
                return None
            return entry[0]

    def debug_dump(self, device_id: str) -> Optional[dict]:
        """Return raw state snapshot for debug endpoints."""

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
        """Prepare presentation for manual-watering status endpoint."""

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


_shadow_store: Optional[DeviceShadowStore] = None


def init_shadow_store() -> None:
    """Ensure singleton DeviceShadowStore exists."""

    global _shadow_store
    if _shadow_store is None:
        _shadow_store = DeviceShadowStore()


def get_shadow_store() -> DeviceShadowStore:
    """Return shadow store singleton or raise."""

    if _shadow_store is None:
        raise RuntimeError("Device shadow store not initialised")
    return _shadow_store


def shutdown_shadow_store() -> None:
    """Dispose shadow store singleton."""

    global _shadow_store
    _shadow_store = None


def get_settings():  # pragma: no cover - compatibility shim for old patches
    """Return MQTT settings object (legacy alias)."""

    return get_mqtt_settings()


from .serialization import Ack, DeviceState, ManualWateringStatus  # noqa: E402


def _as_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _isoformat_utc(dt: datetime) -> str:
    return _as_utc(dt).replace(microsecond=0).isoformat().replace("+00:00", "Z")
