"""Адаптер публикатора MQTT поверх paho-mqtt."""

from __future__ import annotations

import logging
from typing import Union
from uuid import uuid4

from paho.mqtt.client import Client, MQTT_ERR_SUCCESS

from .config import get_mqtt_settings
from .interfaces import IMqttPublisher
from .serialization import CmdPumpStart, CmdPumpStop, serialize
from .topics import cmd_topic

__all__ = ["PahoMqttPublisher"]

logger = logging.getLogger(__name__)


class PahoMqttPublisher(IMqttPublisher):
    """Конкретная реализация публикации команд через paho-mqtt."""

    def __init__(self) -> None:
        settings = get_mqtt_settings()
        client_id = f"{settings.client_id_prefix}-{uuid4().hex[:8]}"
        self._settings = settings
        self._client = Client(client_id=client_id)
        if settings.username:
            # Авторизация через Mosquitto ACL.
            self._client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            # Достаточно дефолтов paho для включения TLS.
            self._client.tls_set()
        self._connected = False

    def connect(self) -> None:
        """Подключиться к брокеру и запустить сетевой цикл."""

        settings = self._settings
        logger.info("Подключаемся к MQTT %s:%s для publisher", settings.host, settings.port)
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
        """Остановить сетевой цикл и отключиться от брокера."""

        if self._connected:
            self._client.loop_stop()
            self._client.disconnect()
            self._connected = False

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop]) -> None:
        """Отправить команду с QoS1, retain=False."""

        if not self._connected:
            raise RuntimeError("MQTT client is not connected")

        topic = cmd_topic(device_id)
        payload = serialize(cmd)
        result = self._client.publish(topic, payload=payload, qos=1, retain=False)
        if result.rc != MQTT_ERR_SUCCESS:
            raise RuntimeError(f"Failed to publish command: rc={result.rc}")

