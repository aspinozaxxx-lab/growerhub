"""Application configuration settings."""

from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from typing import Optional


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    """Настройки приложения: управляют подключением к MQTT и вспомогательными порогами для UI.

    DEVICE_ONLINE_THRESHOLD_S задаёт, насколько свежим должен быть state; по необходимости можно
    переопределить переменной окружения DEVICE_ONLINE_THRESHOLD_S.
    """

    MQTT_HOST: str = "localhost"
    MQTT_PORT: int = 1883
    MQTT_USERNAME: Optional[str] = None
    MQTT_PASSWORD: Optional[str] = None
    MQTT_TLS: bool = False
    MQTT_CLIENT_ID_PREFIX: str = "growerhub-api"
    DEVICE_ONLINE_THRESHOLD_S: int = 10


@lru_cache()
def get_settings() -> Settings:
    """Возвращает кэшированные настройки, считанные из окружения."""

    return Settings(
        MQTT_HOST=os.getenv("MQTT_HOST", Settings.MQTT_HOST),
        MQTT_PORT=_env_int("MQTT_PORT", Settings.MQTT_PORT),
        MQTT_USERNAME=os.getenv("MQTT_USERNAME"),
        MQTT_PASSWORD=os.getenv("MQTT_PASSWORD"),
        MQTT_TLS=_env_bool("MQTT_TLS", Settings.MQTT_TLS),
        MQTT_CLIENT_ID_PREFIX=os.getenv("MQTT_CLIENT_ID_PREFIX", Settings.MQTT_CLIENT_ID_PREFIX),
        DEVICE_ONLINE_THRESHOLD_S=_env_int("DEVICE_ONLINE_THRESHOLD_S", Settings.DEVICE_ONLINE_THRESHOLD_S),
    )
