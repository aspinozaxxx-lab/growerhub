from __future__ import annotations

from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.main
from app.fastapi.routers import devices as devices_router
from app.fastapi.routers import manual_watering as manual_watering_router
from app.models.database_models import Base, DeviceDB
from app.repositories import state_repo
from app.repositories.state_repo import DeviceStateLastRepository


engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Translitem: proveryaem, chto shema sozdana pered obrashcheniem."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Translitem: dependency override na in-memory sessiyu."""

    _create_tables()
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


@pytest.fixture(autouse=True)
def setup_db(monkeypatch):
    """Translitem: zamena SessionLocal i FastAPI zavisimostey na testovye, sozdanie tablits."""

    original_session_local = state_repo.SessionLocal
    state_repo.SessionLocal = TestingSessionLocal
    _create_tables()

    app.main.app.dependency_overrides[manual_watering_router.get_db] = _get_db
    app.main.app.dependency_overrides[devices_router.get_db] = _get_db

    try:
        yield
    finally:
        state_repo.SessionLocal = original_session_local
        app.main.app.dependency_overrides.pop(manual_watering_router.get_db, None)
        app.main.app.dependency_overrides.pop(devices_router.get_db, None)


def _insert_device(device_id: str) -> None:
    """Translitem: dobavlyaem ustroystvo v testovuyu bazu."""

    _create_tables()
    session = TestingSessionLocal()
    try:
        device = DeviceDB(
            device_id=device_id,
            name=f"Device {device_id}",
            soil_moisture=10.0,
            air_temperature=20.0,
            air_humidity=40.0,
            is_watering=False,
            is_light_on=False,
            last_watering=None,
            last_seen=None,
        )
        session.merge(device)
        session.commit()
    finally:
        session.close()


def _upsert_state(device_id: str, state: dict) -> None:
    """Translitem: helper dlya zapisi state_json s metkoy vremeni."""

    repo = DeviceStateLastRepository()
    repo.upsert_state(
        device_id=device_id,
        state=state,
        updated_at=datetime.utcnow().replace(tzinfo=timezone.utc),
    )


def _get_device_entry(client: TestClient, device_id: str) -> dict:
    """Translitem: vydergivaem konkretnoe ustroystvo iz JSON otveta API."""

    resp = client.get("/api/devices")
    assert resp.status_code == 200
    devices = resp.json()
    entry = next((item for item in devices if item["device_id"] == device_id), None)
    assert entry is not None
    return entry


def test_devices_endpoint_returns_fw_ver_when_present(setup_db):
    device_id = "fw-new"
    _insert_device(device_id)
    _upsert_state(
        device_id,
        {
            "manual_watering": {"status": "idle"},
            "fw": "grovika-alpha1",
            "fw_ver": "1.2.3",
        },
    )

    with TestClient(app.main.app) as client:
        entry = _get_device_entry(client, device_id)

    assert entry["firmware_version"] == "1.2.3"


def test_devices_endpoint_marks_old_when_no_fw_ver(setup_db):
    device_id = "fw-old"
    _insert_device(device_id)
    _upsert_state(
        device_id,
        {
            "manual_watering": {"status": "idle"},
            "fw": "grovika-alpha1",
        },
    )

    with TestClient(app.main.app) as client:
        entry = _get_device_entry(client, device_id)

    assert entry["firmware_version"] == "old"
