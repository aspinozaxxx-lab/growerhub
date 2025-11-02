"""Modul obrabatyvaet retained-state soobshcheniya MQTT i obnovlyaet shadow."""

from __future__ import annotations

import json
import logging
from typing import Optional

from pydantic import ValidationError

from ..store import DeviceShadowStore
from ..config import MqttSettings
from ..serialization import DeviceState

# Publikuem konstanty i helpery dlya sostoyaniy
__all__ = [
    "STATE_TOPIC_FILTER",
    "make_state_topic_filter",
    "extract_device_id_from_state_topic",
    "handle_state_message",
]

# Logger dlya fiksacii sostoyaniy ot ustroystv
logger = logging.getLogger(__name__)

# MQTT topic filter dlya retained state topikov
STATE_TOPIC_FILTER = "gh/dev/+/state"


def make_state_topic_filter() -> str:
    """Vozvrashaet topic filter dlya podpiski na sostoyaniya ustroystv."""

    return STATE_TOPIC_FILTER


def extract_device_id_from_state_topic(topic: str) -> Optional[str]:
    """Dobyvaet device_id iz state-topika ili vozvrashaet None pri nevernom formate."""

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
    """Dekodiruet sostoyanie ustroystva i peredayet ego v DeviceShadowStore."""

    if settings.debug:
        try:
            raw = payload.decode("utf-8", errors="replace")
        except Exception:
            raw = "<decode error>"
        print(f"[MQTT DEBUG] (state) prinyato soobshchenie: topic={topic} payload={raw}")

    logger.info(
        "MQTT state message: topic=%s payload=%s",
        topic,
        payload.decode("utf-8", errors="replace"),
    )

    device_id = extract_device_id_from_state_topic(topic)
    if not device_id:
        logger.warning("%s ne sootvetstvuet shablonu gh/dev/<id>/state", topic)
        return

    try:
        payload_text = payload.decode("utf-8")
        state = DeviceState.model_validate_json(payload_text)
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
        logger.warning("Ne udalos razobrat state ot %s: %s", device_id, exc)
        if settings.debug:
            print(f"[MQTT DEBUG] (state) oshibka dekodirovaniya state: {exc}")
        return

    store.update_from_state(device_id, state)
    if settings.debug:
        print(f"[MQTT DEBUG] (state) obnovili shadow dlya {device_id}")
    logger.info("Obnovili shadow dlya %s", device_id)

