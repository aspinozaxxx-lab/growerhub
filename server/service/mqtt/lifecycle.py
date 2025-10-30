"""Жизненный цикл компонентов MQTT-сервиса."""

from __future__ import annotations

import logging
from typing import Callable, Optional

from ack_store import AckStore
from device_shadow import DeviceShadowStore
from paho.mqtt.client import Client

from .client import PahoMqttPublisher
from .interfaces import IMqttPublisher
from .router import MqttAckSubscriber, MqttStateSubscriber

__all__ = [
    "init_publisher",
    "shutdown_publisher",
    "get_publisher",
    "init_state_subscriber",
    "get_state_subscriber",
    "start_state_subscriber",
    "stop_state_subscriber",
    "shutdown_state_subscriber",
    "init_ack_subscriber",
    "get_ack_subscriber",
    "start_ack_subscriber",
    "stop_ack_subscriber",
    "shutdown_ack_subscriber",
]

logger = logging.getLogger(__name__)

_publisher: Optional[PahoMqttPublisher] = None
_publisher_error: Optional[Exception] = None
_state_subscriber: Optional[MqttStateSubscriber] = None
_ack_subscriber: Optional[MqttAckSubscriber] = None


def init_publisher() -> None:
    """Инициализировать singleton-публикатор и установить соединение."""

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
    """Вернуть готовый публикатор или выбросить RuntimeError."""

    if _publisher:
        return _publisher
    if _publisher_error:
        raise RuntimeError("MQTT publisher unavailable") from _publisher_error
    raise RuntimeError("MQTT publisher not initialised")


def init_state_subscriber(
    store: DeviceShadowStore,
    client_factory: Optional[Callable[[], Client]] = None,
) -> None:
    """Создать подписчика state и сохранить singleton."""

    global _state_subscriber
    if _state_subscriber is None:
        _state_subscriber = MqttStateSubscriber(store, client_factory=client_factory)


def get_state_subscriber() -> MqttStateSubscriber:
    if _state_subscriber is None:
        raise RuntimeError("MQTT state subscriber not initialised")
    return _state_subscriber


def start_state_subscriber() -> None:
    get_state_subscriber().start()


def stop_state_subscriber() -> None:
    subscriber = _state_subscriber
    if subscriber:
        subscriber.stop()


def shutdown_state_subscriber() -> None:
    global _state_subscriber
    stop_state_subscriber()
    _state_subscriber = None


def init_ack_subscriber(
    store: AckStore,
    client_factory: Optional[Callable[[], Client]] = None,
) -> None:
    """Создать подписчика ACK и сохранить singleton."""

    global _ack_subscriber
    if _ack_subscriber is None:
        _ack_subscriber = MqttAckSubscriber(store, client_factory=client_factory)


def get_ack_subscriber() -> MqttAckSubscriber:
    if _ack_subscriber is None:
        raise RuntimeError("MQTT ack subscriber not initialised")
    return _ack_subscriber


def start_ack_subscriber() -> None:
    get_ack_subscriber().start()


def stop_ack_subscriber() -> None:
    subscriber = _ack_subscriber
    if subscriber:
        subscriber.stop()


def shutdown_ack_subscriber() -> None:
    global _ack_subscriber
    stop_ack_subscriber()
    _ack_subscriber = None

