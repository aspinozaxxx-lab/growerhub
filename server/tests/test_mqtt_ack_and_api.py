import sys
import types
from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.main import app
from app.mqtt.handlers.ack import extract_device_id_from_ack_topic
from app.mqtt.router import MqttAckSubscriber
from app.mqtt.serialization import Ack, AckResult, ManualWateringStatus
from app.mqtt.store import AckStore, get_ack_store, init_ack_store, shutdown_ack_store
from app.models.database_models import Base
from app.repositories.users import create_local_user


engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    Base.metadata.create_all(bind=engine)


def _get_db():
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
import app.core.security as security_module  # noqa: E402


def _create_user(email: str, password: str) -> int:
    _create_tables()
    session = SessionLocal()
    try:
        user = create_local_user(session, email, None, "user", password)
        session.refresh(user)
        return user.id
    finally:
        session.close()


def _auth_headers(user_id: int) -> dict[str, str]:
    token = security_module.create_access_token({"user_id": user_id})
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture(autouse=True)
def override_db_dependencies():
    import app.core.database as database_module  # noqa: WPS433

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    app.dependency_overrides[database_module.get_db] = _get_db
    app.dependency_overrides[security_module.get_db] = _get_db
    yield
    app.dependency_overrides.pop(database_module.get_db, None)
    app.dependency_overrides.pop(security_module.get_db, None)


def test_extract_device_id_from_ack_topic_valid():
    """Proveryaet izvlechenie device_id iz korrektnogo ack topika."""

    assert extract_device_id_from_ack_topic("gh/dev/xyz/state/ack") == "xyz"


def test_extract_device_id_from_ack_topic_invalid():
    """Proveryaet chto nevernye topiki vozvrashchayut None."""

    assert extract_device_id_from_ack_topic("gh/dev//state/ack") is None
    assert extract_device_id_from_ack_topic("gh/dev/xyz/state") is None
    assert extract_device_id_from_ack_topic("bad/topic") is None


class FakeAckMessage:
    """Minimalnyy analog MQTT-soobshcheniya dlya vyzova on_message."""

    def __init__(self, topic: str, payload: bytes) -> None:
        self.topic = topic
        self.payload = payload


def _make_ack_payload(
    correlation_id: str,
    result: AckResult,
    status: ManualWateringStatus | str | None = None,
) -> bytes:
    """Formiruet JSON payload ack dlya testov."""

    ack = Ack(
        correlation_id=correlation_id,
        result=result,
        status=status,
    )
    return ack.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")


def test_ack_subscriber_on_message_stores_ack():
    """Proveryaet chto ack-subscriber sohranyaet soobshchenie v store."""

    store = AckStore()
    subscriber = MqttAckSubscriber(store, client_factory=lambda: None)

    payload = _make_ack_payload("corr-1", AckResult.accepted, ManualWateringStatus.running)
    message = FakeAckMessage("gh/dev/abc123/state/ack", payload)

    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    ack = store.get("corr-1")
    assert ack is not None
    assert ack.result == AckResult.accepted
    assert ack.status == ManualWateringStatus.running


def test_ack_api_returns_data_when_present():
    """Proveryaet HTTP API ack kogda dannye dostupny."""

    with TestClient(app) as client:
        user_id = _create_user("owner@example.com", "secret")
        headers = _auth_headers(user_id)
        # Proveryaem chto otsutstvuyushchiy correlation_id daet 404
        response = client.get("/api/manual-watering/ack", params={"correlation_id": "missing"}, headers=headers)
        assert response.status_code == 404

        # Dobavlyaem ACK v store i proveryaem JSON-otvet
        store = get_ack_store()
        ack = Ack(
            correlation_id="corr-2",
            result=AckResult.rejected,
            reason="pump jammed",
            status=ManualWateringStatus.idle,
        )
        store.put("abc123", ack)

        response = client.get("/api/manual-watering/ack", params={"correlation_id": "corr-2"}, headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["correlation_id"] == "corr-2"
        assert data["result"] == AckResult.rejected.value
        assert data["reason"] == "pump jammed"
        assert data["status"] == ManualWateringStatus.idle.value

        # Chistim store chtoby ne povtorno ispolzovat dannye
        store.cleanup(max_age_seconds=0)


def test_ack_store_cleanup_removes_old_entries():
    """Proveryaet ochistku starogo ack iz store."""

    store = AckStore()
    recent_ack = Ack(correlation_id="recent", result=AckResult.accepted)
    old_ack = Ack(correlation_id="old", result=AckResult.error, reason="timeout")

    store.put("dev", recent_ack)
    store.put("dev", old_ack)

    # Starim zapisi v store dlya proverki ochistki
    threshold = datetime.utcnow() - timedelta(seconds=10)
    store._storage["old"] = (  # type: ignore[attr-defined]
        store._storage["old"][0],  # type: ignore[attr-defined]
        store._storage["old"][1],  # type: ignore[attr-defined]
        threshold - timedelta(seconds=1),
    )

    removed = store.cleanup(max_age_seconds=5)
    assert removed == 1
    assert store.get("old") is None
    assert store.get("recent") is not None


def test_ack_wait_endpoint_accepts_reboot_status() -> None:
    """Proveryaet chto wait-ack vozvrashaet status reboot bez validacionnyh oshibok."""

    init_ack_store()
    store = get_ack_store()
    reboot_ack = Ack(correlation_id="abc", result=AckResult.accepted, status="reboot")
    store.put("device-1", reboot_ack)

    with TestClient(app) as client:
        user_id = _create_user("owner@example.com", "secret")
        headers = _auth_headers(user_id)
        response = client.get(
            "/api/manual-watering/wait-ack",
            params={"correlation_id": "abc", "timeout_s": 1},
            headers=headers,
        )

    assert response.status_code == 200
    data = response.json()
    assert data["result"] == AckResult.accepted.value
    assert data["status"] == "reboot"
    store.cleanup(max_age_seconds=0)


