"""Pydantic-схемы и (де)сериализация MQTT-пейлоадов."""

from __future__ import annotations

import json
from datetime import datetime
from enum import Enum
from typing import Optional, Union

from pydantic import BaseModel, Field, ValidationError, conint

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
    "deserialize_cmd",
    "deserialize_ack",
    "deserialize_state",
    "is_same_command",
]


class CommandType(str, Enum):
    """Поддерживаемые типы команд ручного полива."""

    pump_start = "pump.start"
    pump_stop = "pump.stop"


class AckResult(str, Enum):
    """Возможные результаты ACK от устройства."""

    accepted = "accepted"
    rejected = "rejected"
    error = "error"


class ManualWateringStatus(str, Enum):
    """Состояния state-machine ручного полива на устройстве."""

    idle = "idle"
    running = "running"
    stopping = "stopping"


class CmdBase(BaseModel):
    """Базовая схема MQTT-команды."""

    type: str
    correlation_id: str = Field(..., min_length=1)
    ts: datetime


class CmdPumpStart(CmdBase):
    """Команда запуска помпы на фиксированное время."""

    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class CmdPumpStop(CmdBase):
    """Команда остановки помпы."""


class Ack(BaseModel):
    """ACK-пейлоад от устройства."""

    correlation_id: str = Field(..., min_length=1)
    result: AckResult
    reason: Optional[str] = None
    status: Optional[ManualWateringStatus] = None
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None


class ManualWateringState(BaseModel):
    """Retained-состояние ручного полива."""

    status: ManualWateringStatus
    duration_s: Optional[int] = Field(default=None, ge=0)
    started_at: Optional[datetime] = None
    remaining_s: Optional[int] = Field(default=None, ge=0)
    correlation_id: Optional[str] = None


class DeviceState(BaseModel):
    """Снимок состояния устройства для retained-топика."""

    manual_watering: ManualWateringState
    fw: Optional[str] = None


def serialize(model: BaseModel) -> bytes:
    """Сериализовать модель Pydantic в UTF-8 JSON."""

    return model.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")


def _load_json(payload: bytes) -> dict:
    try:
        return json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:  # pragma: no cover - защитный путь
        raise ValueError("Invalid JSON payload") from exc


def deserialize_cmd(payload: bytes) -> Union[CmdPumpStart, CmdPumpStop]:
    """Десериализовать MQTT-команду устройства."""

    data = _load_json(payload)
    cmd_type = data.get("type")
    if cmd_type == CommandType.pump_start.value:
        return CmdPumpStart.model_validate(data)
    if cmd_type == CommandType.pump_stop.value:
        return CmdPumpStop.model_validate(data)
    raise ValueError(f"Unsupported command type: {cmd_type!r}")


def deserialize_ack(payload: bytes) -> Ack:
    """Десериализовать ACK-ответ устройства."""

    data = _load_json(payload)
    try:
        return Ack.model_validate(data)
    except ValidationError as exc:
        raise ValueError("Invalid acknowledgement payload") from exc


def deserialize_state(payload: bytes) -> DeviceState:
    """Десериализовать retained-состояние устройства."""

    data = _load_json(payload)
    try:
        return DeviceState.model_validate(data)
    except ValidationError as exc:
        raise ValueError("Invalid state payload") from exc


def is_same_command(new_cmd: CmdBase, prev_correlation_id: Optional[str]) -> bool:
    """Вернуть True, если correlation_id совпадает с предыдущей командой."""

    return prev_correlation_id is not None and new_cmd.correlation_id == prev_correlation_id

