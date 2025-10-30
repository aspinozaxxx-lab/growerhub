"""Фасад жизненного цикла MQTT-публикатора."""

from __future__ import annotations

import logging
from typing import Optional

from .client import PahoMqttPublisher
from .interfaces import IMqttPublisher

__all__ = ["init_publisher", "shutdown_publisher", "get_publisher"]

logger = logging.getLogger(__name__)

_publisher: Optional[PahoMqttPublisher] = None
_publisher_error: Optional[Exception] = None


def init_publisher() -> None:
    """Поднять singleton-публикатор и подключиться к брокеру."""

    global _publisher, _publisher_error
    if _publisher:
        return

    publisher = PahoMqttPublisher()
    try:
        publisher.connect()
    except Exception as exc:  # pragma: no cover - путь с логированием
        _publisher = None
        _publisher_error = exc
        logger.warning("MQTT publisher initialisation failed: %s", exc)
    else:
        _publisher = publisher
        _publisher_error = None


def shutdown_publisher() -> None:
    """Остановить и очистить публикатор."""

    global _publisher, _publisher_error
    if _publisher:
        _publisher.disconnect()
    _publisher = None
    _publisher_error = None


def get_publisher() -> IMqttPublisher:
    """Вернуть текущий публикатор либо выбросить RuntimeError."""

    if _publisher:
        return _publisher
    if _publisher_error:
        raise RuntimeError("MQTT publisher unavailable") from _publisher_error
    raise RuntimeError("MQTT publisher not initialised")

