"""Modul opisывает Pydantic-shemy dlya MQTT soobshcheniy i utilitu serialize."""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Optional, Union

from pydantic import BaseModel, Field, conint

# Publikuem spisok kluchevyh modeley i helpers
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
    """Tipy komand manual watering, kotorye otpravljayutsya brokeru."""

    pump_start = "pump.start"
    pump_stop = "pump.stop"


class AckResult(str, Enum):
    """Vozmozhnye rezul'taty ACK ot ustroystva (uspeshno, otkaz, oshibka)."""

    accepted = "accepted"
    rejected = "rejected"
    error = "error"


class ManualWateringStatus(str, Enum):
    """Statusy sostoyaniya poliva, kotorie otpravlyaet ustroystvo."""

    idle = "idle"
    running = "running"
    stopping = "stopping"


class CmdBase(BaseModel):
    """Bazovaya shema dlya lyuboy komandy MQTT (tip, korelaciya, vremya)."""

    type: str
    correlation_id: str = Field(..., min_length=1)
    ts: datetime


class CmdPumpStart(CmdBase):
    """Komanda zapuska nasosa s ukazaniem dlitelnosti v sekundah."""

    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class CmdPumpStop(CmdBase):
    """Komanda ostanovki nasosa bez dopolnitelnyh poley."""


class Ack(BaseModel):
    """ACK payload ot ustroystva s korelaciey i opisaniem rezul'tata."""

    correlation_id: str = Field(..., min_length=1)
    result: AckResult
    reason: Optional[str] = None
    status: Optional[ManualWateringStatus] = None
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None


class ManualWateringState(BaseModel):
    """Sostoyanie manual watering dlya retained-topika ustroystva."""

    status: ManualWateringStatus
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None
    remaining_s: Optional[int] = Field(default=None, ge=0)
    correlation_id: Optional[str] = None


class DeviceState(BaseModel):
    """Aggregirovannoe sostoyanie ustroystva (manual watering i firmware)."""

    manual_watering: ManualWateringState
    fw: Optional[str] = None


def serialize(model: BaseModel) -> bytes:
    """Preobrazuet Pydantic-model v JSON bytes UTF-8 bez None poley."""

    return model.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")

