"""Centralised MQTT configuration access."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from typing import Optional

from config import get_settings

__all__ = ["MqttSettings", "get_mqtt_settings"]


@dataclass(frozen=True)
class MqttSettings:
    """Configuration subset used by the MQTT service layer."""

    host: str
    port: int
    username: Optional[str]
    password: Optional[str]
    tls: bool
    client_id_prefix: str
    debug: bool
    device_online_threshold_s: int

    @property
    def DEVICE_ONLINE_THRESHOLD_S(self) -> int:  # pragma: no cover - compatibility shim
        """Backward-compatible accessor for legacy code/tests."""

        return self.device_online_threshold_s


@lru_cache()
def get_mqtt_settings() -> MqttSettings:
    """Return cached MQTT-related configuration."""

    settings = get_settings()
    return MqttSettings(
        host=settings.MQTT_HOST,
        port=settings.MQTT_PORT,
        username=settings.MQTT_USERNAME,
        password=settings.MQTT_PASSWORD,
        tls=settings.MQTT_TLS,
        client_id_prefix=settings.MQTT_CLIENT_ID_PREFIX,
        debug=settings.DEBUG,
        device_online_threshold_s=settings.DEVICE_ONLINE_THRESHOLD_S,
    )
