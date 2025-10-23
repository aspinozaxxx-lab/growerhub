"""Теневое хранилище последних состояний устройств для построения UI-прогресса.

Храним данные в памяти, обеспечивая потокобезопасный доступ через RLock,
так как обновления могут приходить из MQTT/HTTP параллельно запросам от UI.
"""

from __future__ import annotations

from datetime import datetime, timezone
from threading import RLock
from typing import Dict, Optional, Tuple

from mqtt_protocol import DeviceState, ManualWateringStatus


def _as_utc(dt: datetime) -> datetime:
    """Преобразуем произвольный datetime в UTC-aware для корректных расчетов."""

    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _isoformat_utc(dt: datetime) -> str:
    """Сериализуем дату/время в ISO 8601 с явным указанием UTC."""

    return _as_utc(dt).replace(microsecond=0).isoformat().replace("+00:00", "Z")


class DeviceShadowStore:
    """Потокобезопасное in-memory хранилище теневых состояний устройств."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._storage: Dict[str, Tuple[DeviceState, datetime]] = {}

    def update_from_state(self, device_id: str, state: DeviceState) -> None:
        """Сохраняем последнее состояние, сохраняем timestamp обновления."""

        with self._lock:
            self._storage[device_id] = (state, datetime.utcnow())

    def get_last_state(self, device_id: str) -> Optional[DeviceState]:
        """Возвращаем последнее DeviceState либо None, если данных нет."""

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
        """Возвращает нормализованное состояние ручного полива для UI.

        Здесь рассчитываем остаток времени (remaining_s) на сервере, чтобы
        фронтенд мог строить прогресс-бар без отдельных формул.
        """

        with self._lock:
            entry = self._storage.get(device_id)

        if not entry:
            return None

        state, updated_at = entry
        mw_state = state.manual_watering

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

        return {
            "status": status,
            "duration_s": duration_s,
            "started_at": started_iso,
            "remaining_s": remaining_s,
            "correlation_id": correlation_id,
            "updated_at": _isoformat_utc(updated_at),
            "source": "calculated",
        }


_store: Optional[DeviceShadowStore] = None


def init_shadow_store() -> None:
    """Инициализируем синглтон стора (вызывается на старте приложения)."""

    global _store
    if _store is None:
        _store = DeviceShadowStore()


def shutdown_shadow_store() -> None:
    """Очищаем ссылку на стор (на будущее, если потребуется сбросить состояние)."""

    global _store
    _store = None


def get_shadow_store() -> DeviceShadowStore:
    """Возвращает активный стор либо поднимает ошибку, если он не инициализирован."""

    if _store is None:
        raise RuntimeError("Device shadow store not initialised")
    return _store
