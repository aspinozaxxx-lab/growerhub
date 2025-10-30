"""Потокобезопасное in-memory хранилище ACK-сообщений.

ACK-и важны для UI: фронтенд может быстро показать оператору, принята ли команда,
была ли отвергнута и по какой причине. Хранилище очищается по TTL, чтобы не
накапливать устаревшие подтверждения в памяти.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from threading import RLock
from typing import Dict, Optional, Tuple

from service.mqtt.serialization import Ack


class AckStore:
    """Потокобезопасный стор ACK-сообщений, ключ — correlation_id."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._storage: Dict[str, Tuple[str, Ack, datetime]] = {}

    def put(self, device_id: str, ack: Ack) -> None:
        """Сохраняем ACK для указанного устройства с отметкой времени."""

        inserted_at = datetime.utcnow()
        with self._lock:
            self._storage[ack.correlation_id] = (device_id, ack, inserted_at)

    def get(self, correlation_id: str) -> Optional[Ack]:
        """Возвращает ACK по correlation_id либо None, если данные отсутствуют."""

        with self._lock:
            entry = self._storage.get(correlation_id)
            if not entry:
                return None
            return entry[1]

    def cleanup(self, max_age_seconds: int = 300) -> int:
        """Удаляет ACK-и старше max_age_seconds и возвращает количество удалённых записей."""

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
    """Создаёт глобальный экземпляр AckStore — вызывается на старте приложения."""

    global _ack_store
    if _ack_store is None:
        _ack_store = AckStore()


def get_ack_store() -> AckStore:
    """Возвращает текущий AckStore либо поднимает RuntimeError, если он не инициализирован."""

    if _ack_store is None:
        raise RuntimeError("Ack store not initialised")
    return _ack_store


def shutdown_ack_store() -> None:
    """Сбрасывает глобальный AckStore (используется при остановке приложения)."""

    global _ack_store
    _ack_store = None
