"""Шаблоны и генераторы MQTT-топиков для устройств."""

from __future__ import annotations

__all__ = [
    "CMD_TOPIC_TEMPLATE",
    "ACK_TOPIC_TEMPLATE",
    "STATE_TOPIC_TEMPLATE",
    "cmd_topic",
    "ack_topic",
    "state_topic",
]


CMD_TOPIC_TEMPLATE = "gh/dev/{device_id}/cmd"
ACK_TOPIC_TEMPLATE = "gh/dev/{device_id}/ack"
STATE_TOPIC_TEMPLATE = "gh/dev/{device_id}/state"


def cmd_topic(device_id: str) -> str:
    """Вернуть топик для команд (QoS1, retain=False)."""

    return CMD_TOPIC_TEMPLATE.format(device_id=device_id)


def ack_topic(device_id: str) -> str:
    """Вернуть топик для ACK (QoS1, retain=False)."""

    return ACK_TOPIC_TEMPLATE.format(device_id=device_id)


def state_topic(device_id: str) -> str:
    """Вернуть retained-топик состояния устройства (QoS1, retain=True)."""

    return STATE_TOPIC_TEMPLATE.format(device_id=device_id)

