from __future__ import annotations

import sys
import types
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.main
from app.fastapi.routers import devices as devices_router
from app.fastapi.routers import manual_watering as manual_watering_router
from app.models.database_models import Base, DeviceDB
from app.mqtt.store import init_shadow_store, shutdown_shadow_store
from app.repositories import state_repo
from app.repositories.state_repo import DeviceStateLastRepository
from app.repositories.users import create_local_user
from app.core.security import create_access_token


engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Translitem: sozdaem shemu v in-memory sqlite dlya testov vosstanovleniya."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Translitem: dependency override dlya FastAPI, rabotaem s odnoj sessiyey."""

    _create_tables()
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


@pytest.fixture(autouse=True)
def setup_db(monkeypatch):
    """Translitem: podmenyaem SessionLocal v repo i FastAPI zavisimosti."""

    original_session_local = state_repo.SessionLocal
    state_repo.SessionLocal = TestingSessionLocal
    _create_tables()

    # Override FastAPI zavisimosti get_db na nas testovyy generator
    app.main.app.dependency_overrides[manual_watering_router.get_db] = _get_db
    app.main.app.dependency_overrides[devices_router.get_db] = _get_db
    import app.core.database as database_module  # noqa: WPS433
    import app.core.security as security_module  # noqa: WPS433

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    app.main.app.dependency_overrides[database_module.get_db] = _get_db
    app.main.app.dependency_overrides[security_module.get_db] = _get_db

    try:
        yield
    finally:
        state_repo.SessionLocal = original_session_local
        app.main.app.dependency_overrides.pop(manual_watering_router.get_db, None)
        app.main.app.dependency_overrides.pop(devices_router.get_db, None)
        app.main.app.dependency_overrides.pop(database_module.get_db, None)
        app.main.app.dependency_overrides.pop(security_module.get_db, None)


def _insert_device(device_id: str, user_id: int | None = None) -> None:
    """Translitem: dobavlyaem zapis v DeviceDB dlya endpointa /api/devices."""

    _create_tables()
    session = TestingSessionLocal()
    try:
        device = session.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
        if device is None:
            device = DeviceDB(
                device_id=device_id,
                name="Persisted Device",
                soil_moisture=12.5,
                air_temperature=22.5,
                air_humidity=55.4,
                is_watering=False,
                is_light_on=False,
                last_watering=None,
                last_seen=None,
            )
            session.add(device)
        device.user_id = user_id
        session.commit()
    finally:
        session.close()


def _create_user(email: str, password: str) -> int:
    """Translitem: sozdaem polzovatelya v lokalnoj test-baze."""

    _create_tables()
    session = TestingSessionLocal()
    try:
        user = create_local_user(session, email, None, "user", password)
        session.refresh(user)
        return user.id
    finally:
        session.close()


def test_state_restored_from_db_without_shadow(setup_db):
    device_id = "persist-db-state"
    _create_tables()
    repo = DeviceStateLastRepository()
    updated_at = datetime.utcnow().replace(microsecond=0, tzinfo=timezone.utc)
    state_payload = {
        "manual_watering": {
            "status": "running",
            "duration_s": 30,
            "started_at": updated_at.isoformat().replace("+00:00", "Z"),
            "remaining_s": 20,
            "correlation_id": "corr-db",
        },
        "fw": "1.2.3",
    }
    repo.upsert_state(device_id, state_payload, updated_at)
    owner_id = _create_user("owner@example.com", "secret")
    _insert_device(device_id, user_id=owner_id)

    # Translitem: imitiruem restart — ochishaem shadow store, no BD ostavlyaem.
    shutdown_shadow_store()
    init_shadow_store()

    with TestClient(app.main.app) as client:
        token = create_access_token({"user_id": owner_id})
        headers = {"Authorization": f"Bearer {token}"}
        status_resp = client.get("/api/manual-watering/status", params={"device_id": device_id}, headers=headers)
        assert status_resp.status_code == 200
        status_data = status_resp.json()
        assert status_data["source"] == "db_state"
        assert status_data["status"] == "running"
        assert status_data["correlation_id"] == "corr-db"
        assert status_data["is_online"] in {True, False}

        devices_resp = client.get("/api/devices")
        assert devices_resp.status_code == 200
        devices_list = devices_resp.json()
        persisted = next((item for item in devices_list if item["device_id"] == device_id), None)
        assert persisted is not None
        assert persisted["is_watering"] is True
        # Translitem: last_seen ispolzuet updated_at iz BD state
        assert persisted["last_seen"] is not None
