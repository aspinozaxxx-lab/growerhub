from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class DeviceOwnerInfo(BaseModel):
    """Translitem: kratkaya informaciya o vladtse ustrojstva."""

    id: int
    email: str
    username: Optional[str] = None


class DeviceOut(BaseModel):
    """Translitem: otvet dlya spiska ustrojstv polzovatelya."""

    id: int
    device_id: str
    name: str
    is_online: bool
    soil_moisture: float
    air_temperature: float
    air_humidity: float
    is_watering: bool
    is_light_on: bool
    last_watering: Optional[datetime]
    last_seen: Optional[datetime]
    target_moisture: float
    watering_duration: int
    watering_timeout: int
    watering_speed_lph: Optional[float] = None
    light_on_hour: int
    light_off_hour: int
    light_duration: int
    current_version: str
    update_available: bool
    firmware_version: str
    user_id: Optional[int] = None
    plant_ids: list[int] = Field(default_factory=list)


class AdminDeviceOut(DeviceOut):
    """Translitem: rasshirennaya model' dlya admina s dannymi o vlasnike."""

    owner: Optional[DeviceOwnerInfo] = None


class AssignToMeIn(BaseModel):
    """Translitem: payload dlya privyazki svobodnogo ustrojstva k sebe."""

    device_id: int


class AdminAssignIn(BaseModel):
    """Translitem: payload dlya adminskogo priznacheniya ustrojstva."""

    user_id: int

