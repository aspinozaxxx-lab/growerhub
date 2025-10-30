"""Compatibility layer for legacy imports of mqtt_protocol.

TODO: remove this module once everything uses service.mqtt.* directly.
"""

from __future__ import annotations

from service.mqtt.serialization import (
    Ack,
    AckResult,
    CmdBase,
    CmdPumpStart,
    CmdPumpStop,
    CommandType,
    DeviceState,
    ManualWateringState,
    ManualWateringStatus,
    serialize,
)
from service.mqtt.topics import (
    ACK_TOPIC_TEMPLATE,
    CMD_TOPIC_TEMPLATE,
    STATE_TOPIC_TEMPLATE,
    ack_topic,
    cmd_topic,
    state_topic,
)

__all__ = [
    "Ack",
    "AckResult",
    "CmdBase",
    "CmdPumpStart",
    "CmdPumpStop",
    "CommandType",
    "DeviceState",
    "ManualWateringState",
    "ManualWateringStatus",
    "serialize",
    "CMD_TOPIC_TEMPLATE",
    "ACK_TOPIC_TEMPLATE",
    "STATE_TOPIC_TEMPLATE",
    "cmd_topic",
    "ack_topic",
    "state_topic",
]
