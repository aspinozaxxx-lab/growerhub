"""Доступ к MQTT-настройкам приложения."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from typing import Optional

from config import get_settings

__all__ = ["MqttSettings", "get_mqtt_settings"]


@dataclass(frozen=True)
class MqttSettings:
    """Набор настроек MQTT, необходимых сервисному слою."""

    host: str
    port: int
    username: Optional[str]
    password: Optional[str]
    tls: bool
    client_id_prefix: str
    debug: bool


@lru_cache()
def get_mqtt_settings() -> MqttSettings:
    """Вернуть кэшированную проекцию настроек приложения на MQTT."""

    settings = get_settings()
    return MqttSettings(
        host=settings.MQTT_HOST,
        port=settings.MQTT_PORT,
        username=settings.MQTT_USERNAME,
        password=settings.MQTT_PASSWORD,
        tls=settings.MQTT_TLS,
        client_id_prefix=settings.MQTT_CLIENT_ID_PREFIX,
        debug=settings.DEBUG,
    )

