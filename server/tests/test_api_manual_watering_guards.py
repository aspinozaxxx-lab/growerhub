from __future__ import annotations

import sys
import types
from contextlib import ExitStack, contextmanager
from datetime import datetime
from typing import Iterator
from unittest.mock import patch

import config
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.security import create_access_token
from app.models.database_models import Base, DeviceDB
from app.repositories.users import create_local_user


@pytest.fixture(autouse=True)
def enable_debug(monkeypatch):
    """Vklyuchaet DEBUG dlya dostupnosti debug endpointov v testah."""

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    yield
    config.get_settings.cache_clear()

engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Sozdaet sqlite shemu v pamyati dlya testov."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Generator, vozvrashchayushchiy sessiyu SQLAlchemy i zakryvayushchiy ee posle ispolzovaniya."""

    _create_tables()
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

from app.fastapi.routers.manual_watering import get_mqtt_dep
from app.main import app
from app.mqtt.interfaces import IMqttPublisher
from app.mqtt.serialization import CmdPumpStart, CmdPumpStop, CommandType


def _create_user(email: str, password: str) -> int:
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        user = create_local_user(session, email, None, "user", password)
        session.refresh(user)
        return user.id
    finally:
        session.close()


def _insert_device(device_id: str, user_id: int) -> None:
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        session.query(DeviceDB).filter(DeviceDB.device_id == device_id).delete()
        device = DeviceDB(device_id=device_id, name=f"Device {device_id}", user_id=user_id)
        session.add(device)
        session.commit()
    finally:
        session.close()


@pytest.fixture(autouse=True)
def override_db_dependencies():
    import app.core.database as database_module  # noqa: WPS433
    import app.core.security as security_module  # noqa: WPS433

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    app.dependency_overrides[database_module.get_db] = _get_db
    app.dependency_overrides[security_module.get_db] = _get_db
    yield
    app.dependency_overrides.pop(database_module.get_db, None)
    app.dependency_overrides.pop(security_module.get_db, None)


class FakePublisher(IMqttPublisher):
    """Feikovyi MQTT-pablisher: zapominaet publikuyemye komandy vmesto realnogo brokera."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStart | CmdPumpStop]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStart | CmdPumpStop) -> None:
        """Sohranyaet komandu v spiske dlya proverok v teste."""

        self.published.append((device_id, cmd))


class _DummySubscriber:
    """Zaglushka dlya MQTT-podpischikov na starte prilozheniya bez realnogo podklyucheniya."""

    def start(self) -> None:
        """Zaglushka start: nichego ne delaet v testovom kliente."""

    def stop(self) -> None:
        """Zaglushka stop: nichego ne delaet v testovom kliente."""


@contextmanager
def manual_watering_client(fake_publisher: FakePublisher) -> Iterator[TestClient]:
    """Sozdaet TestClient s zaglushkami MQTT dlya scenariev manual watering."""

    stack = ExitStack()
    dummy_state = _DummySubscriber()
    dummy_ack = _DummySubscriber()

    # Na etape starta FastAPI inicializiruet MQTT-podpischikov i publisher, patchim na zaglushki,
    # chtoby testy ne delali realnoe podklyuchenie i ne zaviseli ot brokera.
    stack.enter_context(patch("app.main.init_publisher", lambda: None))
    stack.enter_context(patch("app.mqtt.lifecycle.init_state_subscriber", lambda store: None))
    stack.enter_context(patch("app.mqtt.lifecycle.get_state_subscriber", lambda: dummy_state))
    stack.enter_context(patch("app.mqtt.lifecycle.init_ack_subscriber", lambda store: None))
    stack.enter_context(patch("app.mqtt.lifecycle.get_ack_subscriber", lambda: dummy_ack))
    stack.enter_context(patch("app.mqtt.lifecycle.shutdown_state_subscriber", lambda: None))
    stack.enter_context(patch("app.mqtt.lifecycle.shutdown_ack_subscriber", lambda: None))
    stack.enter_context(patch("app.mqtt.lifecycle.shutdown_publisher", lambda: None))

    # Podmenyaem MQTT-zavisimost v routere na nash feikovyi pablisher.
    app.dependency_overrides[get_mqtt_dep] = lambda: fake_publisher

    try:
        with TestClient(app) as client:
            yield client
    finally:
        app.dependency_overrides.clear()
        stack.close()


def _owner_headers(client: TestClient, device_id: str) -> dict[str, str]:
    user_id = _create_user("owner@example.com", "secret")
    _insert_device(device_id, user_id=user_id)
    token = create_access_token({"user_id": user_id})
    return {"Authorization": f"Bearer {token}"}


def _push_shadow_state(client: TestClient, device_id: str, status: str, *, duration: int | None = None, started_at: str | None = None, correlation_id: str | None = None) -> None:
    """Otpravlyaet ten' ustroystva cherez debug endpoint dlya testov."""

    manual_watering: dict[str, object] = {"status": status}
    if duration is not None:
        manual_watering["duration_s"] = duration
    if started_at is not None:
        manual_watering["started_at"] = started_at
    if correlation_id is not None:
        manual_watering["correlation_id"] = correlation_id

    response = client.post(
        "/_debug/shadow/state",
        json={
            "device_id": device_id,
            "state": {
                "manual_watering": manual_watering,
            },
        },
    )
    assert response.status_code == 200, response.text


def _iso_now() -> str:
    """Vozvrashaet ISO vremya bez mikrosekund dlya sravneniy."""

    return datetime.utcnow().replace(microsecond=0).isoformat() + "Z"


def test_manual_watering_start_conflict_when_running() -> None:
    """Proveryaet chto povtornyi start pri status running vozvrashaet HTTP 409 i ne publikuet komandu."""

    fake = FakePublisher()
    device_id = "guard-start-running"

    with manual_watering_client(fake) as client:
        headers = _owner_headers(client, device_id)
        _push_shadow_state(
            client,
            device_id=device_id,
            status="running",
            duration=60,
            started_at=_iso_now(),
            correlation_id="existing-correlation",
        )

        response = client.post(
            "/api/manual-watering/start",
            json={"device_id": device_id, "duration_s": 20},
            headers=headers,
        )

    assert response.status_code == 409
    detail = response.json()["detail"]
    assert isinstance(detail, str) and detail
    assert fake.published == []


def test_manual_watering_stop_conflict_when_idle() -> None:
    """Proveryaet chto stop pri idle vozvrashaet HTTP 409 i komanda ne uhodit."""

    fake = FakePublisher()
    device_id = "guard-stop-idle"

    with manual_watering_client(fake) as client:
        headers = _owner_headers(client, device_id)
        _push_shadow_state(
            client,
            device_id=device_id,
            status="idle",
        )

        response = client.post(
            "/api/manual-watering/stop",
            json={"device_id": device_id},
            headers=headers,
        )

    assert response.status_code == 409
    detail = response.json()["detail"]
    assert isinstance(detail, str) and detail
    assert fake.published == []


def test_manual_watering_start_allowed_after_idle() -> None:
    """Proveryaet uspeshnyi start pri idle i publikaciyu pump.start."""

    fake = FakePublisher()
    device_id = "guard-start-idle"

    with manual_watering_client(fake) as client:
        headers = _owner_headers(client, device_id)
        _push_shadow_state(
            client,
            device_id=device_id,
            status="idle",
        )

        response = client.post(
            "/api/manual-watering/start",
            json={"device_id": device_id, "duration_s": 45},
            headers=headers,
        )

    assert response.status_code == 200
    payload = response.json()
    correlation_id = payload.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    assert len(fake.published) == 1
    published_device, command = fake.published[0]
    assert published_device == device_id
    assert isinstance(command, CmdPumpStart)
    assert command.type == CommandType.pump_start.value
    assert command.duration_s == 45
    assert command.correlation_id == correlation_id


def test_manual_watering_stop_allowed_after_running() -> None:
    """Proveryaet uspeshnyi stop pri running i publikaciyu pump.stop."""

    fake = FakePublisher()
    device_id = "guard-stop-running"

    with manual_watering_client(fake) as client:
        headers = _owner_headers(client, device_id)
        _push_shadow_state(
            client,
            device_id=device_id,
            status="running",
            duration=90,
            started_at=_iso_now(),
            correlation_id="correlation-from-device",
        )

        response = client.post(
            "/api/manual-watering/stop",
            json={"device_id": device_id},
            headers=headers,
        )

    assert response.status_code == 200
    payload = response.json()
    correlation_id = payload.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    assert len(fake.published) == 1
    published_device, command = fake.published[0]
    assert published_device == device_id
    assert isinstance(command, CmdPumpStop)
    assert command.type == CommandType.pump_stop.value
    assert command.correlation_id == correlation_id
