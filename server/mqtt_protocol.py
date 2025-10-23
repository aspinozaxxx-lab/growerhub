"""MQTT protocol helpers for manual watering commands.

cmd topics publish with QoS 1 (not retained), acknowledgements with QoS 1,
and device state topics with QoS 1 retained.
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Optional, Union

from pydantic import BaseModel, Field, ValidationError, conint


CMD_TOPIC_TEMPLATE = "gh/dev/{device_id}/cmd"
ACK_TOPIC_TEMPLATE = "gh/dev/{device_id}/ack"
STATE_TOPIC_TEMPLATE = "gh/dev/{device_id}/state"


class CommandType(str, Enum):
    """Supported manual watering command types."""

    pump_start = "pump.start"
    pump_stop = "pump.stop"


class AckResult(str, Enum):
    """Possible acknowledgement statuses."""

    accepted = "accepted"
    rejected = "rejected"
    error = "error"


class ManualWateringStatus(str, Enum):
    """Device manual watering state machine statuses."""

    idle = "idle"
    running = "running"
    stopping = "stopping"


class CmdBase(BaseModel):
    """Base schema for device commands."""

    type: str
    correlation_id: str = Field(..., min_length=1)
    ts: datetime


class CmdPumpStart(CmdBase):
    """Command to start the pump for a fixed duration."""

    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class CmdPumpStop(CmdBase):
    """Command to stop the pump."""


class Ack(BaseModel):
    """Acknowledgement payload from the device."""

    correlation_id: str = Field(..., min_length=1)
    result: AckResult
    reason: Optional[str] = None
    status: Optional[ManualWateringStatus] = None
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None


class ManualWateringState(BaseModel):
    """Current manual watering state reported by a device."""

    status: ManualWateringStatus
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None
    remaining_s: Optional[int] = Field(default=None, ge=0)
    correlation_id: Optional[str] = None


class DeviceState(BaseModel):
    """Device state payload for MQTT retained topic."""

    manual_watering: ManualWateringState
    fw: Optional[str] = None


def cmd_topic(device_id: str) -> str:
    """Return the command topic for a device (QoS1, not retained)."""

    return CMD_TOPIC_TEMPLATE.format(device_id=device_id)


def ack_topic(device_id: str) -> str:
    """Return the acknowledgement topic for a device (QoS1)."""

    return ACK_TOPIC_TEMPLATE.format(device_id=device_id)


def state_topic(device_id: str) -> str:
    """Return the retained state topic for a device (QoS1 retained)."""

    return STATE_TOPIC_TEMPLATE.format(device_id=device_id)


def serialize(model: BaseModel) -> bytes:
    """Serialize a Pydantic model to UTF-8 encoded JSON."""

    return model.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")


def _load_json(payload: bytes) -> dict:
    import json

    try:
        return json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("Invalid JSON payload") from exc


def deserialize_cmd(payload: bytes) -> Union[CmdPumpStart, CmdPumpStop]:
    """Deserialize a device command payload."""

    data = _load_json(payload)
    cmd_type = data.get("type")
    if cmd_type == CommandType.pump_start.value:
        return CmdPumpStart.model_validate(data)
    if cmd_type == CommandType.pump_stop.value:
        return CmdPumpStop.model_validate(data)
    raise ValueError(f"Unsupported command type: {cmd_type!r}")


def deserialize_ack(payload: bytes) -> Ack:
    """Deserialize an acknowledgement payload."""

    data = _load_json(payload)
    try:
        return Ack.model_validate(data)
    except ValidationError as exc:
        raise ValueError("Invalid acknowledgement payload") from exc


def deserialize_state(payload: bytes) -> DeviceState:
    """Deserialize a device state payload."""

    data = _load_json(payload)
    try:
        return DeviceState.model_validate(data)
    except ValidationError as exc:
        raise ValueError("Invalid state payload") from exc


def is_same_command(new_cmd: CmdBase, prev_correlation_id: Optional[str]) -> bool:
    """Return True when correlation id matches previous command."""

    return prev_correlation_id is not None and new_cmd.correlation_id == prev_correlation_id
