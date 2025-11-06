"""Modul obrabatyvaet MQTT ACK soobshcheniya i zapisyvaet ih v AckStore."""

from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import Optional

from pydantic import ValidationError

from ..store import AckStore
from ..config import MqttSettings
from ..serialization import Ack
from app.repositories.state_repo import DeviceStateLastRepository, MqttAckRepository

# Publikuem konstanty i helpery dlya raboty s ACK
__all__ = [
    "ACK_TOPIC_FILTER",
    "make_ack_topic_filter",
    "extract_device_id_from_ack_topic",
    "handle_ack_message",
]

# Logger dlya kontrolya poluchennyh ACK
logger = logging.getLogger(__name__)

# Repo dlya sohraneniya ACK v BD
_ack_repo = MqttAckRepository()
# Repo dlya obnovleniya updated_at po MQTT sobytiyam
_state_repo = DeviceStateLastRepository()

# MQTT topic filter dlya vsekh ACK ot ustroystv
ACK_TOPIC_FILTER = "gh/dev/+/state/ack"


def make_ack_topic_filter() -> str:
    """Vozvrashaet topic filter dlya podpiski na vse ACK soobshcheniya."""

    return ACK_TOPIC_FILTER


def extract_device_id_from_ack_topic(topic: str) -> Optional[str]:
    """Vydelyaet device_id iz ack-topika ili vozvrashaet None pri nevernom formate."""

    parts = topic.split("/")
    if len(parts) != 5:
        return None
    prefix, middle, device_id, state_segment, suffix = parts
    if (
        prefix != "gh"
        or middle != "dev"
        or state_segment != "state"
        or suffix != "ack"
        or not device_id
    ):
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
        logger.warning("%s ne sootvetstvuet shablonu gh/dev/<id>/state/ack", topic)
        return

    try:
        payload_text = payload.decode("utf-8")
        ack = Ack.model_validate_json(payload_text)
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError, ValidationError) as exc:
        raw_payload = payload.decode("utf-8", errors="replace")
        logger.warning("Ne udalos razobrat ACK ot %s: %s", device_id, exc)
        logger.info("Otkidaem nekorrektnyj ACK ot %s: %s", device_id, raw_payload)
        if settings.debug:
            print(f"[MQTT DEBUG] (ack) oshibka dekodirovaniya ACK: {exc}")
        return

    store.put(device_id, ack)
    if settings.debug:
        print(f"[MQTT DEBUG] (ack) sohranili ACK correlation_id={ack.correlation_id}")
    logger.info("Sohranili ACK s correlation_id=%s", ack.correlation_id)

    # Translitem: sohranyaem ack v BD s TTL, chtoby pri restarte ne terять poslednie podtverzhdeniya.
    ack_dict = ack.model_dump(mode="json")
    received_at = datetime.utcnow()
    try:
        _ack_repo.put_ack(
            correlation_id=ack.correlation_id,
            device_id=device_id,
            ack_dict=ack_dict,
            received_at=received_at,
            ttl_seconds=settings.ack_ttl_seconds,
        )
    except Exception as exc:  # pragma: no cover - translitem: ne padaem esli BD nedostupna
        logger.warning("Ne udalos sohranit ACK %s v BD: %s", ack.correlation_id, exc)
    else:
        logger.debug(
            "Sohranili ACK v BD dlya %s s TTL %s",
            ack.correlation_id,
            settings.ack_ttl_seconds,
        )
        # Translitem: probyvaem puls ustroystva chtoby ACK prodlil okno onlayna.
        try:
            _state_repo.touch(device_id=device_id, now_utc=received_at)
        except Exception as exc:  # pragma: no cover - translitem: ne blokiruem potok pri oshibke BD
            logger.warning("Ne udalos obnovit puls dlya %s posle ACK: %s", device_id, exc)

