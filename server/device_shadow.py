"""Теневое хранилище устройств: отдаёт данные для UI и внутренних сервисов.

Храним состояние в памяти, синхронизируя доступ через RLock, чтобы обрабатывать обновления
по MQTT/HTTP параллельно без гонок.
"""

from __future__ import annotations

from datetime import datetime, timezone
from threading import RLock
from typing import Dict, Optional, Tuple

from config import get_settings
from mqtt_protocol import DeviceState, ManualWateringStatus


def _as_utc(dt: datetime) -> datetime:
    """Приводим datetime к UTC-aware, чтобы не путаться с часовыми поясами."""

    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _isoformat_utc(dt: datetime) -> str:
    """Сериализуем дату/время в ISO 8601 с суффиксом Z (UTC)."""

    return _as_utc(dt).replace(microsecond=0).isoformat().replace("+00:00", "Z")


class DeviceShadowStore:
    """In-memory представление теневого состояния устройства."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._storage: Dict[str, Tuple[DeviceState, datetime]] = {}

    def update_from_state(self, device_id: str, state: DeviceState) -> None:
        """Обновляем состояние устройства, фиксируя момент получения сообщения."""

        with self._lock:
            self._storage[device_id] = (state, datetime.utcnow())

    def get_last_state(self, device_id: str) -> Optional[DeviceState]:
        """Возвращает последнее сохранённое DeviceState или None, если данных нет."""

        with self._lock:
            entry = self._storage.get(device_id)
            if not entry:
                return None
            return entry[0]

    def get_manual_watering_view(
        self,
        device_id: str,
        now: Optional[datetime] = None,
    ) -> Optional[dict]:
        """Формирует нормализованное представление ручного полива для UI."""

        with self._lock:
            entry = self._storage.get(device_id)

        if not entry:
            return None

        state, updated_at = entry
        mw_state = state.manual_watering
        settings = get_settings()

        if isinstance(mw_state.status, ManualWateringStatus):
            status_enum = mw_state.status
            status = status_enum.value
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
        if status_enum is ManualWateringStatus.running and duration_s is not None and started_at is not None:
            current_time = _as_utc(now or datetime.utcnow())
            started_utc = _as_utc(started_at)
            elapsed = int((current_time - started_utc).total_seconds())
            remaining_s = max(0, duration_s - elapsed)

        started_iso = _isoformat_utc(started_at) if started_at else None
        observed_at = _as_utc(updated_at)
        current_utc = _as_utc(now or datetime.utcnow())
        # updated_at хранит последнее полученное retained/state; фронту важно знать и точное время (last_seen_at),
        # и булев флаг is_online. Даже retained после рестарта брокера считается «последним seen», ведь UI видит
        # ровно эти данные.
        is_online = (current_utc - observed_at).total_seconds() <= settings.DEVICE_ONLINE_THRESHOLD_S
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


_store: Optional[DeviceShadowStore] = None


def init_shadow_store() -> None:
    """Инициализирует стор теневых состояний (вызывается при старте приложения)."""

    global _store
    if _store is None:
        _store = DeviceShadowStore()


def shutdown_shadow_store() -> None:
    """Сбрасывает стор (нужно при тестах и при штатном выключении)."""

    global _store
    _store = None


def get_shadow_store() -> DeviceShadowStore:
    """Возвращает текущий стор или выбрасывает RuntimeError, если его не инициализировали."""

    if _store is None:
        raise RuntimeError("Device shadow store not initialised")
    return _store
