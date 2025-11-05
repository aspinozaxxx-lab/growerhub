"""Modul realizuet MQTT subscriberov dlya sostoyaniy i ACK manual watering."""

from __future__ import annotations

import logging
from typing import Callable, Optional
from uuid import uuid4

from paho.mqtt.client import CallbackAPIVersion, Client, MQTT_ERR_SUCCESS

from .config import get_mqtt_settings
from .handlers.ack import (
    extract_device_id_from_ack_topic,
    handle_ack_message,
    make_ack_topic_filter,
)
from .handlers.device_state import (
    extract_device_id_from_state_topic,
    handle_state_message,
    make_state_topic_filter,
)
from .store import AckStore, DeviceShadowStore

# Publikuem osnovnye klassy i helpery podpischikov
__all__ = [
    "MqttStateSubscriber",
    "MqttAckSubscriber",
    "make_state_topic_filter",
    "extract_device_id_from_state_topic",
    "make_ack_topic_filter",
    "extract_device_id_from_ack_topic",
]

# Logger dlya diagnostiki raboty subscriberov
logger = logging.getLogger(__name__)


class MqttStateSubscriber:
    """Podpischik retained-state topikov, sozhranyaet dannye v DeviceShadowStore."""

    def __init__(
        self,
        store: DeviceShadowStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        """Pripravyet konfiguraciyu i fabriky klienta dlya polucheniya sostoyanii."""

        self._settings = get_mqtt_settings()
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        """Sobiraet paho klient s unikalnym client_id i nastroikami bezopasnosti."""

        settings = self._settings
        client_id = f"{settings.client_id_prefix}-state-{uuid4().hex[:8]}"
        client = Client(client_id=client_id, callback_api_version=CallbackAPIVersion.VERSION2)
        if settings.username:
            client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            client.tls_set()
        return client

    def start(self) -> None:
        """Zapusk podpischika: podklyuchaet klient i nachinaet fonovyj loop (potok)."""

        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message
        settings = self._settings
        if settings.debug:
            print(
                f"[MQTT DEBUG] (state) podklyuchenie k {settings.host}:{settings.port} kak state-subscriber "
                f"pod loginom {settings.username!r}"
            )
        try:
            logger.info(
                "Podklyuchaemsya k MQTT %s:%s kak state subscriber",
                settings.host,
                settings.port,
            )
            result = client.connect(settings.host, settings.port)
            if result != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={result}")
            client.loop_start()
            topic_filter = make_state_topic_filter()
            client.subscribe(topic_filter, qos=1)
            logger.info("mqtt: subscribed to %s qos=1", topic_filter)  # TRANSLIT: fiksiruem podpisku state
            self._client = client
            self._running = True
        except Exception as exc:  # pragma: no cover - kriticheskie oshibki slozhno smodelirovat
            logger.warning("MQTT state subscriber failed to start: %s", exc)
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
            self._client = None
            self._running = False

    def stop(self) -> None:
        """Ostanavlivaet loop i razryvaet soedinenie, esli podpischik aktivnyj."""

        if not self._running or not self._client:
            return
        try:
            self._client.loop_stop()
            self._client.disconnect()
        finally:
            self._client = None
            self._running = False

    def is_running(self) -> bool:
        """Vozvrashaet priznak aktivnogo loop_start (ispolzuetsya v garde)."""

        return self._running

    def _on_connect(self, client: Client, _userdata, _flags, rc):  # type: ignore[override]
        """Obrabatyvaet sobytie podklyucheniya, povtorya podpisku na topic filter."""

        settings = self._settings
        if settings.debug:
            print(f"[MQTT DEBUG] (state) on_connect rc={rc}")

        if rc == 0:
            logger.info(
                "Uspeshno podklyuchilis k MQTT %s:%s kak state subscriber, rc=%s",
                settings.host,
                settings.port,
                rc,
            )
            topic_filter = make_state_topic_filter()
            client.subscribe(topic_filter, qos=1)
            if settings.debug:
                print(f"[MQTT DEBUG] (state) povtornaya podpiska na {topic_filter}")
        else:
            logger.error(
                "State subscriber ne podklyuchilsya k MQTT %s:%s, rc=%s. Proverte ACL i dostup.",
                settings.host,
                settings.port,
                rc,
            )
            if settings.debug:
                print(
                    "[MQTT DEBUG] (state) podklyuchenie ne udalos, "
                    "v vozmozhnom sluchae rc=5 nuzhno proverit dostup"
                )

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        """Proksiruet poluchennoe soobshchenie v handler sostoyaniya."""

        topic = getattr(message, "topic", "")
        payload = getattr(message, "payload", b"")
        handle_state_message(self._settings, self._store, topic, payload)


class MqttAckSubscriber:
    """Podpischik ACK-topikov, zapisывает otvety v AckStore dlya API."""

    def __init__(
        self,
        store: AckStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        """Pripravyet konfiguraciyu i fabriku klienta dlya obrabotki ACK."""

        self._settings = get_mqtt_settings()
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        """Sobiraet paho klient dlya ACK topikov s unikalnym client_id."""

        settings = self._settings
        client_id = f"{settings.client_id_prefix}-ack-{uuid4().hex[:8]}"
        client = Client(client_id=client_id, callback_api_version=CallbackAPIVersion.VERSION2)
        if settings.username:
            client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            client.tls_set()
        return client

    def start(self) -> None:
        """Zapusk podpischika: fonovyj loop i podpiska na ACK topic filter."""

        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message
        settings = self._settings
        if settings.debug:
            print(
                f"[MQTT DEBUG] (ack) podklyuchenie k {settings.host}:{settings.port} kak ack-subscriber "
                f"pod loginom {settings.username!r}"
            )
        try:
            logger.info(
                "Podklyuchaemsya k MQTT %s:%s kak ack subscriber",
                settings.host,
                settings.port,
            )
            result = client.connect(settings.host, settings.port)
            if result != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={result}")
            client.loop_start()
            topic_filter = make_ack_topic_filter()
            client.subscribe(topic_filter, qos=1)
            logger.info("mqtt: subscribed to %s qos=1", topic_filter)  # TRANSLIT: fiksiruem podpisku ack
            self._client = client
            self._running = True
        except Exception as exc:  # pragma: no cover - kriticheskie oshibki slozhno smodelirovat
            logger.warning("MQTT ack subscriber failed to start: %s", exc)
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
            self._client = None
            self._running = False

    def stop(self) -> None:
        """Ostanavlivaet loop i razryvaet soedinenie dlya ack-subscriber."""

        if not self._running or not self._client:
            return
        try:
            self._client.loop_stop()
            self._client.disconnect()
        finally:
            self._client = None
            self._running = False

    def is_running(self) -> bool:
        """Vozvrashaet priznak rabotayuschego loop_start podpischika."""

        return self._running

    def _on_connect(self, client: Client, _userdata, _flags, rc):  # type: ignore[override]
        """Obrabatyvaet podklyuchenie i vozobnovlyaet podpisku na ack topik."""

        settings = self._settings
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) on_connect rc={rc}")

        if rc == 0:
            logger.info(
                "Uspeshno podklyuchilis k MQTT %s:%s kak ack subscriber, rc=%s",
                settings.host,
                settings.port,
                rc,
            )
            topic_filter = make_ack_topic_filter()
            client.subscribe(topic_filter, qos=1)
            if settings.debug:
                print(f"[MQTT DEBUG] (ack) povtornaya podpiska na {topic_filter}")
        else:
            logger.error(
                "Ack subscriber ne podklyuchilsya k MQTT %s:%s, rc=%s. Proverte ACL i dostup.",
                settings.host,
                settings.port,
                rc,
            )
            if settings.debug:
                print(
                    "[MQTT DEBUG] (ack) podklyuchenie ne udalos, "
                    "v vozmozhnom sluchae rc=5 nuzhny korrektnye rekvizity"
                )

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        """Peredayet poluchennoe soobshchenie v handler ACK dlya obrabotki."""

        topic = getattr(message, "topic", "")
        payload = getattr(message, "payload", b"")
        handle_ack_message(self._settings, self._store, topic, payload)

