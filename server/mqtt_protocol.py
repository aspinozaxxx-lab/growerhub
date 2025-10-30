"""Совместимость для старых импортов mqtt_protocol.

TODO: удалить после перехода всего кода на service.mqtt.*.
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
    deserialize_ack,
    deserialize_cmd,
    deserialize_state,
    is_same_command,
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
    "deserialize_ack",
    "deserialize_cmd",
    "deserialize_state",
    "is_same_command",
    "serialize",
    "CMD_TOPIC_TEMPLATE",
    "ACK_TOPIC_TEMPLATE",
    "STATE_TOPIC_TEMPLATE",
    "cmd_topic",
    "ack_topic",
    "state_topic",
]

