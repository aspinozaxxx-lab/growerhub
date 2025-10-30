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
from device_shadow import DeviceShadowStore
from service.mqtt.config import get_mqtt_settings
from service.mqtt.serialization import Ack, DeviceState

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
        settings = get_mqtt_settings()
        self._settings = settings
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        settings = self._settings
        client_id = f"{settings.client_id_prefix}-state-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.username:
            # Передаём брокеру логин/пароль, иначе mosquitto отвечает rc=5 (Not authorized) и соединение не устанавливается.
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
            # Печатаем напрямую в stdout, чтобы systemd/uvicorn гарантированно передал эти строки в journalctl.
            print(
                f"[MQTT DEBUG] (state) Пытаемся подключиться к брокеру "
                f"{settings.host}:{settings.port} как пользователь {settings.username!r}"
            )

        try:
            logger.info(
                "Подключаемся к MQTT брокеру %s:%s для чтения state",
                self._settings.host,
                self._settings.port,
            )
            rc = client.connect(self._settings.host, self._settings.port)
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
        settings = self._settings
        if settings.debug:
            print(f"[MQTT DEBUG] (state) on_connect rc={rc}")

        if rc == 0:
            logger.info(
                "Успешно подключились к MQTT (%s:%s) для state, rc=%s",
                self._settings.host,
                self._settings.port,
                rc,
            )
            topic_filter = make_state_topic_filter()
            client.subscribe(topic_filter, qos=1)
            if settings.debug:
                print(f"[MQTT DEBUG] (state) Подписались на {topic_filter}")
        else:
            logger.error(
                "Не удалось подключиться к MQTT (%s:%s) для state, rc=%s. Проверьте логин/пароль и ACL брокера",
                self._settings.host,
                self._settings.port,
                rc,
            )
            if settings.debug:
                print("[MQTT DEBUG] (state) Не удалось подключиться к MQTT, rc={rc} (rc=5 обычно означает ошибку авторизации)".format(rc=rc))

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        settings = self._settings
        topic = getattr(message, "topic", "")
        payload = getattr(message, "payload", b"")
        if settings.debug:
            try:
                raw_payload = payload.decode("utf-8", errors="replace")
            except Exception:
                raw_payload = "<decode error>"
            print(f"[MQTT DEBUG] (state) Пришло сообщение topic={topic} payload={raw_payload}")
        # Это сырое сообщение от устройства, до проверки схемы.
        logger.info(
            "MQTT state message: topic=%s payload=%s",
            topic,
            payload.decode("utf-8", errors="replace"),
        )
        device_id = extract_device_id_from_state_topic(topic)
        if not device_id:
            logger.warning("Топик %s не соответствует шаблону gh/dev/<id>/state", topic)
            return

        try:
            payload_text = payload.decode("utf-8")
            state = DeviceState.model_validate_json(payload_text)
        except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
            # сообщение проигнорировано, т.к. не соответствует ожидаемому формату DeviceState
            logger.warning("Не удалось разобрать состояние устройства %s: %s", device_id, exc)
            if settings.debug:
                # Такое может произойти, если прошивка прислала JSON в неожиданном формате.
                print(f"[MQTT DEBUG] (state) Ошибка парсинга состояния устройства: {exc}")
            return

        self._store.update_from_state(device_id, state)
        if settings.debug:
            print(f"[MQTT DEBUG] (state) Обновили стор для устройства {device_id}")
        logger.info("Обновили стор для устройства %s", device_id)


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
        settings = get_mqtt_settings()
        self._settings = settings
        self._store = store
        self._client_factory = client_factory or self._default_client_factory
        self._client: Optional[Client] = None
        self._running = False

    def _default_client_factory(self) -> Client:
        settings = self._settings
        client_id = f"{settings.client_id_prefix}-ack-{uuid4().hex[:8]}"
        client = Client(client_id=client_id)
        if settings.username:
            # Передаём брокеру логин/пароль, иначе mosquitto отвечает rc=5 (Not authorized) и соединение не устанавливается.
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
            # Здесь тоже используем прямой print, чтобы stdout гарантированно оказался в journalctl при DEBUG=True.
            print(
                f"[MQTT DEBUG] (ack) Пытаемся подключиться к брокеру {settings.host}:{settings.port} как пользователь {settings.username!r}"
            )

        try:
            logger.info(
                "Подключаемся к MQTT брокеру %s:%s для чтения ack",
                self._settings.host,
                self._settings.port,
            )
            rc = client.connect(self._settings.host, self._settings.port)
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
        settings = self._settings
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) on_connect rc={rc}")

        if rc == 0:
            logger.info(
                "Успешно подключились к MQTT (%s:%s) для ack, rc=%s",
                self._settings.host,
                self._settings.port,
                rc,
            )
            topic_filter = make_ack_topic_filter()
            client.subscribe(topic_filter, qos=1)
            if settings.debug:
                print(f"[MQTT DEBUG] (ack) Подписались на {topic_filter}")
        else:
            logger.error(
                "Не удалось подключиться к MQTT (%s:%s) для ack, rc=%s. Проверьте логин/пароль и ACL брокера",
                self._settings.host,
                self._settings.port,
                rc,
            )
            if settings.debug:
                print("[MQTT DEBUG] (ack) Не удалось подключиться к MQTT, rc={rc} (rc=5 обычно означает ошибку авторизации)".format(rc=rc))

    def _on_message(self, _client: Client, _userdata, message):  # type: ignore[override]
        settings = self._settings
        topic = getattr(message, "topic", "")
        payload = getattr(message, "payload", b"")
        if settings.debug:
            try:
                raw_payload = payload.decode("utf-8", errors="replace")
            except Exception:
                raw_payload = "<decode error>"
            print(f"[MQTT DEBUG] (ack) Пришло сообщение topic={topic} payload={raw_payload}")
        # Это сырое сообщение от устройства, до проверки схемы.
        logger.info(
            "MQTT ack message: topic=%s payload=%s",
            topic,
            payload.decode("utf-8", errors="replace"),
        )
        device_id = extract_device_id_from_ack_topic(topic)
        if not device_id:
            logger.warning("Топик %s не соответствует шаблону gh/dev/<id>/ack", topic)
            return

        try:
            payload_text = payload.decode("utf-8")
            ack = Ack.model_validate_json(payload_text)
        except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
            # сообщение проигнорировано, т.к. не соответствует ожидаемому формату Ack
            logger.warning("Не удалось разобрать ACK для %s: %s", device_id, exc)
            if settings.debug:
                # Такое может произойти, если устройство прислало ACK в неожиданном формате.
                print(f"[MQTT DEBUG] (ack) Ошибка парсинга состояния устройства: {exc}")
            return

        self._store.put(device_id, ack)
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) Сохранили ACK correlation_id={ack.correlation_id}")
        logger.info("Сохранили ACK для correlation_id=%s", ack.correlation_id)


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
