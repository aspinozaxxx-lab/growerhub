from __future__ import annotations

from datetime import datetime, timedelta

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.models.database_models import Base
from app.repositories import state_repo
from app.repositories.state_repo import DeviceStateLastRepository, MqttAckRepository


@pytest.fixture(scope="module", autouse=True)
def setup_test_db():
    """Translitem: podmenyaem SessionLocal na in-memory sqlite dlya testov."""

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.create_all(bind=engine)

    original_session_local = state_repo.SessionLocal
    state_repo.SessionLocal = TestingSessionLocal
    try:
        yield
    finally:
        state_repo.SessionLocal = original_session_local


def test_device_state_upsert_and_get(setup_test_db):
    repo = DeviceStateLastRepository()
    now = datetime.utcnow()

    repo.upsert_state("dev-1", {"manual_watering": {"status": "idle"}}, now)
    stored = repo.get_state("dev-1")

    assert stored is not None
    assert stored["device_id"] == "dev-1"
    assert stored["state"]["manual_watering"]["status"] == "idle"

    later = now + timedelta(seconds=30)
    repo.upsert_state("dev-1", {"manual_watering": {"status": "running"}}, later)
    updated = repo.get_state("dev-1")

    assert updated["state"]["manual_watering"]["status"] == "running"
    assert updated["updated_at"] == later


def test_device_state_list_filter(setup_test_db):
    repo = DeviceStateLastRepository()
    now = datetime.utcnow()

    repo.upsert_state("dev-2", {"manual_watering": {"status": "idle"}}, now)
    repo.upsert_state("dev-3", {"manual_watering": {"status": "stopping"}}, now)

    result = repo.list_states(["dev-2"])
    assert len(result) == 1
    assert result[0]["device_id"] == "dev-2"


def test_ack_put_and_get(setup_test_db):
    repo = MqttAckRepository()
    now = datetime.utcnow()

    repo.put_ack(
        correlation_id="corr-1",
        device_id="dev-1",
        ack_dict={"correlation_id": "corr-1", "result": "accepted", "status": "running"},
        received_at=now,
        ttl_seconds=60,
    )

    stored = repo.get_ack("corr-1")
    assert stored is not None
    assert stored["result"] == "accepted"
    assert stored["status"] == "running"
    assert stored["payload"]["correlation_id"] == "corr-1"


def test_ack_expired_and_cleanup(setup_test_db):
    repo = MqttAckRepository()
    now = datetime.utcnow()

    repo.put_ack(
        correlation_id="corr-expired",
        device_id="dev-2",
        ack_dict={"correlation_id": "corr-expired", "result": "accepted"},
        received_at=now - timedelta(seconds=120),
        ttl_seconds=30,
    )

    assert repo.get_ack("corr-expired") is None

    deleted = repo.cleanup_expired(now)
    assert deleted >= 1
