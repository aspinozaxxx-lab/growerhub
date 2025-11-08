from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, Text, UniqueConstraint, Index
from sqlalchemy.orm import declarative_base
from pydantic import BaseModel
from datetime import datetime
from typing import Optional

Base = declarative_base()

# Основная модель устройства
class DeviceDB(Base):
    __tablename__ = "devices"
    
    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, unique=True, index=True)
    name = Column(String, default="Grovika Device")
    soil_moisture = Column(Float, default=0.0)
    air_temperature = Column(Float, default=0.0)
    air_humidity = Column(Float, default=0.0)
    is_watering = Column(Boolean, default=False)
    is_light_on = Column(Boolean, default=False)
    last_watering = Column(DateTime, nullable=True)
    last_seen = Column(DateTime, default=datetime.utcnow)
    
    # Настройки полива
    target_moisture = Column(Float, default=40.0)
    watering_duration = Column(Integer, default=30)
    watering_timeout = Column(Integer, default=300)
    
    # Настройки освещения
    light_on_hour = Column(Integer, default=6)
    light_off_hour = Column(Integer, default=22)
    light_duration = Column(Integer, default=16)
    
    # OTA обновления
    current_version = Column(String, default="1.0.0")
    latest_version = Column(String, default="1.0.0")
    update_available = Column(Boolean, default=False)
    firmware_url = Column(String, nullable=True)

# Модель для данных сенсоров
class SensorDataDB(Base):
    __tablename__ = "sensor_data"
    
    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String, index=True)
    timestamp = Column(DateTime, default=datetime.utcnow, index=True)
    soil_moisture = Column(Float)
    air_temperature = Column(Float)
    air_humidity = Column(Float)

# Модель для логов полива
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

# Pydantic модели
class DeviceSettings(BaseModel):
    target_moisture: float
    watering_duration: int
    watering_timeout: int
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
