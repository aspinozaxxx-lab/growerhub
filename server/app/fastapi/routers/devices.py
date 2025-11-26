from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import get_current_admin, get_current_user
from app.models.database_models import (
    DeviceDB,
    DeviceSettings,
    DeviceStatus,
    SensorDataDB,
    SensorDataPoint,
    UserDB,
    WateringLogDB,
    PlantDeviceDB,
)
from app.models.device_schemas import (
    AdminAssignIn,
    AdminDeviceOut,
    AssignToMeIn,
    DeviceOut,
    DeviceOwnerInfo,
)
from app.repositories.state_repo import DeviceStateLastRepository

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
    state_repo = DeviceStateLastRepository()

    return [
        _device_to_out(device, state_repo, current_time, online_window, db).model_dump()
        for device in devices
    ]


@router.get("/api/devices/my", response_model=list[DeviceOut])
async def get_my_devices(
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    devices = db.query(DeviceDB).filter(DeviceDB.user_id == current_user.id).all()
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    state_repo = DeviceStateLastRepository()

    return [
        _device_to_out(device, state_repo, current_time, online_window, db)
        for device in devices
    ]


@router.get("/api/admin/devices", response_model=list[AdminDeviceOut])
async def admin_list_devices(
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
):
    rows = (
        db.query(DeviceDB, UserDB)
        .outerjoin(UserDB, DeviceDB.user_id == UserDB.id)
        .all()
    )
    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    state_repo = DeviceStateLastRepository()

    result: list[AdminDeviceOut] = []
    for device, owner in rows:
        device_payload = _device_to_out(device, state_repo, current_time, online_window, db)
        owner_payload = (
            DeviceOwnerInfo(id=owner.id, email=owner.email, username=owner.username)
            if owner
            else None
        )
        result.append(AdminDeviceOut(**device_payload.model_dump(), owner=owner_payload))
    return result


@router.post("/api/devices/assign-to-me", response_model=DeviceOut)
async def assign_device_to_me(
    payload: AssignToMeIn,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    device = db.query(DeviceDB).filter(DeviceDB.id == payload.device_id).first()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    if device.user_id is None:
        device.user_id = current_user.id
    elif device.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="ustrojstvo uzhe privyazano k drugomu polzovatelju",
        )

    db.commit()
    db.refresh(device)

    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    state_repo = DeviceStateLastRepository()
    return _device_to_out(device, state_repo, current_time, online_window, db)


@router.post("/api/devices/{device_id}/unassign", response_model=DeviceOut)
async def unassign_device(
    device_id: int,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    device = db.query(DeviceDB).filter(DeviceDB.id == device_id).first()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    if current_user.role != "admin" and device.user_id != current_user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="nedostatochno prav dlya otvyazki etogo ustrojstva")

    device.user_id = None
    db.commit()
    db.refresh(device)

    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    state_repo = DeviceStateLastRepository()
    return _device_to_out(device, state_repo, current_time, online_window, db)


@router.post("/api/admin/devices/{device_id}/assign", response_model=AdminDeviceOut)
async def admin_assign_device(
    device_id: int,
    payload: AdminAssignIn,
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
):
    device = db.query(DeviceDB).filter(DeviceDB.id == device_id).first()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    user = db.query(UserDB).filter(UserDB.id == payload.user_id).first()
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="polzovatel' ne najden")

    device.user_id = user.id
    db.commit()
    db.refresh(device)

    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    state_repo = DeviceStateLastRepository()
    device_payload = _device_to_out(device, state_repo, current_time, online_window, db)
    owner_payload = DeviceOwnerInfo(id=user.id, email=user.email, username=user.username)
    return AdminDeviceOut(**device_payload.model_dump(), owner=owner_payload)


@router.post("/api/admin/devices/{device_id}/unassign", response_model=AdminDeviceOut)
async def admin_unassign_device(
    device_id: int,
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
):
    device = db.query(DeviceDB).filter(DeviceDB.id == device_id).first()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ustrojstvo ne najdeno")

    device.user_id = None
    db.commit()
    db.refresh(device)

    current_time = datetime.utcnow()
    online_window = timedelta(minutes=3)
    state_repo = DeviceStateLastRepository()
    device_payload = _device_to_out(device, state_repo, current_time, online_window, db)
    return AdminDeviceOut(**device_payload.model_dump(), owner=None)


def _device_to_out(
    device: DeviceDB,
    state_repo: DeviceStateLastRepository,
    current_time: datetime,
    online_window: timedelta,
    db: Session,
) -> DeviceOut:
    """Translitem: dopolnyaem dannye ustrojstva state iz osnovnogo hranilishcha."""

    stored_state = state_repo.get_state(device.device_id)
    firmware_version = "old"
    is_watering = device.is_watering
    last_seen = device.last_seen
    is_online = bool(last_seen and (current_time - last_seen) <= online_window)

    if stored_state:
        payload = stored_state["state"]
        firmware_version = payload.get("fw_ver") or "old"
        manual = payload.get("manual_watering", {})
        status_value = manual.get("status")
        is_watering = status_value == "running" if status_value else device.is_watering
        updated_at = stored_state.get("updated_at")
        if updated_at is not None:
            last_seen = updated_at
            is_online = bool(
                (datetime.utcnow().replace(tzinfo=timezone.utc) - _as_utc(updated_at)).total_seconds()
                <= online_window.total_seconds()
            )

    plant_links = (
        db.query(PlantDeviceDB.plant_id).filter(PlantDeviceDB.device_id == device.id).all()
    )
    plant_ids = [row.plant_id for row in plant_links]

    return DeviceOut(
        id=device.id,
        device_id=device.device_id,
        name=device.name,
        soil_moisture=device.soil_moisture,
        air_temperature=device.air_temperature,
        air_humidity=device.air_humidity,
        is_watering=is_watering,
        is_light_on=device.is_light_on,
        is_online=is_online,
        last_watering=device.last_watering,
        last_seen=last_seen,
        target_moisture=device.target_moisture,
        watering_duration=device.watering_duration,
        watering_timeout=device.watering_timeout,
        light_on_hour=device.light_on_hour,
        light_off_hour=device.light_off_hour,
        light_duration=device.light_duration,
        current_version=device.current_version,
        update_available=device.update_available,
        firmware_version=firmware_version,
        user_id=device.user_id,
        plant_ids=plant_ids,
    )


def _as_utc(dt: datetime) -> datetime:
    """Translitem: privodim datetime k UTC dlya sravneniy."""

    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


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

