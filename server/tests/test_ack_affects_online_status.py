from __future__ import annotations

from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.main
from app.fastapi.routers import devices as devices_router
from app.fastapi.routers import manual_watering as manual_watering_router
from app.models.database_models import Base, DeviceDB
from app.mqtt.config import MqttSettings
from app.mqtt.handlers.ack import handle_ack_message
from app.mqtt.serialization import Ack, AckResult
from app.mqtt.store import AckStore, init_shadow_store, shutdown_shadow_store
from app.repositories import state_repo
from app.repositories.state_repo import DeviceStateLastRepository


engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Translitem: sozdaem shemu dlya testov ACK."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Translitem: dependency override na in-memory sessiyu."""

    _create_tables()
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


def _insert_device(device_id: str, last_seen: datetime | None = None) -> None:
    """Translitem: dobavlyaem ustroystvo v testovuyu bazu."""

    _create_tables()
    session = TestingSessionLocal()
    try:
        device = DeviceDB(
            device_id=device_id,
            name=f"Device {device_id}",
            soil_moisture=0.0,
            air_temperature=0.0,
            air_humidity=0.0,
            is_watering=False,
            is_light_on=False,
            last_watering=None,
            last_seen=last_seen,
        )
        session.merge(device)
        session.commit()
    finally:
        session.close()


def _mqtt_settings(debug: bool = False) -> MqttSettings:
    """Translitem: formiruem prostye MQTT nastroiki dlya handlera."""

    return MqttSettings(
        host="localhost",
        port=1883,
        username=None,
        password=None,
        tls=False,
        client_id_prefix="test-ack",
        debug=debug,
        device_online_threshold_s=60,
        ack_ttl_seconds=180,
        ack_cleanup_period_seconds=60,
    )


def _fire_ack(device_id: str, correlation_id: str) -> None:
    """Translitem: vyzyvaem ACK handler tak zhe, kak eto proishodit iz MQTT."""

    payload = (
        Ack(correlation_id=correlation_id, result=AckResult.accepted)
        .model_dump_json(by_alias=True, exclude_none=True)
        .encode("utf-8")
    )
    handle_ack_message(
        settings=_mqtt_settings(),
        store=AckStore(),
        topic=f"gh/dev/{device_id}/state/ack",
        payload=payload,
    )


@pytest.fixture(autouse=True)
def setup_db(monkeypatch):
    """Translitem: obshchaya nastroyka testov (SessionLocal, zavisimosti FastAPI, shadow-store)."""

    original_session_local = state_repo.SessionLocal
    state_repo.SessionLocal = TestingSessionLocal
    _create_tables()

    app.main.app.dependency_overrides[manual_watering_router.get_db] = _get_db
    app.main.app.dependency_overrides[devices_router.get_db] = _get_db

    shutdown_shadow_store()
    init_shadow_store()

    try:
        yield
    finally:
        state_repo.SessionLocal = original_session_local
        app.main.app.dependency_overrides.pop(manual_watering_router.get_db, None)
        app.main.app.dependency_overrides.pop(devices_router.get_db, None)
        shutdown_shadow_store()
        init_shadow_store()


def test_ack_updates_device_state_last_when_no_prior_state():
    """Translitem: esli state otsutstvuet, ACK sozdaet minimal'nuyu zapis' i obnovaet updated_at."""

    device_id = "ack-touch-only"
    _insert_device(device_id)
    repo = DeviceStateLastRepository()
    assert repo.get_state(device_id) is None

    before = datetime.utcnow()
    _fire_ack(device_id=device_id, correlation_id="corr-touch")
    after = datetime.utcnow()

    state = repo.get_state(device_id)
    assert state is not None
    assert state["state"] == {}
    updated_at = state["updated_at"]
    assert before <= updated_at <= after + timedelta(seconds=1)


def test_ack_extends_online_window_in_devices_endpoint():
    """Translitem: ACK bez state delat ustroystvo onlayn v API /api/devices."""

    device_id = "ack-devices-online"
    stale_seen = datetime.utcnow() - timedelta(minutes=10)
    _insert_device(device_id, last_seen=stale_seen)

    _fire_ack(device_id=device_id, correlation_id="corr-devices")

    with TestClient(app.main.app) as client:
        response = client.get("/api/devices")

    assert response.status_code == 200
    devices = response.json()
    entry = next((item for item in devices if item["device_id"] == device_id), None)
    assert entry is not None
    assert entry["is_online"] is True
    assert entry["last_seen"] is not None


def test_ack_extends_online_in_manual_watering_status():
    """Translitem: ACK probyvaet puls dlya endpointa /api/manual-watering/status."""

    device_id = "ack-manual-online"
    _insert_device(device_id)

    _fire_ack(device_id=device_id, correlation_id="corr-manual")

    with TestClient(app.main.app) as client:
        response = client.get("/api/manual-watering/status", params={"device_id": device_id})

    assert response.status_code == 200
    data = response.json()
    assert data["is_online"] is True
    assert data["offline_reason"] is None
    assert data["last_seen_at"] is not None
    assert data["source"] == "db_state"
