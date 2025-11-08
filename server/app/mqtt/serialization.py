"""Modul opisС‹РІР°РµС‚ Pydantic-shemy dlya MQTT soobshcheniy i utilitu serialize."""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Literal, Optional, Union

from pydantic import BaseModel, Field, conint

# Publikuem spisok kluchevyh modeley i helpers
__all__ = [
    "CommandType",
    "AckResult",
    "ManualWateringStatus",
    "CmdBase",
    "CmdPumpStart",
    "CmdPumpStop",
    "CmdReboot",
    "CmdOta",
    "Ack",
    "ManualWateringState",
    "DeviceState",
    "serialize",
]


class CommandType(str, Enum):
    """Tipy komand manual watering, kotorye otpravljayutsya brokeru."""

    pump_start = "pump.start"
    pump_stop = "pump.stop"
    reboot = "reboot"  # komandnyj tip dlya reboot komandy ustroystva
    ota = "ota"


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


class CmdReboot(BaseModel):
    """Komanda perezagruzki ustroystva cherez MQTT."""

    type: Literal[CommandType.reboot.value] = CommandType.reboot.value  # fiksiruem literal reboot chtoby broker prinyal TIP
    correlation_id: str = Field(..., min_length=1)  # korelaciya dlya ozhidaniya ACK ot ustroystva
    issued_at: int = Field(..., ge=0)  # epoch seconds kogda komanda sformirovana


class CmdOta(BaseModel):
    """Komanda OTA zagruzki s https-url i kontrollnoj summoj."""

    type: Literal[CommandType.ota.value] = CommandType.ota.value
    url: str
    version: str
    sha256: str


class Ack(BaseModel):
    """ACK payload ot ustroystva s korelaciey i opisaniem rezul'tata."""

    correlation_id: str = Field(..., min_length=1)
    result: AckResult
    reason: Optional[str] = None
    status: Optional[Union[ManualWateringStatus, Literal["reboot"]]] = (
        None  # status= "reboot" teper' dopuskaetsya dlya komandy reboot
    )
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
    # Translitem: dobavlyaem rasshirennye polya proshivki iz heartbeat.
    fw: Optional[str] = None
    fw_ver: Optional[str] = None
    fw_name: Optional[str] = None
    fw_build: Optional[str] = None


def serialize(model: BaseModel) -> bytes:
    """Preobrazuet Pydantic-model v JSON bytes UTF-8 bez None poley."""

    return model.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")

