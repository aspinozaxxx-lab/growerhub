"""Modul upravlyaet zhiznennym ciklom MQTT publishera i subscriberov."""

from __future__ import annotations

import logging
from typing import Callable, Optional

from paho.mqtt.client import Client

from .store import (
    AckStore,
    DeviceShadowStore,
    get_ack_store,
    get_shadow_store,
    init_ack_store,
    init_shadow_store,
    shutdown_ack_store,
    shutdown_shadow_store,
)

from .client import PahoMqttPublisher
from .interfaces import IMqttPublisher
from .router import MqttAckSubscriber, MqttStateSubscriber

# Publikuem API dlya zarubezhnyh komponentov (startup/shutdown)
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
    "init_ack_store",
    "init_shadow_store",
    "get_ack_store",
    "get_shadow_store",
    "shutdown_ack_store",
    "shutdown_shadow_store",
    "init_mqtt_stores",
    "shutdown_mqtt_stores",
    "is_publisher_started",
    "is_state_subscriber_started",
    "is_ack_subscriber_started",
]

# Logger dlya fiksacii usloviy starta i ostanovki
logger = logging.getLogger(__name__)

# Globalnye singltony ispolzuyutsya kak lazivye zavisimosti v FastAPI
_publisher: Optional[PahoMqttPublisher] = None
_publisher_error: Optional[Exception] = None
_state_subscriber: Optional[MqttStateSubscriber] = None
_ack_subscriber: Optional[MqttAckSubscriber] = None
_publisher_started: bool = False
_state_subscriber_started: bool = False
_ack_subscriber_started: bool = False


def init_mqtt_stores() -> None:
    """Garantiruet, chto in-memory storagi dlya ACK i shadow sozdany."""

    init_ack_store()
    init_shadow_store()


def shutdown_mqtt_stores() -> None:
    """Chisto zakryvaet storagi ACK i shadow posle raboty servisa/testov."""

    shutdown_shadow_store()
    shutdown_ack_store()


def init_publisher() -> None:
    """Sozdaet singleton publisher i podklyuchaet ego k brokeru (fonovyj loop)."""

    global _publisher, _publisher_error
    if _publisher:
        return

    publisher = PahoMqttPublisher()
    try:
        publisher.connect()
    except Exception as exc:  # pragma: no cover - slozhno vosproizvesti v testah
        _publisher = None
        _publisher_error = exc
        logger.warning("MQTT publisher initialisation failed: %s", exc)
    else:
        _publisher = publisher
        _publisher_error = None
        global _publisher_started
        _publisher_started = True
        logger.info("mqtt: publisher started")


def shutdown_publisher() -> None:
    """Otklyuchaet publisher i ochishaet singlton pered ostanovkoy prilozheniya."""

    global _publisher, _publisher_error
    if _publisher:
        _publisher.disconnect()
    _publisher = None
    _publisher_error = None
    global _publisher_started
    _publisher_started = False


def get_publisher() -> IMqttPublisher:
    """Vozvrashaet gotovyy publisher ili podnimaet RuntimeError pri oshibke init."""

    if _publisher:
        return _publisher
    if _publisher_error:
        raise RuntimeError("MQTT publisher unavailable") from _publisher_error
    raise RuntimeError("MQTT publisher not initialised")


def init_state_subscriber(
    store: Optional[DeviceShadowStore] = None,
    client_factory: Optional[Callable[[], Client]] = None,
) -> None:
    """Sozdaet singleton state-subscriber s peredannym store ili globalnym."""

    global _state_subscriber
    if _state_subscriber is None:
        if store is None:
            init_shadow_store()
            store = get_shadow_store()
        _state_subscriber = MqttStateSubscriber(store, client_factory=client_factory)


def get_state_subscriber() -> MqttStateSubscriber:
    """Vozvrashaet lazivo sozdannyj state-subscriber ili podnimaet oshibku."""

    if _state_subscriber is None:
        raise RuntimeError("MQTT state subscriber not initialised")
    return _state_subscriber


def start_state_subscriber() -> None:
    """Zapusk loop_start dlya state-subscriber (sozdaya ego pri neobhodimosti)."""

    if _state_subscriber is None:
        init_state_subscriber()
    subscriber = get_state_subscriber()
    subscriber.start()
    if subscriber.is_running():
        global _state_subscriber_started
        _state_subscriber_started = True
        logger.info("mqtt: state-subscriber started")


def stop_state_subscriber() -> None:
    """Ostanavlivaet loop state-subscriber, esli on byl zapushchen."""

    subscriber = _state_subscriber
    if subscriber:
        subscriber.stop()
    global _state_subscriber_started
    _state_subscriber_started = False


def shutdown_state_subscriber() -> None:
    """Polnostyu ochishaet singleton state-subscriber i resetit shadow store."""

    global _state_subscriber
    stop_state_subscriber()
    _state_subscriber = None
    shutdown_shadow_store()


def init_ack_subscriber(
    store: Optional[AckStore] = None,
    client_factory: Optional[Callable[[], Client]] = None,
) -> None:
    """Sozdaet singleton ack-subscriber s peredannym store ili globalnym."""

    global _ack_subscriber
    if _ack_subscriber is None:
        if store is None:
            init_ack_store()
            store = get_ack_store()
        _ack_subscriber = MqttAckSubscriber(store, client_factory=client_factory)


def get_ack_subscriber() -> MqttAckSubscriber:
    """Vozvrashaet lazivo sozdannyj ack-subscriber ili podnimaet oshibku."""

    if _ack_subscriber is None:
        raise RuntimeError("MQTT ack subscriber not initialised")
    return _ack_subscriber


def start_ack_subscriber() -> None:
    """Zapusk loop_start dlya ack-subscriber (sozdaya ego pri neobhodimosti)."""

    if _ack_subscriber is None:
        init_ack_subscriber()
    subscriber = get_ack_subscriber()
    subscriber.start()
    if subscriber.is_running():
        global _ack_subscriber_started
        _ack_subscriber_started = True
        logger.info("mqtt: ack-subscriber started")


def stop_ack_subscriber() -> None:
    """Ostanavlivaet loop ack-subscriber, esli on aktivnyj."""

    subscriber = _ack_subscriber
    if subscriber:
        subscriber.stop()
    global _ack_subscriber_started
    _ack_subscriber_started = False


def shutdown_ack_subscriber() -> None:
    """Polnostyu ochishaet singleton ack-subscriber i resetit ack store."""

    global _ack_subscriber
    stop_ack_subscriber()
    _ack_subscriber = None
    shutdown_ack_store()
    global _ack_subscriber_started
    _ack_subscriber_started = False


def is_publisher_started() -> bool:
    """Vozvrashaet priznak zapuschennogo publishera."""

    return _publisher_started


def is_state_subscriber_started() -> bool:
    """Vozvrashaet priznak zapuschennogo state-subscriber."""

    return _state_subscriber_started


def is_ack_subscriber_started() -> bool:
    """Vozvrashaet priznak zapuschennogo ack-subscriber."""

    return _ack_subscriber_started

