from __future__ import annotations

from datetime import datetime, timedelta
import math

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.database_models import SensorDataDB, SensorDataPoint, WateringLogDB

router = APIRouter()

MAX_HISTORY_POINTS = 200
PHYSICAL_BOUNDS = {
    "air_temperature": (-20.0, 60.0),
    "air_humidity": (0.0, 100.0),
    "soil_moisture": (0.0, 100.0),
}
OUTLIER_SIGMA = 4.0  # anti-garbage filter: cut points that fly too far from mean


def _mean_std(values: list[float]) -> tuple[float | None, float | None]:
    """Return mean and std for non-empty list; None if not enough data."""

    if not values:
        return None, None
    mean = sum(values) / len(values)
    variance = sum((val - mean) ** 2 for val in values) / len(values)
    return mean, math.sqrt(variance)


def _filter_outliers(points: list[dict]) -> list[dict]:
    """Filter physically impossible values and extreme statistical outliers."""

    # Physical bounds first: drop entire point if any metric is way off.
    physically_ok: list[dict] = []
    for point in points:
        bad = False
        for key, (low, high) in PHYSICAL_BOUNDS.items():
            value = point.get(key)
            if value is None:
                continue
            if value < low or value > high:
                bad = True
                break
        if not bad:
            physically_ok.append(point)

    # Statistical filter: drop points far from mean (mean ± OUTLIER_SIGMA * std).
    temp_vals = [p["air_temperature"] for p in physically_ok if p.get("air_temperature") is not None]
    hum_vals = [p["air_humidity"] for p in physically_ok if p.get("air_humidity") is not None]
    soil_vals = [p["soil_moisture"] for p in physically_ok if p.get("soil_moisture") is not None]

    temp_mean, temp_std = _mean_std(temp_vals)
    hum_mean, hum_std = _mean_std(hum_vals)
    soil_mean, soil_std = _mean_std(soil_vals)

    def _within_band(value: float | None, mean: float | None, std: float | None) -> bool:
        if value is None or mean is None or std is None or std == 0:
            return True
        return abs(value - mean) <= OUTLIER_SIGMA * std

    filtered: list[dict] = []
    for point in physically_ok:
        if not _within_band(point.get("air_temperature"), temp_mean, temp_std):
            continue
        if not _within_band(point.get("air_humidity"), hum_mean, hum_std):
            continue
        if not _within_band(point.get("soil_moisture"), soil_mean, soil_std):
            continue
        filtered.append(point)

    return filtered


def _downsample(points: list[dict], max_points: int = MAX_HISTORY_POINTS) -> list[dict]:
    """Keep chronological order while reducing count to <= max_points."""

    if len(points) <= max_points:
        return points
    step = math.ceil(len(points) / max_points)
    return points[::step]


@router.get("/api/device/{device_id}/sensor-history")
async def get_sensor_history(device_id: str, hours: int = 24, db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(hours=hours)
    data = db.query(SensorDataDB).filter(
        SensorDataDB.device_id == device_id,
        SensorDataDB.timestamp >= since,
    ).order_by(SensorDataDB.timestamp).all()

    raw_points = [
        {
            "timestamp": item.timestamp,
            "soil_moisture": item.soil_moisture,
            "air_temperature": item.air_temperature,
            "air_humidity": item.air_humidity,
        }
        for item in data
    ]

    filtered = _filter_outliers(raw_points)
    sampled = _downsample(filtered, MAX_HISTORY_POINTS)

    return [
        SensorDataPoint(
            timestamp=item["timestamp"],
            soil_moisture=item["soil_moisture"],
            air_temperature=item["air_temperature"],
            air_humidity=item["air_humidity"],
        )
        for item in sampled
    ]


@router.get("/api/device/{device_id}/watering-logs")
async def get_watering_logs(device_id: str, days: int = 7, db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(days=days)
    logs = db.query(WateringLogDB).filter(
        WateringLogDB.device_id == device_id,
        WateringLogDB.start_time >= since,
    ).order_by(WateringLogDB.start_time.desc()).all()

    return logs

