"""Modul formiruet MQTT topiki dlya komand, ACK i sostoyanii ustroystv."""

from __future__ import annotations

# Publikuemoe API topikov dlya drugih modulей
__all__ = [
    "CMD_TOPIC_TEMPLATE",
    "ACK_TOPIC_TEMPLATE",
    "STATE_TOPIC_TEMPLATE",
    "cmd_topic",
    "ack_topic",
    "state_topic",
]

# Shablon topika dlya komandnyh soobshcheniy k ustroystvu
CMD_TOPIC_TEMPLATE = "gh/dev/{device_id}/cmd"
# Shablon topika dlya ACK-otvetov s ustroystva
ACK_TOPIC_TEMPLATE = "gh/dev/{device_id}/ack"
# Shablon topika dlya retained-sostoyanii ustroystva
STATE_TOPIC_TEMPLATE = "gh/dev/{device_id}/state"


def cmd_topic(device_id: str) -> str:
    """Vozvrashaet polnyy komandnyy topik dlya ukazannogo ustroystva."""

    return CMD_TOPIC_TEMPLATE.format(device_id=device_id)


def ack_topic(device_id: str) -> str:
    """Vozvrashaet topik dlya ACK otvetov ot ukazannogo ustroystva."""

    return ACK_TOPIC_TEMPLATE.format(device_id=device_id)


def state_topic(device_id: str) -> str:
    """Vozvrashaet retained-topik s sostoyaniem ukazannogo ustroystva."""

    return STATE_TOPIC_TEMPLATE.format(device_id=device_id)

