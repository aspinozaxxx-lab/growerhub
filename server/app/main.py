from fastapi import FastAPI, HTTPException, Depends, UploadFile, File
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
import os
import shutil
from datetime import datetime, timedelta
from pathlib import Path

from app.models.database_models import (DeviceDB, DeviceSettings, DeviceStatus, DeviceInfo, 
                   SensorDataDB, SensorDataPoint, WateringLogDB, OTAUpdateRequest)
from app.core.database import get_db, create_tables

app = FastAPI(title="Smart Watering System")

create_tables()

app.mount("/static", StaticFiles(directory="../static"), name="static")
app.mount("/firmware", StaticFiles(directory="../firmware_binaries"), name="firmware")

Path("../firmware_binaries").mkdir(exist_ok=True)

@app.get("/")
async def read_root():
    return FileResponse("../static/index.html")

@app.post("/api/device/{device_id}/status")
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
            last_seen=datetime.utcnow()
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
    
    # Сохраняем данные сенсоров в историю
    sensor_data = SensorDataDB(
        device_id=device_id,
        soil_moisture=status.soil_moisture,
        air_temperature=status.air_temperature,
        air_humidity=status.air_humidity
    )
    db.add(sensor_data)
    
    # Логируем полив если он только что начался
    if status.is_watering and not device.is_watering:
        watering_log = WateringLogDB(
            device_id=device_id,
            start_time=datetime.utcnow()
        )
        db.add(watering_log)
    
    db.commit()
    return {"message": "Status updated"}

@app.get("/api/device/{device_id}/settings")
async def get_device_settings(device_id: str, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    
    if not device:
        device = DeviceDB(
            device_id=device_id,
            name=f"Watering Device {device_id}",
            last_seen=datetime.utcnow()
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
        "update_available": device.update_available
    }

@app.get("/api/devices")
async def get_all_devices(db: Session = Depends(get_db)):
    devices = db.query(DeviceDB).all()
    return [
        DeviceInfo(
            device_id=device.device_id,
            name=device.name,
            soil_moisture=device.soil_moisture,
            air_temperature=device.air_temperature,
            air_humidity=device.air_humidity,
            is_watering=device.is_watering,
            is_light_on=device.is_light_on,
            last_watering=device.last_watering,
            last_seen=device.last_seen,
            target_moisture=device.target_moisture,
            watering_duration=device.watering_duration,
            watering_timeout=device.watering_timeout,
            light_on_hour=device.light_on_hour,
            light_off_hour=device.light_off_hour,
            light_duration=device.light_duration,
            current_version=device.current_version,
            update_available=device.update_available
        )
        for device in devices
    ]

@app.put("/api/device/{device_id}/settings")
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

@app.get("/api/device/{device_id}/firmware")
async def check_firmware_update(device_id: str, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device or not device.update_available:
        return {"update_available": False}
    
    return {
        "update_available": True,
        "latest_version": device.latest_version,
        "firmware_url": f"http://192.168.0.11/firmware/{device.latest_version}.bin"
    }

@app.post("/api/upload-firmware")
async def upload_firmware(file: UploadFile = File(...), version: str = "1.0.0"):
    file_path = f"../firmware_binaries/{version}.bin"
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    
    return {"message": f"Firmware {version} uploaded successfully", "file_path": file_path}

@app.post("/api/device/{device_id}/trigger-update")
async def trigger_ota_update(device_id: str, update_request: OTAUpdateRequest, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    
    firmware_path = f"../firmware_binaries/{update_request.firmware_version}.bin"
    if not os.path.exists(firmware_path):
        raise HTTPException(status_code=404, detail="Firmware version not found")
    
    device.update_available = True
    device.latest_version = update_request.firmware_version
    device.firmware_url = f"http://192.168.0.11/firmware/{update_request.firmware_version}.bin"
    
    db.commit()
    return {"message": "OTA update triggered", "firmware_url": device.firmware_url}

# Новые API эндпоинты для истории данных
@app.get("/api/device/{device_id}/sensor-history")
async def get_sensor_history(device_id: str, hours: int = 24, db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(hours=hours)
    data = db.query(SensorDataDB).filter(
        SensorDataDB.device_id == device_id,
        SensorDataDB.timestamp >= since
    ).order_by(SensorDataDB.timestamp).all()
    
    return [
        SensorDataPoint(
            timestamp=item.timestamp,
            soil_moisture=item.soil_moisture,
            air_temperature=item.air_temperature,
            air_humidity=item.air_humidity
        )
        for item in data
    ]

@app.get("/api/device/{device_id}/watering-logs")
async def get_watering_logs(device_id: str, days: int = 7, db: Session = Depends(get_db)):
    since = datetime.utcnow() - timedelta(days=days)
    logs = db.query(WateringLogDB).filter(
        WateringLogDB.device_id == device_id,
        WateringLogDB.start_time >= since
    ).order_by(WateringLogDB.start_time.desc()).all()
    
    return logs

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
