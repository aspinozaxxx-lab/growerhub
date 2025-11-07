import hashlib
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.fastapi.routers import firmware as firmware_router
from app.main import app, remount_firmware_static
from app.models.database_models import Base, DeviceDB
from app.core.database import get_db
from app.mqtt.serialization import CmdOta
from config import Settings, get_settings


class RecordingPublisher:
    """Fiksiruet otpavlennye OTA komandy."""

    def __init__(self) -> None:
        self.calls: list[tuple[str, CmdOta]] = []

    def publish_cmd(self, device_id: str, cmd: CmdOta) -> None:
        self.calls.append((device_id, cmd))


@pytest.fixture()
def client_with_fs(tmp_path):
    """Nastrojka app na vremennyj katalog firmware dlya testov."""

    settings = Settings(
        SERVER_PUBLIC_BASE_URL="https://example.com",
        FIRMWARE_BINARIES_DIR=str(tmp_path),
    )
    app.dependency_overrides[get_settings] = lambda: settings
    remount_firmware_static(settings)
    with TestClient(app) as client:
        yield client, settings
    app.dependency_overrides.pop(get_settings, None)
    remount_firmware_static()


@pytest.fixture()
def sqlite_db_override():
    """Sozdaet sqlite v pamyati i pereopredelyaet get_db."""

    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)

    def _override_db():
        db = SessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = _override_db
    app.dependency_overrides[firmware_router.get_db] = _override_db
    yield SessionLocal
    app.dependency_overrides.pop(get_db, None)
    app.dependency_overrides.pop(firmware_router.get_db, None)


def test_upload_trigger_and_serve(client_with_fs, sqlite_db_override):
    """Proveryaem polnyj cikl: upload -> trigger-update -> vydacha statiki."""

    client, settings = client_with_fs
    version = "9.9.9"
    data = b"firmware-bytes"
    files = {"file": ("firmware.bin", data, "application/octet-stream")}

    resp = client.post("/api/upload-firmware", files=files, data={"version": version})
    assert resp.status_code == 201
    payload = resp.json()
    dst = Path(payload["path"])
    assert dst.exists()
    assert dst.read_bytes() == data

    # Sozdaem ustroistvo v sqlite-baze
    session_factory = sqlite_db_override
    session = session_factory()
    session.add(DeviceDB(device_id="dev-001"))
    session.commit()
    session.close()

    expected_sha = hashlib.sha256(data).hexdigest()
    publisher = RecordingPublisher()
    app.dependency_overrides[firmware_router.get_mqtt_dep] = lambda: publisher

    resp_trigger = client.post("/api/device/dev-001/trigger-update", json={"version": version})
    app.dependency_overrides.pop(firmware_router.get_mqtt_dep, None)

    assert resp_trigger.status_code == 202
    trigger_payload = resp_trigger.json()
    assert trigger_payload == {
        "result": "accepted",
        "version": version,
        "url": f"https://example.com/firmware/{version}.bin",
        "sha256": expected_sha,
    }
    assert len(publisher.calls) == 1
    device_id, cmd = publisher.calls[0]
    assert device_id == "dev-001"
    assert isinstance(cmd, CmdOta)
    assert cmd.sha256 == expected_sha
    assert cmd.url == f"https://example.com/firmware/{version}.bin"

    resp_static = client.get(f"/firmware/{version}.bin")
    assert resp_static.status_code == 200
    assert resp_static.content == data


def test_upload_permission_error_returns_500(client_with_fs, monkeypatch):
    """Esli net prav zapisi, poluchaem 500 i soobshchenie permission denied."""

    client, settings = client_with_fs
    version = "1.1.1"
    target = Path(settings.FIRMWARE_BINARIES_DIR) / f"{version}.bin"
    original_open = Path.open

    def _deny_open(self, mode="r", *args, **kwargs):
        if self == target and "w" in mode:
            raise PermissionError("denied")
        return original_open(self, mode, *args, **kwargs)

    monkeypatch.setattr(Path, "open", _deny_open)

    app.dependency_overrides[firmware_router.get_mqtt_dep] = lambda: RecordingPublisher()
    files = {"file": ("firmware.bin", b"data", "application/octet-stream")}
    resp = client.post("/api/upload-firmware", files=files, data={"version": version})
    app.dependency_overrides.pop(firmware_router.get_mqtt_dep, None)
    assert resp.status_code == 500
    assert resp.json()["detail"] == "permission denied"
    assert not target.exists()
