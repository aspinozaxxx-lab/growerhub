from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel

from app.models.device_schemas import DeviceOut


class PlantGroupCreate(BaseModel):
    """Translitem: sozdat' gruppu rastenij."""

    name: str


class PlantGroupOut(BaseModel):
    """Translitem: gruppa rastenij v otvetah API."""

    id: int
    name: str
    user_id: Optional[int] = None


class PlantCreate(BaseModel):
    """Translitem: sozdanie rastenija."""

    name: str
    planted_at: Optional[datetime] = None
    plant_group_id: Optional[int] = None


class PlantUpdate(BaseModel):
    """Translitem: obnovlenie rastenija."""

    name: Optional[str] = None
    planted_at: Optional[datetime] = None
    plant_group_id: Optional[int] = None


class PlantOut(BaseModel):
    """Translitem: rasshirennoe opisanie rastenija."""

    id: int
    name: str
    planted_at: datetime
    user_id: Optional[int] = None
    plant_group: Optional[PlantGroupOut] = None
    devices: list[DeviceOut] = []


class PlantJournalPhotoOut(BaseModel):
    """Translitem: opisanie foto v zhurnale."""

    id: int
    url: Optional[str] = None
    caption: Optional[str] = None
    has_data: bool = False


class PlantJournalEntryCreate(BaseModel):
    """Translitem: sozdanie zapisi zhurnala rastenija."""

    type: Literal["watering", "feeding", "note", "photo", "other"]
    text: Optional[str] = None
    event_at: Optional[datetime] = None
    photo_urls: Optional[list[str]] = None


class PlantJournalWateringDetailsOut(BaseModel):
    """Translitem: detali poliva v zhurnale (metadannye, bez binarnyh dannyh)."""

    water_volume_l: Optional[float] = None
    duration_s: Optional[int] = None
    ph: Optional[float] = None
    fertilizers_per_liter: Optional[str] = None


class PlantJournalEntryOut(BaseModel):
    """Translitem: zapis' zhurnala v otvete."""

    id: int
    plant_id: int
    user_id: Optional[int] = None
    type: str
    text: Optional[str] = None
    event_at: datetime
    created_at: datetime
    photos: list[PlantJournalPhotoOut] = []
    watering_details: Optional[PlantJournalWateringDetailsOut] = None


class AdminPlantOut(BaseModel):
    """Translitem: zapis' rastenija dlya admina."""

    id: int
    name: str
    owner_email: Optional[str] = None
    owner_username: Optional[str] = None
    owner_id: Optional[int] = None
    group_name: Optional[str] = None
