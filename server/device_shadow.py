"""Compatibility wrapper for DeviceShadowStore (TODO: remove after migration)."""

from config import get_settings
from service.mqtt.store import (
    DeviceShadowStore,
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
