"""Modul otvechaet za dostup k nastroikam MQTT dlya API-urovnya."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from typing import Optional

from config import get_settings

# Eksportiruem obyazatelnye obekty konfiguracii MQTT
__all__ = ["MqttSettings", "get_mqtt_settings"]


@dataclass(frozen=True)
class MqttSettings:
    """Opisanie nastroek MQTT, kotorye nuzhny servisnomu sloyu API."""

    host: str
    port: int
    username: Optional[str]
    password: Optional[str]
    tls: bool
    client_id_prefix: str
    debug: bool
    device_online_threshold_s: int
    ack_ttl_seconds: int
    ack_cleanup_period_seconds: int

    @property
    def DEVICE_ONLINE_THRESHOLD_S(self) -> int:  # pragma: no cover - compatibility shim
        """Vozvrashaet znachenie poroga onlayn statusa (legacy alias)."""

        return self.device_online_threshold_s


@lru_cache()
def get_mqtt_settings() -> MqttSettings:
    """Vozvrashchaet zakeshirovannye nastroiki MQTT iz globalnyh settings."""

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
        ack_ttl_seconds=settings.ACK_TTL_SECONDS,
        ack_cleanup_period_seconds=settings.ACK_CLEANUP_PERIOD_SECONDS,
    )

