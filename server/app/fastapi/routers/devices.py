from __future__ import annotations

from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.database_models import (
    DeviceDB,
    DeviceInfo,
    DeviceSettings,
    DeviceStatus,
    SensorDataDB,
    SensorDataPoint,
    WateringLogDB,
)

router = APIRouter()


@router.post("/api/device/{device_id}/status")
async def update_device_status(device_id: str, status: DeviceStatus, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()

    if not device:
        device = DeviceDB(
            device_id=device_id,
            name=f"Watering Device {device_id}",
            soil_moisture=status.soil_moisture,
            air_temperature=status.air_temperature,
            air_humidity=status.air_humidity,
            is_watering=status.is_watering,
            is_light_on=status.is_light_on,
            last_watering=status.last_watering,
            last_seen=datetime.utcnow(),
        )
        db.add(device)
    else:
        device.soil_moisture = status.soil_moisture
        device.air_temperature = status.air_temperature
        device.air_humidity = status.air_humidity
        device.is_watering = status.is_watering
        device.is_light_on = status.is_light_on
        if status.last_watering:
            device.last_watering = status.last_watering
        device.last_seen = datetime.utcnow()

    sensor_data = SensorDataDB(
        device_id=device_id,
        soil_moisture=status.soil_moisture,
        air_temperature=status.air_temperature,
        air_humidity=status.air_humidity,
    )
    db.add(sensor_data)

    if status.is_watering and not device.is_watering:
        watering_log = WateringLogDB(
            device_id=device_id,
            start_time=datetime.utcnow(),
        )
        db.add(watering_log)

    db.commit()
    return {"message": "Status updated"}


@router.get("/api/device/{device_id}/settings")
async def get_device_settings(device_id: str, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()

    if not device:
        device = DeviceDB(
            device_id=device_id,
            name=f"Watering Device {device_id}",
            last_seen=datetime.utcnow(),
        )
        db.add(device)
        db.commit()

    return {
        "target_moisture": device.target_moisture,
        "watering_duration": device.watering_duration,
        "watering_timeout": device.watering_timeout,
        "light_on_hour": device.light_on_hour,
        "light_off_hour": device.light_off_hour,
        "light_duration": device.light_duration,
        "update_available": device.update_available,
    }


@router.put("/api/device/{device_id}/settings")
async def update_device_settings(device_id: str, settings: DeviceSettings, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    device.target_moisture = settings.target_moisture
    device.watering_duration = settings.watering_duration
    device.watering_timeout = settings.watering_timeout
    device.light_on_hour = settings.light_on_hour
    device.light_off_hour = settings.light_off_hour
    device.light_duration = settings.light_duration

    db.commit()
    return {"message": "Settings updated"}


@router.get("/api/devices")
async def get_all_devices(db: Session = Depends(get_db)):
    devices = db.query(DeviceDB).all()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    return [
        DeviceInfo(
            device_id=device.device_id,
            name=device.name,
            soil_moisture=device.soil_moisture,
            air_temperature=device.air_temperature,
            air_humidity=device.air_humidity,
            is_watering=device.is_watering,
            is_light_on=device.is_light_on,
            is_online=bool(device.last_seen and (current_time - device.last_seen) <= online_window),
            last_watering=device.last_watering,
            last_seen=device.last_seen,
            target_moisture=device.target_moisture,
            watering_duration=device.watering_duration,
            watering_timeout=device.watering_timeout,
            light_on_hour=device.light_on_hour,
            light_off_hour=device.light_off_hour,
            light_duration=device.light_duration,
            current_version=device.current_version,
            update_available=device.update_available,
        )
        for device in devices
    ]


@router.delete("/api/device/{device_id}")
async def delete_device(device_id: str, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    db.query(SensorDataDB).filter(SensorDataDB.device_id == device_id).delete()
    db.query(WateringLogDB).filter(WateringLogDB.device_id == device_id).delete()

    db.delete(device)
    db.commit()
    return {"message": "Device deleted"}

