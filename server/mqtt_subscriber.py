"""MQTT-подписчики для состояний устройства и ACK-сообщений.

За счёт подписчиков сервер постоянно обновляет теневое состояние и
последние подтверждения команд без участия фронтенда. UI только читает
подготовленные данные через REST.
"""

from __future__ import annotations

import json
import logging
from typing import Callable, Optional
from uuid import uuid4

from paho.mqtt.client import Client, MQTT_ERR_SUCCESS
from pydantic import ValidationError

from ack_store import AckStore
from config import get_settings
from device_shadow import DeviceShadowStore
from mqtt_protocol import Ack, DeviceState

logger = logging.getLogger(__name__)


# --- Общие утилиты для топиков state ---

def make_state_topic_filter() -> str:
    """Возвращает MQTT-фильтр для retained state всех устройств."""

    return "gh/dev/+/state"


def extract_device_id_from_state_topic(topic: str) -> Optional[str]:
    """Извлекает device_id из топика state, иначе возвращает None."""

    parts = topic.split("/")
    if len(parts) != 4:
        return None
    prefix, middle, device_id, suffix = parts
    if prefix != "gh" or middle != "dev" or suffix != "state" or not device_id:
        return None
    return device_id


# --- Общие утилиты для топиков ack ---

def make_ack_topic_filter() -> str:
    """Возвращает MQTT-фильтр для ACK всех устройств."""

    return "gh/dev/+/ack"


def extract_device_id_from_ack_topic(topic: str) -> Optional[str]:
    """Извлекает device_id из топика ack, иначе None."""

    parts = topic.split("/")
    if len(parts) != 4:
        return None
    prefix, middle, device_id, suffix = parts
    if prefix != "gh" or middle != "dev" or suffix != "ack" or not device_id:
        return None
    return device_id


# --- Подписчик state ---

class MqttStateSubscriber:
    """Подписчик на retained state: обновляет DeviceShadowStore.

    Жизненный цикл:
      * start() — создаёт MQTT-клиент, подключается, подписывается и запускает loop.
      * stop()  — аккуратно завершает loop и соединение.
      * on_message() — десериализует DeviceState и кладёт его в стор.

    client_factory позволяет подменить клиента в тестах и работать без сети.
    """

    def __init__(
        self,
        store: DeviceShadowStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        settings = get_settings()
        self._settings = settings
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        settings = self._settings
        client_id = f"{settings.MQTT_CLIENT_ID_PREFIX}-state-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.MQTT_USERNAME:
            client.username_pw_set(settings.MQTT_USERNAME, settings.MQTT_PASSWORD)
        if settings.MQTT_TLS:
            client.tls_set()
        return client

    def start(self) -> None:
        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message

        try:
            rc = client.connect(self._settings.MQTT_HOST, self._settings.MQTT_PORT)
            if rc != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={rc}")
            client.loop_start()
            client.subscribe(make_state_topic_filter(), qos=1)
            self._client = client
            self._running = True
        except Exception as exc:  # pragma: no cover - сеть в тестах не используется
            logger.warning("MQTT state subscriber failed to start: %s", exc)
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
            self._client = None
            self._running = False

    def stop(self) -> None:
        if not self._running or not self._client:
            return
        try:
            self._client.loop_stop()
            self._client.disconnect()
        finally:
            self._client = None
            self._running = False

    def is_running(self) -> bool:
        return self._running

    def _on_connect(self, client: Client, _userdata, _flags, rc):  # type: ignore[override]
        if rc == 0:
            client.subscribe(make_state_topic_filter(), qos=1)
        else:
            logger.warning("MQTT state subscriber connect rc=%s", rc)

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        topic = getattr(message, "topic", "")
        device_id = extract_device_id_from_state_topic(topic)
        if not device_id:
            logger.debug("Ignore MQTT state message with unexpected topic: %s", topic)
            return

        payload = getattr(message, "payload", b"")
        try:
            state = DeviceState.model_validate_json(payload.decode("utf-8"))
        except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
            logger.warning("Failed to parse device state for %s: %s", device_id, exc)
            return

        self._store.update_from_state(device_id, state)


_state_subscriber: Optional[MqttStateSubscriber] = None


def init_state_subscriber(store: DeviceShadowStore) -> None:
    """Создаёт глобальный экземпляр подписчика state."""

    global _state_subscriber
    if _state_subscriber is None:
        _state_subscriber = MqttStateSubscriber(store)


def get_state_subscriber() -> MqttStateSubscriber:
    if _state_subscriber is None:
        raise RuntimeError("MQTT state subscriber not initialised")
    return _state_subscriber


def shutdown_state_subscriber() -> None:
    global _state_subscriber
    _state_subscriber = None


# --- Подписчик ack ---

class MqttAckSubscriber:
    """Подписчик на ACK: хранит последние подтверждения в AckStore.

    ACK-и не retained, поэтому подписчик просто слушает qos=1 и запоминает
    последнее подтверждение по correlation_id.
    """

    def __init__(
        self,
        store: AckStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        settings = get_settings()
        self._settings = settings
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        settings = self._settings
        client_id = f"{settings.MQTT_CLIENT_ID_PREFIX}-ack-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.MQTT_USERNAME:
            client.username_pw_set(settings.MQTT_USERNAME, settings.MQTT_PASSWORD)
        if settings.MQTT_TLS:
            client.tls_set()
        return client

    def start(self) -> None:
        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message

        try:
            rc = client.connect(self._settings.MQTT_HOST, self._settings.MQTT_PORT)
            if rc != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={rc}")
            client.loop_start()
            client.subscribe(make_ack_topic_filter(), qos=1)
            self._client = client
            self._running = True
        except Exception as exc:  # pragma: no cover
            logger.warning("MQTT ack subscriber failed to start: %s", exc)
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
            self._client = None
            self._running = False

    def stop(self) -> None:
        if not self._running or not self._client:
            return
        try:
            self._client.loop_stop()
            self._client.disconnect()
        finally:
            self._client = None
            self._running = False

    def is_running(self) -> bool:
        return self._running

    def _on_connect(self, client: Client, _userdata, _flags, rc):  # type: ignore[override]
        if rc == 0:
            client.subscribe(make_ack_topic_filter(), qos=1)
        else:
            logger.warning("MQTT ack subscriber connect rc=%s", rc)

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        topic = getattr(message, "topic", "")
        device_id = extract_device_id_from_ack_topic(topic)
        if not device_id:
            logger.debug("Ignore MQTT ack message with unexpected topic: %s", topic)
            return

        payload = getattr(message, "payload", b"")
        try:
            ack = Ack.model_validate_json(payload.decode("utf-8"))
        except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
            logger.warning("Failed to parse ack for %s: %s", device_id, exc)
            return

        self._store.put(device_id, ack)


_ack_subscriber: Optional[MqttAckSubscriber] = None


def init_ack_subscriber(store: AckStore) -> None:
    """Создаёт глобальный экземпляр подписчика ACK."""

    global _ack_subscriber
    if _ack_subscriber is None:
        _ack_subscriber = MqttAckSubscriber(store)


def get_ack_subscriber() -> MqttAckSubscriber:
    if _ack_subscriber is None:
        raise RuntimeError("MQTT ack subscriber not initialised")
    return _ack_subscriber


def shutdown_ack_subscriber() -> None:
    global _ack_subscriber
    _ack_subscriber = None
