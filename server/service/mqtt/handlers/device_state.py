"""Обработчики retained state сообщений устройств."""

from __future__ import annotations

import json
import logging
from typing import Optional

from pydantic import ValidationError

from service.mqtt.store import DeviceShadowStore
from service.mqtt.config import MqttSettings
from service.mqtt.serialization import DeviceState

__all__ = [
    "STATE_TOPIC_FILTER",
    "make_state_topic_filter",
    "extract_device_id_from_state_topic",
    "handle_state_message",
]

logger = logging.getLogger(__name__)

STATE_TOPIC_FILTER = "gh/dev/+/state"


def make_state_topic_filter() -> str:
    """Вернуть MQTT-фильтр для retained state."""

    return STATE_TOPIC_FILTER


def extract_device_id_from_state_topic(topic: str) -> Optional[str]:
    """Выделить device_id из топика retained state."""

    parts = topic.split("/")
    if len(parts) != 4:
        return None
    prefix, middle, device_id, suffix = parts
    if prefix != "gh" or middle != "dev" or suffix != "state" or not device_id:
        return None
    return device_id


def handle_state_message(
    settings: MqttSettings,
    store: DeviceShadowStore,
    topic: str,
    payload: bytes,
) -> None:
    """Разобрать retained state и обновить shadow."""

    if settings.debug:
        try:
            raw = payload.decode("utf-8", errors="replace")
        except Exception:
            raw = "<decode error>"
        print(f"[MQTT DEBUG] (state) входящее сообщение: topic={topic} payload={raw}")

    logger.info(
        "MQTT state message: topic=%s payload=%s",
        topic,
        payload.decode("utf-8", errors="replace"),
    )

    device_id = extract_device_id_from_state_topic(topic)
    if not device_id:
        logger.warning("Топик %s не соответствует шаблону gh/dev/<id>/state", topic)
        return

    try:
        payload_text = payload.decode("utf-8")
        state = DeviceState.model_validate_json(payload_text)
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
        logger.warning("Не удалось разобрать state от %s: %s", device_id, exc)
        if settings.debug:
            print(f"[MQTT DEBUG] (state) ошибка парсинга state: {exc}")
        return

    store.update_from_state(device_id, state)
    if settings.debug:
        print(f"[MQTT DEBUG] (state) обновлён shadow для {device_id}")
    logger.info("Обновлён shadow устройства %s", device_id)


