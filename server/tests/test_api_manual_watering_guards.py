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

from app.models.database_models import Base


@pytest.fixture(autouse=True)
def enable_debug(monkeypatch):
    """╨Т╨║╨╗╤О╤З╨░╨╡╨╝ DEBUG, ╤З╤В╨╛╨▒╤Л ╤Б╨╡╤А╨▓╨╕╤Б╨╜╤Л╨╣ ╤Н╨╜╨┤╨┐╨╛╨╕╨╜╤В /_debug/shadow/state ╨▒╤Л╨╗ ╨┤╨╛╤Б╤В╤Г╨┐╨╡╨╜ ╨▓ ╤В╨╡╤Б╤В╨░╤Е."""

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    yield
    config.get_settings.cache_clear()

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """╨а╨░╨╖╨▓╨╛╤А╨░╤З╨╕╨▓╨░╨╡╨╝ in-memory SQLite, ╤З╤В╨╛╨▒╤Л FastAPI ╨╜╨╡ ╤В╤П╨╜╤Г╨╗ ╤А╨╡╨░╨╗╤М╨╜╤Л╨╣ Postgres."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """╨Ю╤В╨┤╨░╤С╨╝ ╤В╨╡╤Б╤В╨╛╨▓╤Г╤О ╤Б╨╡╤Б╤Б╨╕╤О SQLAlchemy ╨╕ ╨░╨║╨║╤Г╤А╨░╤В╨╜╨╛ ╨╖╨░╨║╤А╤Л╨▓╨░╨╡╨╝ ╨╡╤С ╨┐╨╛╤Б╨╗╨╡ ╨╕╤Б╨┐╨╛╨╗╤М╨╖╨╛╨▓╨░╨╜╨╕╤П."""

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

from api_manual_watering import get_mqtt_dep
from app.main import app
from service.mqtt.interfaces import IMqttPublisher
from service.mqtt.serialization import CmdPumpStart, CmdPumpStop, CommandType


class FakePublisher(IMqttPublisher):
    """╨д╨╡╨╣╨║╨╛╨▓╤Л╨╣ MQTT-╨┐╨░╨▒╨╗╨╕╤И╨╡╤А: ╨╖╨░╨┐╨╛╨╝╨╕╨╜╨░╨╡╤В ╨┐╤Г╨▒╨╗╨╕╨║╤Г╨╡╨╝╤Л╨╡ ╨║╨╛╨╝╨░╨╜╨┤╤Л ╨▓╨╝╨╡╤Б╤В╨╛ ╤А╨╡╨░╨╗╤М╨╜╨╛╨│╨╛ ╨▒╤А╨╛╨║╨╡╤А╨░."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStart | CmdPumpStop]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStart | CmdPumpStop) -> None:
        """╨б╨╛╤Е╤А╨░╨╜╤П╨╡╨╝ ╨║╨╛╨╝╨░╨╜╨┤╤Г ╨▓ ╤Б╨┐╨╕╤Б╨╛╨║, ╤З╤В╨╛╨▒╤Л ╤В╨╡╤Б╤В╤Л ╨╝╨╛╨│╨╗╨╕ ╨┐╤А╨╛╨▓╨╡╤А╨╕╤В╤М ╤Д╨░╨║╤В ╨┐╤Г╨▒╨╗╨╕╨║╨░╤Ж╨╕╨╕."""

        self.published.append((device_id, cmd))


class _DummySubscriber:
    """╨Ч╨░╨│╨╗╤Г╤И╨║╨░ ╨┤╨╗╤П MQTT-╨┐╨╛╨┤╨┐╨╕╤Б╤З╨╕╨║╨╛╨▓, ╤З╤В╨╛╨▒╤Л ╨▓╤Л╨║╨╗╤О╤З╨╕╤В╤М ╤А╨╡╨░╨╗╤М╨╜╤Л╨╡ ╨┐╨╛╨┤╨║╨╗╤О╤З╨╡╨╜╨╕╤П ╨╜╨░ ╤Б╤В╨░╤А╤В╨╡ ╨┐╤А╨╕╨╗╨╛╨╢╨╡╨╜╨╕╤П."""

    def start(self) -> None:
        """╨Э╨╕╤З╨╡╨│╨╛ ╨╜╨╡ ╨┤╨╡╨╗╨░╨╡╨╝, ╨╜╨╛ ╤Б╨╛╤Е╤А╨░╨╜╤П╨╡╨╝ ╤Б╨╛╨▓╨╝╨╡╤Б╤В╨╕╨╝╨╛╤Б╤В╤М ╤Б ╨╛╨╢╨╕╨┤╨░╨╜╨╕╤П╨╝╨╕ app.main."""

    def stop(self) -> None:
        """╨Э╨╕╤З╨╡╨│╨╛ ╨╜╨╡ ╨┤╨╡╨╗╨░╨╡╨╝ ╨┐╤А╨╕ ╨╛╤Б╤В╨░╨╜╨╛╨▓╨║╨╡, ╤З╤В╨╛╨▒╤Л ╤В╨╡╤Б╤В╤Л ╨╖╨░╨▓╨╡╤А╤И╨░╨╗╨╕╤Б╤М ╨╝╨│╨╜╨╛╨▓╨╡╨╜╨╜╨╛."""


@contextmanager
def manual_watering_client(fake_publisher: FakePublisher) -> Iterator[TestClient]:
    """╨б╨╛╨╖╨┤╨░╤С╨╝ TestClient ╤Б ╨┐╨╛╨┤╨╝╨╡╨╜╤С╨╜╨╜╤Л╨╝╨╕ ╨╖╨░╨▓╨╕╤Б╨╕╨╝╨╛╤Б╤В╤П╨╝╨╕, ╨╛╤Б╤В╨░╨▓╨╗╤П╤П ╤А╨░╨▒╨╛╤З╨╕╨╝ ╤В╨╛╨╗╤М╨║╨╛ DeviceShadowStore."""

    stack = ExitStack()
    dummy_state = _DummySubscriber()
    dummy_ack = _DummySubscriber()

    # ╨Э╨░ ╤Н╤В╨░╨┐╨╡ ╤Б╤В╨░╤А╤В╨░ FastAPI ╨╕╨╜╨╕╤Ж╨╕╨░╨╗╨╕╨╖╨╕╤А╤Г╨╡╤В MQTT-╨┐╨╛╨┤╨┐╨╕╤Б╤З╨╕╨║╨╛╨▓ ╨╕ ╨┐╨░╨▒╨╗╨╕╤И╨╡╤А тАФ ╨┐╨╛╨┤╨╝╨╡╨╜╤П╨╡╨╝ ╨╜╨░ ╨╖╨░╨│╨╗╤Г╤И╨║╨╕,
    # ╤З╤В╨╛╨▒╤Л ╤В╨╡╤Б╤В╤Л ╨╜╨╡ ╤Е╨╛╨┤╨╕╨╗╨╕ ╨▓ ╤Б╨╡╤В╤М ╨╕ ╨╜╨╡ ╨╖╨░╨▓╨╕╤Б╨╡╨╗╨╕ ╨╛╤В ╨▒╤А╨╛╨║╨╡╤А╨░.
    stack.enter_context(patch("app.main.init_publisher", lambda: None))
    stack.enter_context(patch("app.main.init_state_subscriber", lambda store: None))
    stack.enter_context(patch("app.main.get_state_subscriber", lambda: dummy_state))
    stack.enter_context(patch("app.main.init_ack_subscriber", lambda store: None))
    stack.enter_context(patch("app.main.get_ack_subscriber", lambda: dummy_ack))
    stack.enter_context(patch("app.main.shutdown_state_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_ack_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_publisher", lambda: None))

    # ╨Я╨╛╨┤╨╝╨╡╨╜╤П╨╡╨╝ MQTT-╨╖╨░╨▓╨╕╤Б╨╕╨╝╨╛╤Б╤В╤М ╨▓ ╤А╨╛╤Г╤В╨╡╤А╨╡ ╨╜╨░ ╨╜╨░╤И ╤Д╨╡╨╣╨║╨╛╨▓╤Л╨╣ ╨┐╨░╨▒╨╗╨╕╤И╨╡╤А.
    app.dependency_overrides[get_mqtt_dep] = lambda: fake_publisher

    try:
        with TestClient(app) as client:
            yield client
    finally:
        app.dependency_overrides.clear()
        stack.close()


def _push_shadow_state(client: TestClient, device_id: str, status: str, *, duration: int | None = None, started_at: str | None = None, correlation_id: str | None = None) -> None:
    """╨Ю╤В╨┐╤А╨░╨▓╨╗╤П╨╡╨╝ ╤Б╨╛╤Б╤В╨╛╤П╨╜╨╕╨╡ ╨▓ DeviceShadowStore ╤З╨╡╤А╨╡╨╖ ╨┐╤Г╨▒╨╗╨╕╤З╨╜╤Л╨╣ debug-╤Н╨╜╨┤╨┐╨╛╨╣╨╜╤В."""

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
    """╨У╨╡╨╜╨╡╤А╨╕╤А╤Г╨╡╨╝ ISO-╨▓╤А╨╡╨╝╤П ╨▒╨╡╨╖ ╨╝╨╕╨║╤А╨╛╤Б╨╡╨║╤Г╨╜╨┤ ╨┤╨╗╤П started_at."""

    return datetime.utcnow().replace(microsecond=0).isoformat() + "Z"


def test_manual_watering_start_conflict_when_running() -> None:
    """╨Я╨╛╨▓╤В╨╛╤А╨╜╤Л╨╣ ╤Б╤В╨░╤А╤В ╨┐╤А╨╕ ╤Б╤В╨░╤В╤Г╤Б╨╡ running ╨┤╨╛╨╗╨╢╨╡╨╜ ╨▒╨╗╨╛╨║╨╕╤А╨╛╨▓╨░╤В╤М╤Б╤П ╤Б 409 ╨╕ ╨▒╨╡╨╖ ╨┐╤Г╨▒╨╗╨╕╨║╨░╤Ж╨╕╨╕ ╨║╨╛╨╝╨░╨╜╨┤╤Л."""

    fake = FakePublisher()
    device_id = "guard-start-running"

    with manual_watering_client(fake) as client:
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
        )

    assert response.status_code == 409
    assert response.json()["detail"] == "Полив уже выполняется — повторный запуск запрещён."
    assert fake.published == []


def test_manual_watering_stop_conflict_when_idle() -> None:
    """╨б╤В╨╛╨┐ ╨┐╤А╨╕ ╤Б╤В╨░╤В╤Г╤Б╨╡ idle ╨┤╨╛╨╗╨╢╨╡╨╜ ╨▓╨╛╨╖╨▓╤А╨░╤Й╨░╤В╤М 409 ╨╕ ╨╜╨╡ ╨╛╤В╨┐╤А╨░╨▓╨╗╤П╤В╤М ╨║╨╛╨╝╨░╨╜╨┤╤Г."""

    fake = FakePublisher()
    device_id = "guard-stop-idle"

    with manual_watering_client(fake) as client:
        _push_shadow_state(
            client,
            device_id=device_id,
            status="idle",
        )

        response = client.post(
            "/api/manual-watering/stop",
            json={"device_id": device_id},
        )

    assert response.status_code == 409
    assert response.json()["detail"] == "Полив не выполняется — останавливать нечего."
    assert fake.published == []


def test_manual_watering_start_allowed_after_idle() -> None:
    """╨Я╤А╨╕ ╤Б╤В╨░╤В╤Г╤Б╨╡ idle ╨╖╨░╨┐╤Г╤Б╨║ ╤А╨░╨╖╤А╨╡╤И╤С╨╜: ╨▓╨╕╨┤╨╕╨╝ 200, ╨║╨╛╨╝╨░╨╜╨┤╤Г pump.start ╨╕ ╨║╨╛╤А╤А╨╡╨║╤В╨╜╤Л╨╣ correlation_id."""

    fake = FakePublisher()
    device_id = "guard-start-idle"

    with manual_watering_client(fake) as client:
        _push_shadow_state(
            client,
            device_id=device_id,
            status="idle",
        )

        response = client.post(
            "/api/manual-watering/start",
            json={"device_id": device_id, "duration_s": 45},
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
    """╨Х╤Б╨╗╨╕ ╤В╨╡╨╜╤М ╤Б╨╛╨╛╨▒╤Й╨░╨╡╤В running, ╤Б╤В╨╛╨┐ ╨┐╤А╨╛╤Е╨╛╨┤╨╕╤В ╤Г╤Б╨┐╨╡╤И╨╜╨╛ ╨╕ ╨┐╤Г╨▒╨╗╨╕╨║╤Г╨╡╤В pump.stop."""

    fake = FakePublisher()
    device_id = "guard-stop-running"

    with manual_watering_client(fake) as client:
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

