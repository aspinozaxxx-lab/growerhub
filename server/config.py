"""Application configuration settings."""

from __future__ import annotations

import os
import logging
from dataclasses import dataclass
from pathlib import Path
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


logger = logging.getLogger(__name__)
DEFAULT_PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FIRMWARE_DIR = (DEFAULT_PROJECT_ROOT / "server" / "firmware_binaries").resolve()
_logged_firmware_dir = False


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
    SECRET_KEY: str = "change-me-in-prod"  # Translitem: v boe obyazatel'no zadat' cherez env
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60  # Translitem: vremya zhizni JWT tokena v minutah
    AUTH_JWT_ALGORITHM: str = "HS256"  # Translitem: algoritm podpisaniya JWT
    ACK_TTL_SECONDS: int = 180  # Translitem: skolko hranim ack v BD do ochistki
    ACK_CLEANUP_PERIOD_SECONDS: int = 60  # Translitem: chastota fonovoy ochistki ack
    SERVER_PUBLIC_BASE_URL: str = "https://growerhub.ru"  # Translitem: bazovyj https dlya firmware URL (pereopredelyaetsya cherez env)
    FIRMWARE_BINARIES_DIR: str = str(DEFAULT_FIRMWARE_DIR)  # Translitem: absolyutnyj katalog .bin (pereopredelyaetsya FIRMWARE_BINARIES_DIR)
    AUTH_SSO_REDIRECT_BASE: Optional[str] = None  # Translitem: bazovyj URL dlya formirovaniya redirect_uri v SSO
    AUTH_GOOGLE_CLIENT_ID: str = ""
    AUTH_GOOGLE_CLIENT_SECRET: str = ""
    AUTH_GOOGLE_AUTH_URL: str = "https://accounts.google.com/o/oauth2/v2/auth"
    AUTH_GOOGLE_TOKEN_URL: str = "https://oauth2.googleapis.com/token"
    AUTH_GOOGLE_USERINFO_URL: str = "https://openidconnect.googleapis.com/v1/userinfo"
    AUTH_YANDEX_CLIENT_ID: str = ""
    AUTH_YANDEX_CLIENT_SECRET: str = ""
    AUTH_YANDEX_AUTH_URL: str = "https://oauth.yandex.ru/authorize"
    AUTH_YANDEX_TOKEN_URL: str = "https://oauth.yandex.ru/token"
    AUTH_YANDEX_USERINFO_URL: str = "https://login.yandex.ru/info"


@lru_cache()
def get_settings() -> Settings:
    """Возвращает кэшированные настройки, считанные из окружения."""

    settings = Settings(
        MQTT_HOST=os.getenv("MQTT_HOST", Settings.MQTT_HOST),
        MQTT_PORT=_env_int("MQTT_PORT", Settings.MQTT_PORT),
        MQTT_USERNAME=os.getenv("MQTT_USERNAME"),
        MQTT_PASSWORD=os.getenv("MQTT_PASSWORD"),
        MQTT_TLS=_env_bool("MQTT_TLS", Settings.MQTT_TLS),
        MQTT_CLIENT_ID_PREFIX=os.getenv("MQTT_CLIENT_ID_PREFIX", Settings.MQTT_CLIENT_ID_PREFIX),
        DEVICE_ONLINE_THRESHOLD_S=Settings.DEVICE_ONLINE_THRESHOLD_S,
        #DEVICE_ONLINE_THRESHOLD_S=_env_int("DEVICE_ONLINE_THRESHOLD_S", Settings.DEVICE_ONLINE_THRESHOLD_S),
        DEBUG=_env_bool("DEBUG", Settings.DEBUG),
        SECRET_KEY=os.getenv("SECRET_KEY", Settings.SECRET_KEY),
        ACCESS_TOKEN_EXPIRE_MINUTES=_env_int("ACCESS_TOKEN_EXPIRE_MINUTES", Settings.ACCESS_TOKEN_EXPIRE_MINUTES),
        AUTH_JWT_ALGORITHM=os.getenv("AUTH_JWT_ALGORITHM", Settings.AUTH_JWT_ALGORITHM),
        ACK_TTL_SECONDS=_env_int("ACK_TTL_SECONDS", Settings.ACK_TTL_SECONDS),
        ACK_CLEANUP_PERIOD_SECONDS=_env_int("ACK_CLEANUP_PERIOD_SECONDS", Settings.ACK_CLEANUP_PERIOD_SECONDS),
        SERVER_PUBLIC_BASE_URL=os.getenv("SERVER_PUBLIC_BASE_URL", Settings.SERVER_PUBLIC_BASE_URL),
        FIRMWARE_BINARIES_DIR=_resolve_firmware_dir(os.getenv("FIRMWARE_BINARIES_DIR")),
        AUTH_SSO_REDIRECT_BASE=os.getenv("AUTH_SSO_REDIRECT_BASE", Settings.AUTH_SSO_REDIRECT_BASE),
        AUTH_GOOGLE_CLIENT_ID=os.getenv("AUTH_GOOGLE_CLIENT_ID", Settings.AUTH_GOOGLE_CLIENT_ID),
        AUTH_GOOGLE_CLIENT_SECRET=os.getenv("AUTH_GOOGLE_CLIENT_SECRET", Settings.AUTH_GOOGLE_CLIENT_SECRET),
        AUTH_GOOGLE_AUTH_URL=os.getenv("AUTH_GOOGLE_AUTH_URL", Settings.AUTH_GOOGLE_AUTH_URL),
        AUTH_GOOGLE_TOKEN_URL=os.getenv("AUTH_GOOGLE_TOKEN_URL", Settings.AUTH_GOOGLE_TOKEN_URL),
        AUTH_GOOGLE_USERINFO_URL=os.getenv("AUTH_GOOGLE_USERINFO_URL", Settings.AUTH_GOOGLE_USERINFO_URL),
        AUTH_YANDEX_CLIENT_ID=os.getenv("AUTH_YANDEX_CLIENT_ID", Settings.AUTH_YANDEX_CLIENT_ID),
        AUTH_YANDEX_CLIENT_SECRET=os.getenv("AUTH_YANDEX_CLIENT_SECRET", Settings.AUTH_YANDEX_CLIENT_SECRET),
        AUTH_YANDEX_AUTH_URL=os.getenv("AUTH_YANDEX_AUTH_URL", Settings.AUTH_YANDEX_AUTH_URL),
        AUTH_YANDEX_TOKEN_URL=os.getenv("AUTH_YANDEX_TOKEN_URL", Settings.AUTH_YANDEX_TOKEN_URL),
        AUTH_YANDEX_USERINFO_URL=os.getenv("AUTH_YANDEX_USERINFO_URL", Settings.AUTH_YANDEX_USERINFO_URL),
    )
    _log_firmware_dir(settings.FIRMWARE_BINARIES_DIR)
    return settings


def _resolve_firmware_dir(custom_path: Optional[str]) -> str:
    """Vozvraschaet absolyutnyj katalog firmware, zadaem cherez env."""

    if custom_path:
        return str(Path(custom_path).expanduser().resolve())
    return str(DEFAULT_FIRMWARE_DIR)


def _log_firmware_dir(path_str: str) -> None:
    global _logged_firmware_dir
    if _logged_firmware_dir:
        return
    _logged_firmware_dir = True
    logger.info("Firmware dir (effective): %s", path_str)
    legacy_dir = (DEFAULT_PROJECT_ROOT / "firmware_binaries").resolve()
    if legacy_dir.exists() and str(legacy_dir) != path_str:
        logger.warning(
            "Obnaruzhen staryy katalog firmware_binaries v korne (%s); ispol'zuem novyy put' %s",
            legacy_dir,
            path_str,
        )
