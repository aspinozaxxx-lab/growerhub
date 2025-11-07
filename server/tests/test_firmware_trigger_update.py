import hashlib
import os
import sys
import types

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.models.database_models import Base, DeviceDB
from config import Settings, get_settings


engine = create_engine(
    "sqlite://",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Sozdaet shemu v sqlite dlya testov."""

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)


def _get_db():
    """Generator dlya zavisimosti FastAPI (sqlite)."""

    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


stub_database = types.ModuleType("app.core.database")
stub_database.engine = engine
stub_database.SessionLocal = SessionLocal
stub_database.create_tables = _create_tables
stub_database.get_db = _get_db
sys.modules["app.core.database"] = stub_database

from app.fastapi.routers import firmware as firmware_router
from app.fastapi.routers.firmware import get_mqtt_dep
from app.main import app
from app.mqtt.interfaces import IMqttPublisher
from app.mqtt.serialization import CmdOta
from app.core.database import get_db as app_get_db


class FakePublisher(IMqttPublisher):
    """Zamenitel MQTT publishera dlya kontrolya payloadov."""

    def __init__(self, should_fail: bool = False) -> None:
        self.should_fail = should_fail
        self.calls: list[tuple[str, CmdOta]] = []

    def publish_cmd(self, device_id: str, cmd: CmdOta) -> None:
        if self.should_fail:
            raise RuntimeError("mqtt down")
        self.calls.append((device_id, cmd))


@pytest.fixture(autouse=True)
def setup_db():
    """Ochishchaet i sozdaet tablicy pered kazhdym testom."""

    _create_tables()
    app.dependency_overrides[app_get_db] = _get_db
    app.dependency_overrides[firmware_router.get_db] = _get_db
    try:
        yield
    finally:
        app.dependency_overrides.pop(app_get_db, None)
        app.dependency_overrides.pop(firmware_router.get_db, None)


@pytest.fixture()
def firmware_dir(tmp_path):
    """Pereopredelyaet katalog firmware i public URL cherez dependency_override."""

    tmp_path.mkdir(parents=True, exist_ok=True)
    test_settings = Settings(
        SERVER_PUBLIC_BASE_URL="https://example.com",
        FIRMWARE_BINARIES_DIR=str(tmp_path),
    )
    app.dependency_overrides[get_settings] = lambda: test_settings
    try:
        yield tmp_path
    finally:
        app.dependency_overrides.pop(get_settings, None)


def _insert_device(device_id: str = "dev-001") -> None:
    session = SessionLocal()
    session.add(DeviceDB(device_id=device_id))
    session.commit()
    session.close()


def _build_client(fake: IMqttPublisher) -> TestClient:
    app.dependency_overrides[get_mqtt_dep] = lambda: fake
    client = TestClient(app)
    return client


def _cleanup_client(client: TestClient) -> None:
    client.close()
    app.dependency_overrides.pop(get_mqtt_dep, None)


def test_trigger_update_happy_path(firmware_dir):
    """Happy-path: fail nalichen, otpravlyaem cmd/ota i vozvrashaem 202."""

    _insert_device()
    data = os.urandom(32768)
    version = "1.2.3"
    firmware_path = firmware_dir / f"{version}.bin"
    firmware_path.write_bytes(data)
    expected_sha = hashlib.sha256(data).hexdigest()

    fake = FakePublisher()
    client = _build_client(fake)

    resp = client.post("/api/device/dev-001/trigger-update", json={"version": version})

    _cleanup_client(client)

    assert resp.status_code == 202
    payload = resp.json()
    assert payload == {
        "result": "accepted",
        "version": version,
        "url": f"https://example.com/firmware/{version}.bin",
        "sha256": expected_sha,
    }

    assert len(fake.calls) == 1
    device_id, cmd = fake.calls[0]
    assert device_id == "dev-001"
    assert isinstance(cmd, CmdOta)
    assert cmd.url == f"https://example.com/firmware/{version}.bin"
    assert cmd.version == version
    assert cmd.sha256 == expected_sha

    session = SessionLocal()
    db_device = session.query(DeviceDB).filter_by(device_id="dev-001").first()
    session.close()
    assert db_device is not None and db_device.latest_version == version
    assert db_device.firmware_url == f"https://example.com/firmware/{version}.bin"


def test_trigger_update_returns_404_when_file_missing(firmware_dir):
    """Esli fajla net, poluchaem 404 i MQTT ne vyzyvaetsya."""

    _insert_device()
    fake = FakePublisher()
    client = _build_client(fake)

    resp = client.post("/api/device/dev-001/trigger-update", json={"version": "9.9.9"})

    _cleanup_client(client)
    assert resp.status_code == 404
    assert resp.json()["detail"] == "firmware not found"
    assert fake.calls == []


def test_trigger_update_returns_503_when_mqtt_fails(firmware_dir):
    """Esli publish padaet, vozvrashaem 503 i oshibku mqtt."""

    _insert_device()
    version = "2.0.0"
    firmware_path = firmware_dir / f"{version}.bin"
    firmware_path.write_bytes(b"firmware-bytes")

    fake = FakePublisher(should_fail=True)
    client = _build_client(fake)

    resp = client.post("/api/device/dev-001/trigger-update", json={"version": version})

    _cleanup_client(client)

    assert resp.status_code == 503
    assert resp.json()["detail"] == "mqtt publish failed"
