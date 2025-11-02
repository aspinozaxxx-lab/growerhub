"""Compatibility wrapper exposing DeviceShadowStore helpers after MQTT refactor."""

from app.mqtt.store import (
    DeviceShadowStore,
    get_settings,
    get_shadow_store,
    init_shadow_store,
    shutdown_shadow_store,
)

__all__ = [
    "DeviceShadowStore",
    "get_shadow_store",
    "init_shadow_store",
    "shutdown_shadow_store",
    "get_settings",
]
