"""Pydantic models and helpers for MQTT payloads."""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Optional, Union

from pydantic import BaseModel, Field, conint

__all__ = [
    "CommandType",
    "AckResult",
    "ManualWateringStatus",
    "CmdBase",
    "CmdPumpStart",
    "CmdPumpStop",
    "Ack",
    "ManualWateringState",
    "DeviceState",
    "serialize",
]


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
    """Manual watering state-machine statuses."""

    idle = "idle"
    running = "running"
    stopping = "stopping"


class CmdBase(BaseModel):
    """Base schema for MQTT commands."""

    type: str
    correlation_id: str = Field(..., min_length=1)
    ts: datetime


class CmdPumpStart(CmdBase):
    """Command to start the pump for a fixed duration."""

    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class CmdPumpStop(CmdBase):
    """Command to stop the pump."""


class Ack(BaseModel):
    """Acknowledgement payload."""

    correlation_id: str = Field(..., min_length=1)
    result: AckResult
    reason: Optional[str] = None
    status: Optional[ManualWateringStatus] = None
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None


class ManualWateringState(BaseModel):
    """Manual watering state reported by a device."""

    status: ManualWateringStatus
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None
    remaining_s: Optional[int] = Field(default=None, ge=0)
    correlation_id: Optional[str] = None


class DeviceState(BaseModel):
    """Device state payload for retained topic."""

    manual_watering: ManualWateringState
    fw: Optional[str] = None


def serialize(model: BaseModel) -> bytes:
    """Serialize a Pydantic model to UTF-8 JSON bytes."""

    return model.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")
