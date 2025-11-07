"""Modul s paho-mqtt adapterom dlya publikacii komand iz FastAPI."""

from __future__ import annotations

import logging
from typing import Union
from uuid import uuid4

from paho.mqtt.client import CallbackAPIVersion, Client, MQTT_ERR_SUCCESS

from .config import get_mqtt_settings
from .interfaces import IMqttPublisher
from .serialization import CmdPumpStart, CmdPumpStop, CmdReboot, CmdOta, serialize
from .topics import cmd_topic

# Publikuemyi adapter dlya zavisimostey
__all__ = ["PahoMqttPublisher"]

# Logger dlya otladki MQTT klienta
logger = logging.getLogger(__name__)


class PahoMqttPublisher(IMqttPublisher):
    """Adapter vklyuchaet paho-mqtt klient dlya otpravki komand manual watering."""

    def __init__(self) -> None:
        """Sobiraet nastroiki i gotovit klient s unik ID, uchityvaya ACL i TLS."""

        settings = get_mqtt_settings()
        client_id = f"{settings.client_id_prefix}-{uuid4().hex[:8]}"
        self._settings = settings
        self._client = Client(client_id=client_id, callback_api_version=CallbackAPIVersion.VERSION2)
        if settings.username:
            # Nastraivaem credentials dlya Mosquitto ACL
            self._client.username_pw_set(settings.username, settings.password)
        if settings.tls:
            # Vklyuchaem TLS po trebovaniyu nastroek
            self._client.tls_set()
        self._connected = False

    def connect(self) -> None:
        """Podklyuchaet klient k brokeru i zapuskaet fonovyy loop_start (otdelnyy potok)."""

        settings = self._settings
        logger.info("Podklyuchenie k MQTT %s:%s kak publisher", settings.host, settings.port)
        result = self._client.connect(settings.host, settings.port)
        if result != MQTT_ERR_SUCCESS:
            logger.error(
                "Ne udalos podklyuchitsya k MQTT (%s:%s) kak publisher, rc=%s. "
                "Proverte ACL Mosquitto i rekvizity.",
                settings.host,
                settings.port,
                result,
            )
            raise RuntimeError(f"Failed to connect to MQTT broker: rc={result}")
        logger.info(
            "Uspeshno podklyuchilis k MQTT (%s:%s) kak publisher, rc=%s",
            settings.host,
            settings.port,
            result,
        )
        self._client.loop_start()
        self._connected = True

    def disconnect(self) -> None:
        """Korrekto osta navlivaet fonovyj loop i razryvaet soedinenie."""

        if self._connected:
            self._client.loop_stop()
            self._client.disconnect()
            self._connected = False

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop, CmdReboot, CmdOta]) -> None:
        """Otpravlyaet komandnoe soobshchenie v topik ustroystva s QoS1 bez retain."""

        if not self._connected:
            raise RuntimeError("MQTT client is not connected")

        topic = cmd_topic(device_id)
        payload = serialize(cmd)
        result = self._client.publish(topic, payload=payload, qos=1, retain=False)
        if result.rc != MQTT_ERR_SUCCESS:
            raise RuntimeError(f"Failed to publish command: rc={result.rc}")

