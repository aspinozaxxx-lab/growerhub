"""MQTT publisher abstraction for manual watering commands."""

from __future__ import annotations

import logging
from typing import Optional, Union
from uuid import uuid4

from paho.mqtt.client import Client, MQTT_ERR_SUCCESS

from service.mqtt.config import get_mqtt_settings
from service.mqtt.interfaces import IMqttPublisher
from service.mqtt.serialization import CmdPumpStart, CmdPumpStop, serialize
from service.mqtt.topics import cmd_topic

logger = logging.getLogger(__name__)

__all__ = [
    "IMqttPublisher",
    "PahoMqttPublisher",
    "init_publisher",
    "shutdown_publisher",
    "get_publisher",
]


class PahoMqttPublisher(IMqttPublisher):
    """Реализация публикации команд поверх paho-mqtt."""

    def __init__(self) -> None:
        settings = get_mqtt_settings()
        client_id = f"{settings.client_id_prefix}-{uuid4().hex[:8]}"
        self._settings = settings
        self._client = Client(client_id=client_id)
        if settings.username:
            # Логин/пароль идут от Mosquitto, иначе брокер чаще всего отвечает rc=5 (Not authorized).
            self._client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            # Базовая активация TLS без кастомных параметров — текущих настроек хватает.
            self._client.tls_set()
        self._connected = False

    def connect(self) -> None:
        """Подключиться к брокеру и запустить сетевой цикл paho."""

        settings = self._settings
        logger.info(
            "Подключаемся к MQTT %s:%s для publisher",
            settings.host,
            settings.port,
        )
        result = self._client.connect(settings.host, settings.port)
        if result != MQTT_ERR_SUCCESS:
            logger.error(
                "Не удалось подключиться к MQTT (%s:%s) для publisher, rc=%s. "
                "Проверьте логин/пароль и ACL Mosquitto.",
                settings.host,
                settings.port,
                result,
            )
            raise RuntimeError(f"Failed to connect to MQTT broker: rc={result}")
        logger.info(
            "Успешно подключились к MQTT (%s:%s) для publisher, rc=%s",
            settings.host,
            settings.port,
            result,
        )
        self._client.loop_start()
        self._connected = True

    def disconnect(self) -> None:
        """Остановить циклы paho и разорвать подключение."""

        if self._connected:
            self._client.loop_stop()
            self._client.disconnect()
            self._connected = False

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop]) -> None:
        """Отправить команду помпе с QoS1, retain=False."""

        if not self._connected:
            raise RuntimeError("MQTT client is not connected")

        topic = cmd_topic(device_id)
        payload = serialize(cmd)
        result = self._client.publish(topic, payload=payload, qos=1, retain=False)
        if result.rc != MQTT_ERR_SUCCESS:
            raise RuntimeError(f"Failed to publish command: rc={result.rc}")


_publisher: Optional[PahoMqttPublisher] = None
_publisher_error: Optional[Exception] = None


def init_publisher() -> None:
    """Поднять singleton-публикатор и подготовить соединение."""

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
    """Аккуратно остановить публикатор и очистить кэш."""

    global _publisher, _publisher_error
    if _publisher:
        _publisher.disconnect()
    _publisher = None
    _publisher_error = None


def get_publisher() -> IMqttPublisher:
    """Вернуть активный публикатор либо выбросить RuntimeError."""

    if _publisher:
        return _publisher
    if _publisher_error:
        raise RuntimeError("MQTT publisher unavailable") from _publisher_error
    raise RuntimeError("MQTT publisher not initialised")
