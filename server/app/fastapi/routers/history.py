from __future__ import annotations

from datetime import datetime, timedelta

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.database_models import SensorDataDB, SensorDataPoint, WateringLogDB

router = APIRouter()


@router.get("/api/device/{device_id}/sensor-history")
async def get_sensor_history(device_id: str, hours: int = 24, db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(hours=hours)
    data = db.query(SensorDataDB).filter(
        SensorDataDB.device_id == device_id,
        SensorDataDB.timestamp >= since,
    ).order_by(SensorDataDB.timestamp).all()

    return [
        SensorDataPoint(
            timestamp=item.timestamp,
            soil_moisture=item.soil_moisture,
            air_temperature=item.air_temperature,
            air_humidity=item.air_humidity,
        )
        for item in data
    ]


@router.get("/api/device/{device_id}/watering-logs")
async def get_watering_logs(device_id: str, days: int = 7, db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(days=days)
    logs = db.query(WateringLogDB).filter(
        WateringLogDB.device_id == device_id,
        WateringLogDB.start_time >= since,
    ).order_by(WateringLogDB.start_time.desc()).all()

    return logs

