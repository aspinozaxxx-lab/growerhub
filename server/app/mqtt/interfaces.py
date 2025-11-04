"""Modul opisывает protokol MQTT publishera dlya marshrutizatorov API."""

from __future__ import annotations

from typing import Protocol, Union

from .serialization import CmdPumpStart, CmdPumpStop, CmdReboot

# Publikuemyi interface dlya zavisimostei
__all__ = ["IMqttPublisher"]


class IMqttPublisher(Protocol):
    """Obshchii kontrakt dlya MQTT-izdatelya komand manual watering."""

    def publish_cmd(self, device_id: str, cmd: Union[CmdPumpStart, CmdPumpStop, CmdReboot]) -> None:
        """Otpravlyaet komandnoe soobshchenie s QoS1 i bez retain dlya ukazannogo uzla."""

