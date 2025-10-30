"""Маршрутизация MQTT-сообщений и подписчики сервиса."""

from __future__ import annotations

import logging
from typing import Callable, Optional
from uuid import uuid4

from paho.mqtt.client import Client, MQTT_ERR_SUCCESS

from ack_store import AckStore
from service.mqtt.config import get_mqtt_settings
from service.mqtt.handlers.ack import (
    extract_device_id_from_ack_topic,
    handle_ack_message,
    make_ack_topic_filter,
)
from service.mqtt.handlers.device_state import (
    extract_device_id_from_state_topic,
    handle_state_message,
    make_state_topic_filter,
)
from device_shadow import DeviceShadowStore

__all__ = [
    "MqttStateSubscriber",
    "MqttAckSubscriber",
    "make_state_topic_filter",
    "extract_device_id_from_state_topic",
    "make_ack_topic_filter",
    "extract_device_id_from_ack_topic",
]

logger = logging.getLogger(__name__)


class MqttStateSubscriber:
    """Подписчик retained state, обновляющий DeviceShadowStore."""

    def __init__(
        self,
        store: DeviceShadowStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        self._settings = get_mqtt_settings()
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        settings = self._settings
        client_id = f"{settings.client_id_prefix}-state-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.username:
            client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            client.tls_set()
        return client

    def start(self) -> None:
        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message
        settings = self._settings
        if settings.debug:
            print(
                f"[MQTT DEBUG] (state) пытаемся подключиться к брокеру "
                f"{settings.host}:{settings.port} как {settings.username!r}"
            )
        try:
            logger.info(
                "Подключаемся к MQTT брокеру %s:%s для чтения state",
                settings.host,
                settings.port,
            )
            result = client.connect(settings.host, settings.port)
            if result != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={result}")
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
        settings = self._settings
        if settings.debug:
            print(f"[MQTT DEBUG] (state) on_connect rc={rc}")

        if rc == 0:
            logger.info(
                "Успешное подключение к MQTT (%s:%s) для state, rc=%s",
                settings.host,
                settings.port,
                rc,
            )
            topic_filter = make_state_topic_filter()
            client.subscribe(topic_filter, qos=1)
            if settings.debug:
                print(f"[MQTT DEBUG] (state) подписка на {topic_filter}")
        else:
            logger.error(
                "Не удалось подключиться к MQTT (%s:%s) для state, rc=%s. "
                "Проверьте логин/пароль или ACL.",
                settings.host,
                settings.port,
                rc,
            )
            if settings.debug:
                print(
                    "[MQTT DEBUG] (state) ошибка подключения rc={rc} "
                    "(rc=5 обычно означает неправильные креды)".format(rc=rc)
                )

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        topic = getattr(message, "topic", "")
        payload = getattr(message, "payload", b"")
        handle_state_message(self._settings, self._store, topic, payload)


class MqttAckSubscriber:
    """Подписчик ACK, складывающий ответы в AckStore."""

    def __init__(
        self,
        store: AckStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        self._settings = get_mqtt_settings()
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        settings = self._settings
        client_id = f"{settings.client_id_prefix}-ack-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.username:
            client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            client.tls_set()
        return client

    def start(self) -> None:
        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message
        settings = self._settings
        if settings.debug:
            print(
                f"[MQTT DEBUG] (ack) пытаемся подключиться к брокеру "
                f"{settings.host}:{settings.port} как {settings.username!r}"
            )
        try:
            logger.info(
                "Подключаемся к MQTT брокеру %s:%s для чтения ack",
                settings.host,
                settings.port,
            )
            result = client.connect(settings.host, settings.port)
            if result != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={result}")
            client.loop_start()
            client.subscribe(make_ack_topic_filter(), qos=1)
            self._client = client
            self._running = True
        except Exception as exc:  # pragma: no cover - сеть в тестах не используется
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
        settings = self._settings
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) on_connect rc={rc}")

        if rc == 0:
            logger.info(
                "Успешное подключение к MQTT (%s:%s) для ack, rc=%s",
                settings.host,
                settings.port,
                rc,
            )
            topic_filter = make_ack_topic_filter()
            client.subscribe(topic_filter, qos=1)
            if settings.debug:
                print(f"[MQTT DEBUG] (ack) подписка на {topic_filter}")
        else:
            logger.error(
                "Не удалось подключиться к MQTT (%s:%s) для ack, rc=%s. "
                "Проверьте логин/пароль или ACL.",
                settings.host,
                settings.port,
                rc,
            )
            if settings.debug:
                print(
                    "[MQTT DEBUG] (ack) ошибка подключения rc={rc} "
                    "(rc=5 обычно означает неправильные креды)".format(rc=rc)
                )

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        topic = getattr(message, "topic", "")
        payload = getattr(message, "payload", b"")
        handle_ack_message(self._settings, self._store, topic, payload)
