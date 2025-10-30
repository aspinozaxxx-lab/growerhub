"""Интерфейсы и протоколы MQTT-уровня сервиса."""

from __future__ import annotations

from typing import Protocol, Union

from .serialization import CmdPumpStart, CmdPumpStop

__all__ = ["IMqttPublisher"]


class IMqttPublisher(Protocol):
    """Интерфейс MQTT-публикатора для команд ручного полива."""

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop]) -> None:
        """Опубликовать команду с QoS1, retain=False."""

