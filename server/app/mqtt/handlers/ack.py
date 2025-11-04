"""Modul obrabatyvaet MQTT ACK soobshcheniya i zapisyvaet ih v AckStore."""

from __future__ import annotations

import json
import logging
from typing import Optional

from pydantic import ValidationError

from ..store import AckStore
from ..config import MqttSettings
from ..serialization import Ack

# Publikuem konstanty i helpery dlya raboty s ACK
__all__ = [
    "ACK_TOPIC_FILTER",
    "make_ack_topic_filter",
    "extract_device_id_from_ack_topic",
    "handle_ack_message",
]

# Logger dlya kontrolya poluchennyh ACK
logger = logging.getLogger(__name__)

# MQTT topic filter dlya vsekh ACK ot ustroystv
ACK_TOPIC_FILTER = "gh/dev/+/ack"


def make_ack_topic_filter() -> str:
    """Vozvrashaet topic filter dlya podpiski na vse ACK soobshcheniya."""

    return ACK_TOPIC_FILTER


def extract_device_id_from_ack_topic(topic: str) -> Optional[str]:
    """Vydelyaet device_id iz ack-topika ili vozvrashaet None pri nevernom formate."""

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
    """Dekodiruet ACK iz payload, validiruet i sohranyaet v AckStore."""

    if settings.debug:
        try:
            raw = payload.decode("utf-8", errors="replace")
        except Exception:
            raw = "<decode error>"
        print(f"[MQTT DEBUG] (ack) prinyato soobshchenie: topic={topic} payload={raw}")

    logger.info(
        "MQTT ack message: topic=%s payload=%s",
        topic,
        payload.decode("utf-8", errors="replace"),
    )

    device_id = extract_device_id_from_ack_topic(topic)
    if not device_id:
        logger.warning("%s ne sootvetstvuet shablonu gh/dev/<id>/ack", topic)
        return

    try:
        payload_text = payload.decode("utf-8")
        ack = Ack.model_validate_json(payload_text)
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
        logger.warning("Ne udalos razobrat ACK ot %s: %s", device_id, exc)
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) oshibka dekodirovaniya ACK: {exc}")
        return

    store.put(device_id, ack)
    if settings.debug:
        print(f"[MQTT DEBUG] (ack) sohranili ACK correlation_id={ack.correlation_id}")
    logger.info("Sohranili ACK s correlation_id=%s", ack.correlation_id)

