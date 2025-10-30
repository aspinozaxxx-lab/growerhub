from __future__ import annotations

import os
import shutil
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.database_models import DeviceDB, OTAUpdateRequest

router = APIRouter()

SERVER_DIR = Path(__file__).resolve().parents[3]
FIRMWARE_DIR = SERVER_DIR / "firmware_binaries"
FIRMWARE_DIR.mkdir(exist_ok=True, parents=True)


@router.get("/api/device/{device_id}/firmware")
async def check_firmware_update(device_id: str, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device or not device.update_available:
        return {"update_available": False}

    return {
        "update_available": True,
        "latest_version": device.latest_version,
        "firmware_url": f"http://192.168.0.11/firmware/{device.latest_version}.bin",
    }


@router.post("/api/upload-firmware")
async def upload_firmware(file: UploadFile = File(...), version: str = "1.0.0"):
    file_path = FIRMWARE_DIR / f"{version}.bin"
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    return {"message": f"Firmware {version} uploaded successfully", "file_path": file_path}


@router.post("/api/device/{device_id}/trigger-update")
async def trigger_ota_update(device_id: str, update_request: OTAUpdateRequest, db: Session = Depends(get_db)):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    firmware_path = FIRMWARE_DIR / f"{update_request.firmware_version}.bin"
    if not os.path.exists(firmware_path):
        raise HTTPException(status_code=404, detail="Firmware version not found")

    device.update_available = True
    device.latest_version = update_request.firmware_version
    device.firmware_url = f"http://192.168.0.11/firmware/{update_request.firmware_version}.bin"

    db.commit()
    return {"message": "OTA update triggered", "firmware_url": device.firmware_url}

