"""Обработчики ACK-сообщений из MQTT."""

from __future__ import annotations

import json
import logging
from typing import Optional

from pydantic import ValidationError

from ack_store import AckStore
from service.mqtt.config import MqttSettings
from service.mqtt.serialization import Ack

__all__ = [
    "ACK_TOPIC_FILTER",
    "make_ack_topic_filter",
    "extract_device_id_from_ack_topic",
    "handle_ack_message",
]

logger = logging.getLogger(__name__)

ACK_TOPIC_FILTER = "gh/dev/+/ack"


def make_ack_topic_filter() -> str:
    """Вернуть MQTT-фильтр для ACK-топиков."""

    return ACK_TOPIC_FILTER


def extract_device_id_from_ack_topic(topic: str) -> Optional[str]:
    """Выделить device_id из топика ACK, иначе вернуть None."""

    parts = topic.split("/")
    if len(parts) != 4:
        return None
    prefix, middle, device_id, suffix = parts
    if prefix != "gh" or middle != "dev" or suffix != "ack" or not device_id:
        return None
    return device_id


def handle_ack_message(
    settings: MqttSettings,
    store: AckStore,
    topic: str,
    payload: bytes,
) -> None:
    """Разобрать ACK и сохранить его в хранилище."""

    if settings.debug:
        try:
            raw = payload.decode("utf-8", errors="replace")
        except Exception:
            raw = "<decode error>"
        print(f"[MQTT DEBUG] (ack) входящее сообщение: topic={topic} payload={raw}")

    logger.info(
        "MQTT ack message: topic=%s payload=%s",
        topic,
        payload.decode("utf-8", errors="replace"),
    )

    device_id = extract_device_id_from_ack_topic(topic)
    if not device_id:
        logger.warning("Топик %s не соответствует шаблону gh/dev/<id>/ack", topic)
        return

    try:
        payload_text = payload.decode("utf-8")
        ack = Ack.model_validate_json(payload_text)
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
        logger.warning("Не удалось разобрать ACK от %s: %s", device_id, exc)
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) ошибка парсинга ACK: {exc}")
        return

    store.put(device_id, ack)
    if settings.debug:
        print(f"[MQTT DEBUG] (ack) сохранён ACK correlation_id={ack.correlation_id}")
    logger.info("Сохранён ACK с correlation_id=%s", ack.correlation_id)

