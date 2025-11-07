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
    """Настройки приложения: отвечают за подключение бэкенда к MQTT и сопутствующие пороги для UI.

    Переменные окружения MQTT_HOST/MQTT_PORT/MQTT_USERNAME/MQTT_PASSWORD задают креды, с которыми
    сервер выступает как MQTT-клиент; их удобно прокидывать через systemd unit. DEVICE_ONLINE_THRESHOLD_S
    ограничивает, насколько свежим должен быть state, а DEBUG включает отладочные ручки и сценарии.
    """

    MQTT_HOST: str = "localhost"
    MQTT_PORT: int = 1883
    MQTT_USERNAME: Optional[str] = None
    MQTT_PASSWORD: Optional[str] = None
    MQTT_TLS: bool = False
    MQTT_CLIENT_ID_PREFIX: str = "growerhub-api"
    DEVICE_ONLINE_THRESHOLD_S: int = 60  # интервал проверки состояния устройства
    DEBUG: bool = True
    ACK_TTL_SECONDS: int = 180  # Translitem: skolko hranim ack v BD do ochistki
    ACK_CLEANUP_PERIOD_SECONDS: int = 60  # Translitem: chastota fonovoy ochistki ack
    SERVER_PUBLIC_BASE_URL: str = "https://growerhub.ru"  # Translitem: bazovyj https dlya firmware URL (pereopredelyaetsya cherez env)
    FIRMWARE_BINARIES_DIR: str = "server/firmware_binaries"  # Translitem: lokalnyj katalog .bin (pereopredelyaetsya cherez env FIRMWARE_BINARIES_DIR)


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
        DEVICE_ONLINE_THRESHOLD_S=Settings.DEVICE_ONLINE_THRESHOLD_S,
        #DEVICE_ONLINE_THRESHOLD_S=_env_int("DEVICE_ONLINE_THRESHOLD_S", Settings.DEVICE_ONLINE_THRESHOLD_S),
        DEBUG=_env_bool("DEBUG", Settings.DEBUG),
        ACK_TTL_SECONDS=_env_int("ACK_TTL_SECONDS", Settings.ACK_TTL_SECONDS),
        ACK_CLEANUP_PERIOD_SECONDS=_env_int("ACK_CLEANUP_PERIOD_SECONDS", Settings.ACK_CLEANUP_PERIOD_SECONDS),
        SERVER_PUBLIC_BASE_URL=os.getenv("SERVER_PUBLIC_BASE_URL", Settings.SERVER_PUBLIC_BASE_URL),
        FIRMWARE_BINARIES_DIR=os.getenv("FIRMWARE_BINARIES_DIR", Settings.FIRMWARE_BINARIES_DIR),
    )
