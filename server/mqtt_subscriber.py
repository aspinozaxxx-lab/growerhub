"""MQTT-подписчик состояний устройств, обновляющий теневой стор.

Отдельный подписчик позволяет асинхронно принимать retained-сообщения
о текущем состоянии устройств (gh/dev/<device_id>/state) и складывать
их в DeviceShadowStore. UI читает эти данные через REST, поэтому
подписчик работает в фоне вместе с FastAPI.
"""

from __future__ import annotations

import json
import logging
from typing import Callable, Optional
from uuid import uuid4

from paho.mqtt.client import Client, MQTT_ERR_SUCCESS

from config import get_settings
from device_shadow import DeviceShadowStore
from mqtt_protocol import DeviceState
from pydantic import ValidationError

logger = logging.getLogger(__name__)


def make_state_topic_filter() -> str:
    """Возвращает MQTT-фильтр для подписки на retained-state всех устройств."""

    return "gh/dev/+/state"


def extract_device_id_from_state_topic(topic: str) -> Optional[str]:
    """Извлекает device_id из топика gh/dev/{device_id}/state, иначе None."""

    parts = topic.split("/")
    if len(parts) != 4:
        return None
    prefix, middle, device_id, suffix = parts
    if prefix != "gh" or middle != "dev" or suffix != "state" or not device_id:
        return None
    return device_id


class MqttStateSubscriber:
    """Подписчик на MQTT state, обновляющий теневой стор.

    Жизненный цикл:
      * start(): создаёт MQTT-клиент, подключается и подписывается на retained-state
      * on_connect(): подтверждает установку соединения и повторяет подписку
      * on_message(): десериализует DeviceState и отправляет в стор
      * stop(): останавливает loop и закрывает соединение

    Параметр client_factory позволяет подменить MQTT-клиент в тестах и
    не зависеть от реальной сети.
    """

    def __init__(
        self,
        store: DeviceShadowStore,
        client_factory: Optional[Callable[[], Client]] = None,
    ) -> None:
        settings = get_settings()
        client_factory = client_factory or self._default_client_factory
        self._settings = settings
        self._store = store
        self._client_factory = client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        """Создаём MQTT-клиент с уникальным client_id на основе настроек."""

        settings = self._settings
        client_id = f"{settings.MQTT_CLIENT_ID_PREFIX}-sub-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.MQTT_USERNAME:
            client.username_pw_set(settings.MQTT_USERNAME, settings.MQTT_PASSWORD)
        if settings.MQTT_TLS:
            client.tls_set()
        return client

    def start(self) -> None:
        """Запускаем подписчика: подключение, запуск loop и подписка."""

        if self._running:
            return

        client = self._client_factory()
        client.on_connect = self._on_connect
        client.on_message = self._on_message

        try:
            result = client.connect(self._settings.MQTT_HOST, self._settings.MQTT_PORT)
            if result != MQTT_ERR_SUCCESS:
                raise RuntimeError(f"MQTT connect returned rc={result}")
            client.loop_start()
            client.subscribe(make_state_topic_filter(), qos=1)
            self._client = client
            self._running = True
        except Exception as exc:  # pragma: no cover - в тестах подменяем фабрику
            logger.warning("MQTT state subscriber failed to start: %s", exc)
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
            self._client = None
            self._running = False

    def stop(self) -> None:
        """Останавливаем loop и закрываем соединение."""

        if not self._running or not self._client:
            return
        try:
            self._client.loop_stop()
            self._client.disconnect()
        finally:
            self._client = None
            self._running = False

    def is_running(self) -> bool:
        """Возвращает True, если подписчик запущен и держит соединение."""

        return self._running

    # --- MQTT callbacks ---

    def _on_connect(self, client: Client, _userdata, flags, rc):  # type: ignore[override]
        """После успешного подключения повторно подписываемся на state."""

        if rc == 0:
            client.subscribe(make_state_topic_filter(), qos=1)
        else:
            logger.warning("MQTT state subscriber connect rc=%s", rc)

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        """Обрабатываем retained state: парсим device_id и сохраняем в стор."""

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


_subscriber: Optional[MqttStateSubscriber] = None


def init_state_subscriber(store: DeviceShadowStore) -> None:
    """Создаём глобальный экземпляр подписчика (по аналогии с паблишером)."""

    global _subscriber
    if _subscriber is None:
        _subscriber = MqttStateSubscriber(store)


def get_state_subscriber() -> MqttStateSubscriber:
    """Возвращает подписчика или поднимает исключение, если не инициализирован."""

    if _subscriber is None:
        raise RuntimeError("MQTT state subscriber not initialised")
    return _subscriber


def shutdown_state_subscriber() -> None:
    """Сбрасывает глобальный экземпляр (используем при остановке приложения)."""

    global _subscriber
    _subscriber = None
