from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, Text, UniqueConstraint, Index, ForeignKey
from sqlalchemy.orm import declarative_base
from pydantic import BaseModel
from datetime import datetime
from typing import Optional

Base = declarative_base()

# РћСЃРЅРѕРІРЅР°СЏ РјРѕРґРµР»СЊ СѓСЃС‚СЂРѕР№СЃС‚РІР°
class DeviceDB(Base):
    __tablename__ = "devices"
    
    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, unique=True, index=True)
    name = Column(String, default="Grovika Device")
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )  # Translitem: eto vladelec ustrojstva
    soil_moisture = Column(Float, default=0.0)
    air_temperature = Column(Float, default=0.0)
    air_humidity = Column(Float, default=0.0)
    is_watering = Column(Boolean, default=False)
    is_light_on = Column(Boolean, default=False)
    last_watering = Column(DateTime, nullable=True)
    last_seen = Column(DateTime, default=datetime.utcnow)
    
    # РќР°СЃС‚СЂРѕР№РєРё РїРѕР»РёРІР°
    target_moisture = Column(Float, default=40.0)
    watering_duration = Column(Integer, default=30)
    watering_timeout = Column(Integer, default=300)
    watering_speed_lph = Column(Float, nullable=True)
    
    # РќР°СЃС‚СЂРѕР№РєРё РѕСЃРІРµС‰РµРЅРёСЏ
    light_on_hour = Column(Integer, default=6)
    light_off_hour = Column(Integer, default=22)
    light_duration = Column(Integer, default=16)
    
    # OTA РѕР±РЅРѕРІР»РµРЅРёСЏ
    current_version = Column(String, default="1.0.0")
    latest_version = Column(String, default="1.0.0")
    update_available = Column(Boolean, default=False)
    firmware_url = Column(String, nullable=True)

# РњРѕРґРµР»СЊ РґР»СЏ РґР°РЅРЅС‹С… СЃРµРЅСЃРѕСЂРѕРІ
class SensorDataDB(Base):
    __tablename__ = "sensor_data"
    
    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, index=True)
    timestamp = Column(DateTime, default=datetime.utcnow, index=True)
    soil_moisture = Column(Float)
    air_temperature = Column(Float)
    air_humidity = Column(Float)

# РњРѕРґРµР»СЊ РґР»СЏ Р»РѕРіРѕРІ РїРѕР»РёРІР°
class WateringLogDB(Base):
    __tablename__ = "watering_logs"
    
    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, index=True)
    start_time = Column(DateTime, default=datetime.utcnow)
    duration = Column(Integer, nullable=True)
    water_used = Column(Float, nullable=True)


class DeviceStateLastDB(Base):
    __tablename__ = "device_state_last"
    __table_args__ = (
        UniqueConstraint("device_id", name="uq_device_state_last_device_id"),
        Index("ix_device_state_last_updated_at", "updated_at"),
    )

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, nullable=False)
    state_json = Column(Text, nullable=False)  # Translitem: syroy json sostoyaniya dlya vosstanovleniya
    updated_at = Column(DateTime, nullable=False, default=datetime.utcnow)  # Translitem: kogda state poluchili


class MqttAckDB(Base):
    __tablename__ = "mqtt_ack"
    __table_args__ = (
        UniqueConstraint("correlation_id", name="uq_mqtt_ack_correlation_id"),
        Index("ix_mqtt_ack_device_id", "device_id"),
        Index("ix_mqtt_ack_received_at", "received_at"),
        Index("ix_mqtt_ack_expires_at", "expires_at"),
    )

    id = Column(Integer, primary_key=True, index=True)
    correlation_id = Column(String, nullable=False)
    device_id = Column(String, nullable=False)
    result = Column(String, nullable=False)
    status = Column(String, nullable=True)
    payload_json = Column(Text, nullable=False)  # Translitem: syroy payload ack dlya audit logov
    received_at = Column(DateTime, nullable=False, default=datetime.utcnow)  # Translitem: metka polucheniya ack
    expires_at = Column(DateTime, nullable=True)  # Translitem: kogda ack nado ochistit TTL mehanizmom


class UserDB(Base):
    """Translitem: tablitsa polzovateley sistemy."""

    __tablename__ = "users"
    __table_args__ = (
        UniqueConstraint("email", name="uq_users_email"),
    )

    id = Column(Integer, primary_key=True)
    email = Column(String, nullable=False, unique=True)  # Translitem: unikalnyj email dlya vhoda
    username = Column(String, nullable=True)
    role = Column(String, nullable=False, default="user")  # Translitem: rol polzovatelya (user/admin i t.d.)
    is_active = Column(Boolean, nullable=False, default=True)  # Translitem: priznak aktivnosti akkaunta
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class UserAuthIdentityDB(Base):
    """Translitem: svyaz polzovatelya s istochnikom autentifikatsii."""

    __tablename__ = "user_auth_identities"
    __table_args__ = (
        UniqueConstraint("provider", "provider_subject", name="uq_user_auth_identities_provider_subject"),
        UniqueConstraint("user_id", "provider", name="uq_user_auth_identities_user_provider"),
    )

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    provider = Column(String, nullable=False)  # Translitem: tip provajdera (local/google/yandex/gosuslugi ...)
    provider_subject = Column(String, nullable=True)  # Translitem: vneshnij identifikator/sub ot provajdera
    password_hash = Column(String, nullable=True)  # Translitem: heslennyj parol tolko dlya provider=local
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class PlantGroupDB(Base):
    """Translitem: gruppa rastenij dlja organizacii po polkam/boksam."""

    __tablename__ = "plant_groups"

    id = Column(Integer, primary_key=True)
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    name = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class PlantDB(Base):
    """Translitem: model' rastenija s privyazkoj k polzovatelyu i gruppe."""

    __tablename__ = "plants"

    id = Column(Integer, primary_key=True)
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    name = Column(String, nullable=False)
    planted_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    plant_group_id = Column(
        Integer,
        ForeignKey("plant_groups.id", ondelete="SET NULL"),
        nullable=True,
    )
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class PlantDeviceDB(Base):
    """Translitem: svyaz rastenija s ustrojstvami."""

    __tablename__ = "plant_devices"
    __table_args__ = (
        UniqueConstraint("plant_id", "device_id", name="uq_plant_device_pair"),
    )

    id = Column(Integer, primary_key=True)
    plant_id = Column(Integer, ForeignKey("plants.id", ondelete="CASCADE"), nullable=False)
    device_id = Column(Integer, ForeignKey("devices.id", ondelete="CASCADE"), nullable=False)


class PlantJournalEntryDB(Base):
    """Translitem: zapis' zhurnala aktivnosti rastenija."""

    __tablename__ = "plant_journal_entries"

    id = Column(Integer, primary_key=True)
    plant_id = Column(Integer, ForeignKey("plants.id", ondelete="CASCADE"), nullable=False)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    type = Column(String, nullable=False)
    text = Column(Text, nullable=True)
    event_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class PlantJournalPhotoDB(Base):
    """Translitem: foto-prilozhenie k zapisjam zhurnala."""

    __tablename__ = "plant_journal_photos"

    id = Column(Integer, primary_key=True)
    journal_entry_id = Column(
        Integer,
        ForeignKey("plant_journal_entries.id", ondelete="CASCADE"),
        nullable=False,
    )
    url = Column(String, nullable=False)
    caption = Column(String, nullable=True)

# Pydantic РјРѕРґРµР»Рё
class DeviceSettings(BaseModel):
    target_moisture: float
    watering_duration: int
    watering_timeout: int
    watering_speed_lph: Optional[float] = None
    light_on_hour: int
    light_off_hour: int
    light_duration: int

class DeviceStatus(BaseModel):
    device_id: str
    soil_moisture: float
    air_temperature: float
    air_humidity: float
    is_watering: bool
    is_light_on: bool
    last_watering: Optional[datetime] = None

class DeviceInfo(BaseModel):
    device_id: str
    name: str
    soil_moisture: float
    air_temperature: float
    air_humidity: float
    is_watering: bool
    is_light_on: bool
    is_online: bool
    last_watering: Optional[datetime]
    last_seen: datetime
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

class SensorDataPoint(BaseModel):
    timestamp: datetime
    soil_moisture: float
    air_temperature: float
    air_humidity: float

class OTAUpdateRequest(BaseModel):
    device_id: str
    firmware_version: str


