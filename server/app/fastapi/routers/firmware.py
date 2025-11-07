from __future__ import annotations

import hashlib
import os
import shutil
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from pydantic import BaseModel, root_validator
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.database_models import DeviceDB
from app.mqtt.interfaces import IMqttPublisher
from app.mqtt.lifecycle import get_publisher
from app.mqtt.serialization import CmdOta
from config import get_settings

router = APIRouter()

SERVER_DIR = Path(__file__).resolve().parents[3]
FIRMWARE_DIR = SERVER_DIR / "firmware_binaries"
FIRMWARE_DIR.mkdir(exist_ok=True, parents=True)


class TriggerFirmwareUpdateRequest(BaseModel):
    """Payload dlya zapuska OTA: novyj format version + sovmestimost s firmware_version."""

    version: str | None = None
    firmware_version: str | None = None
    device_id: str | None = None  # translitem: dopuskaem staruyu formu, no ignoriruem

    @root_validator(skip_on_failure=True)
    def _ensure_version(cls, values: dict) -> dict:
        candidate = values.get("version") or values.get("firmware_version")
        if not candidate:
            raise ValueError("version required")
        values["_resolved_version"] = candidate
        return values

    @property
    def resolved_version(self) -> str:
        return self.__dict__["_resolved_version"]


def get_mqtt_dep() -> IMqttPublisher:
    """Vozvrashaet MQTT publisher ili 503 pri oshibke."""

    try:
        return get_publisher()
    except RuntimeError as exc:  # pragma: no cover - sohranyaem edinuyu obrabotku oshibok
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="MQTT publisher unavailable") from exc


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


@router.post("/api/device/{device_id}/trigger-update", status_code=status.HTTP_202_ACCEPTED)
async def trigger_ota_update(
    device_id: str,
    update_request: TriggerFirmwareUpdateRequest,
    db: Session = Depends(get_db),
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
):
    device = db.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    version = update_request.resolved_version
    firmware_path = FIRMWARE_DIR / f"{version}.bin"
    if not firmware_path.exists():
        raise HTTPException(status_code=404, detail="firmware not found")

    sha256_hex = _sha256_file(firmware_path)
    base_url = getattr(get_settings(), "SERVER_PUBLIC_BASE_URL", "https://growerhub.ru")
    firmware_url = _build_firmware_url(base_url, version)

    cmd = CmdOta(url=firmware_url, version=version, sha256=sha256_hex)
    try:
        publisher.publish_cmd(device_id, cmd)
    except Exception as exc:  # pragma: no cover - obshchaya obrabotka oshibok publish
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="mqtt publish failed") from exc

    device.update_available = True
    device.latest_version = version
    device.firmware_url = firmware_url
    db.commit()

    return {"result": "accepted", "version": version, "url": firmware_url, "sha256": sha256_hex}


def _sha256_file(path: Path) -> str:
    """Schitaet sha256 ot faila blokami."""

    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _build_firmware_url(base_url: str, version: str) -> str:
    """Formiruet absolyutnyj HTTPS URL do firmware."""

    prefix = base_url.rstrip("/")
    return f"{prefix}/firmware/{version}.bin"

