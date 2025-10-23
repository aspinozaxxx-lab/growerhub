"""MQTT publisher abstraction for manual watering commands."""

from __future__ import annotations

import logging
from typing import Optional, Protocol, Union
from uuid import uuid4

from paho.mqtt.client import Client, MQTT_ERR_SUCCESS

from config import get_settings
from mqtt_protocol import CmdPumpStart, CmdPumpStop, cmd_topic, serialize

logger = logging.getLogger(__name__)


class IMqttPublisher(Protocol):
    """Интерфейс паблишера команд в MQTT.

    Любая реализация должна уметь публиковать команды pump.start/pump.stop
    в нужный топик с QoS1 и без retain, а также сигнализировать об ошибках
    публикации. Такой контракт позволяет подменять реализацию в тестах.
    """

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop]) -> None:
        """Публикует команду в QoS1, retain=False."""


class PahoMqttPublisher(IMqttPublisher):
    """Реализация паблишера на базе paho-mqtt.

    Создаёт MQTT-клиент с индивидуальным client_id, подключается при старте,
    запускает сетевой loop в отдельном потоке и умеет публиковать команды.
    Любая ошибка подключения или публикации переводится в исключение.
    """

    def __init__(self) -> None:
        settings = get_settings()
        client_id = f"{settings.MQTT_CLIENT_ID_PREFIX}-{uuid4().hex[:8]}"
        self._settings = settings
        self._client = Client(client_id=client_id)
        if settings.MQTT_USERNAME:
            self._client.username_pw_set(settings.MQTT_USERNAME, settings.MQTT_PASSWORD)
        if settings.MQTT_TLS:
            self._client.tls_set()  # Defaults suitable for simple TLS enablement.
        self._connected = False

    def connect(self) -> None:
        """Connect to MQTT broker and start background network loop."""

        settings = self._settings
        result = self._client.connect(settings.MQTT_HOST, settings.MQTT_PORT)
        if result != MQTT_ERR_SUCCESS:
            raise RuntimeError(f"Failed to connect to MQTT broker: rc={result}")
        self._client.loop_start()
        self._connected = True

    def disconnect(self) -> None:
        """Stop background loop and disconnect from broker."""

        if self._connected:
            self._client.loop_stop()
            self._client.disconnect()
            self._connected = False

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop]) -> None:
        """Публикует команду в QoS1, retain=False; ошибки rc != SUCCESS считаем критичными.

        Любая неуспешная попытка (код возврата брокера не равен MQTT_ERR_SUCCESS)
        приводит к выбросу RuntimeError, чтобы вызывающий код мог отработать ошибку.
        """

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
    """Инициализирует глобальный экземпляр паблишера; повторные вызовы игнорируются.

    Соединение с брокером устанавливается один раз на старте приложения,
    чтобы переиспользовать MQTT-клиент между запросами FastAPI.
    """

    global _publisher, _publisher_error
    if _publisher:
        return

    publisher = PahoMqttPublisher()
    try:
        publisher.connect()
    except Exception as exc:  # pragma: no cover - logging path
        _publisher = None
        _publisher_error = exc
        logger.warning("MQTT publisher initialisation failed: %s", exc)
    else:
        _publisher = publisher
        _publisher_error = None


def shutdown_publisher() -> None:
    """Аккуратно завершает работу паблишера: останавливает loop и закрывает соединение.

    Вызывается при выключении приложения, чтобы корректно завершить
    фоновые потоки paho-mqtt и освободить ресурсы.
    """

    global _publisher, _publisher_error
    if _publisher:
        _publisher.disconnect()
    _publisher = None
    _publisher_error = None


def get_publisher() -> IMqttPublisher:
    """Возвращает активный паблишер или поднимает осмысленную ошибку."""

    if _publisher:
        return _publisher
    if _publisher_error:
        raise RuntimeError("MQTT publisher unavailable") from _publisher_error
    raise RuntimeError("MQTT publisher not initialised")
