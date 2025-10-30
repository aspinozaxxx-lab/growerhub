﻿"""ACK не получен в заданное время"""

from __future__ import annotations

import sys
import threading
import time
import types
from contextlib import ExitStack, contextmanager
from typing import Iterator
from unittest.mock import patch

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

import service.mqtt.store as ack_store_module
from app.models.database_models import Base

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """╨а╨░╨╖╨▓╨╛╤А╨░╤З╨╕╨▓╨░╨╡╨╝ in-memory SQLite, ╤З╤В╨╛╨▒╤Л ╤В╨╡╤Б╤В╤Л ╨╜╨╡ ╨╖╨░╨▓╨╕╤Б╨╡╨╗╨╕ ╨╛╤В ╨╜╨░╤Б╤В╨╛╤П╤Й╨╡╨╣ ╨С╨Ф."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """╨Ю╤В╨┤╨░╤С╨╝ ╤Б╨╡╤Б╤Б╨╕╤О SQLAlchemy ╨╕ ╨│╨░╤А╨░╨╜╤В╨╕╤А╨╛╨▓╨░╨╜╨╜╨╛ ╨╖╨░╨║╤А╤Л╨▓╨░╨╡╨╝ ╨╡╤С ╨┐╨╛╤Б╨╗╨╡ ╨╕╤Б╨┐╨╛╨╗╤М╨╖╨╛╨▓╨░╨╜╨╕╤П."""

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

from service.mqtt.store import AckStore  # noqa: E402
from app.api.routers.manual_watering import get_ack_dep  # noqa: E402
from app.main import app  # noqa: E402
from service.mqtt.serialization import Ack, AckResult  # noqa: E402


class _DummySubscriber:
    """╨Ч╨░╨│╨╗╤Г╤И╨║╨░ ╨┤╨╗╤П MQTT-╤Б╨░╨▒╤Б╨║╤А╨░╨╣╨▒╨╡╤А╨╛╨▓: ╨╜╨╡ ╨┤╨╡╨╗╨░╨╡╤В ╨╜╨╕╤З╨╡╨│╨╛, ╨╜╨╛ ╤Г╨┤╨╛╨▓╨╗╨╡╤В╨▓╨╛╤А╤П╨╡╤В ╨╕╨╜╤В╨╡╤А╤Д╨╡╨╣╤Б."""

    def start(self) -> None:
        """╨Э╨╕╤З╨╡╨│╨╛ ╨╜╨╡ ╨┤╨╡╨╗╨░╨╡╨╝ ╨╜╨░ ╤Б╤В╨░╤А╤В╨╡ тАФ ╤В╨╡╤Б╤В╤Л ╨╜╨╡ ╨╜╤Г╨╢╨┤╨░╤О╤В╤Б╤П ╨▓ ╤А╨╡╨░╨╗╤М╨╜╨╛╨╝ ╨▒╤А╨╛╨║╨╡╤А╨╡."""

    def stop(self) -> None:
        """╨в╨░╨║╨╢╨╡ ╨╜╨╕╤З╨╡╨│╨╛ ╨╜╨╡ ╨┤╨╡╨╗╨░╨╡╨╝ ╨╜╨░ ╨╛╤Б╤В╨░╨╜╨╛╨▓╨║╨╡, ╤З╤В╨╛╨▒╤Л ╨╖╨░╨▓╨╡╤А╤И╨╡╨╜╨╕╨╡ ╨▒╤Л╨╗╨╛ ╨╝╨│╨╜╨╛╨▓╨╡╨╜╨╜╤Л╨╝."""


@contextmanager
def wait_ack_client(store: AckStore) -> Iterator[TestClient]:
    """╨б╨╛╨╖╨┤╨░╤С╨╝ TestClient ╤Б ╨┐╨╛╨┤╨╝╨╡╨╜╤С╨╜╨╜╤Л╨╝╨╕ ╨╖╨░╨▓╨╕╤Б╨╕╨╝╨╛╤Б╤В╤П╨╝╨╕ ╨╕ ╨╖╨░╤А╨░╨╜╨╡╨╡ ╨┐╨╛╨┤╨│╨╛╤В╨╛╨▓╨╗╨╡╨╜╨╜╤Л╨╝ AckStore."""

    stack = ExitStack()
    dummy_state = _DummySubscriber()
    dummy_ack = _DummySubscriber()

    stack.enter_context(patch("service.mqtt.lifecycle.init_publisher", lambda: None))
    stack.enter_context(patch("service.mqtt.lifecycle.init_state_subscriber", lambda shadow: None))
    stack.enter_context(patch("service.mqtt.lifecycle.get_state_subscriber", lambda: dummy_state))
    stack.enter_context(patch("service.mqtt.lifecycle.init_ack_subscriber", lambda ack_store: None))
    stack.enter_context(patch("service.mqtt.lifecycle.get_ack_subscriber", lambda: dummy_ack))
    stack.enter_context(patch("service.mqtt.lifecycle.shutdown_state_subscriber", lambda: None))
    stack.enter_context(patch("service.mqtt.lifecycle.shutdown_ack_subscriber", lambda: None))
    stack.enter_context(patch("service.mqtt.lifecycle.shutdown_publisher", lambda: None))

    def _init_ack_store_stub() -> None:
        """╨Я╤А╨╕ ╤Б╤В╨░╤А╤В╨╡ ╨┐╤А╨╕╨╗╨╛╨╢╨╡╨╜╨╕╤П ╨┐╤А╨╕╨▓╤П╨╖╤Л╨▓╨░╨╡╨╝ ╨│╨╗╨╛╨▒╨░╨╗╤М╨╜╤Л╨╣ AckStore ╨║ ╨╜╨░╤И╨╡╨╝╤Г in-memory ╤Н╨║╨╖╨╡╨╝╨┐╨╗╤П╤А╤Г."""

        ack_store_module._ack_store = store

    stack.enter_context(patch("service.mqtt.store.init_ack_store", _init_ack_store_stub))
    stack.enter_context(patch("service.mqtt.store.shutdown_ack_store", lambda: None))

    app.dependency_overrides[get_ack_dep] = lambda: store
    ack_store_module._ack_store = store

    try:
        with TestClient(app) as client:
            yield client
    finally:
        app.dependency_overrides.clear()
        ack_store_module._ack_store = None
        stack.close()


def _make_ack(correlation_id: str, *, result: AckResult = AckResult.accepted) -> Ack:
    """╨б╨╛╨╖╨┤╨░╤С╨╝ ╨╝╨╕╨╜╨╕╨╝╨░╨╗╤М╨╜╤Л╨╣ ACK ╤Б ╨╖╨░╨┤╨░╨╜╨╜╤Л╨╝ ╤А╨╡╨╖╤Г╨╗╤М╤В╨░╤В╨╛╨╝."""

    return Ack(correlation_id=correlation_id, result=result)


def test_wait_ack_returns_existing_ack() -> None:
    """╨Х╤Б╨╗╨╕ ACK ╤Г╨╢╨╡ ╨╗╨╡╨╢╨╕╤В ╨▓ ╤Б╤В╨╛╤А╨╡, ╤А╤Г╤З╨║╨░ ╨┤╨╛╨╗╨╢╨╜╨░ ╨▓╨╡╤А╨╜╤Г╤В╤М ╨╡╨│╨╛ ╨╝╨│╨╜╨╛╨▓╨╡╨╜╨╜╨╛."""

    store = AckStore()
    correlation_id = "ack-immediate"
    store.put("dev-1", _make_ack(correlation_id))

    with wait_ack_client(store) as client:
        response = client.get(
            "/api/manual-watering/wait-ack",
            params={"correlation_id": correlation_id, "timeout_s": 5},
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["correlation_id"] == correlation_id
    assert payload["result"] == AckResult.accepted.value


def test_wait_ack_returns_when_ack_delayed() -> None:
    """ACK ╨╝╨╛╨╢╨╡╤В ╨┐╨╛╤П╨▓╨╕╤В╤М╤Б╤П ╤Б ╨╖╨░╨┤╨╡╤А╨╢╨║╨╛╨╣ тАФ ╨┐╤А╨╛╨▓╨╡╤А╤П╨╡╨╝, ╤З╤В╨╛ long-poll ╨┤╨╛╨╢╨╕╨┤╨░╨╡╤В╤Б╤П ╨╛╤В╨▓╨╡╤В╨░."""

    store = AckStore()
    correlation_id = "ack-delayed"

    def _delayed_put() -> None:
        """╨Ш╨╝╨╕╤В╨░╤Ж╨╕╤П ╤Г╤Б╤В╤А╨╛╨╣╤Б╤В╨▓╨░: ╤З╨╡╤А╨╡╨╖ ╤Б╨╡╨║╤Г╨╜╨┤╤Г ╨╖╨░╨┐╨╕╤Б╤Л╨▓╨░╨╡╨╝ ACK ╨▓ ╤Б╤В╨╛╤А."""

        time.sleep(1)
        store.put("dev-2", _make_ack(correlation_id))

    worker = threading.Thread(target=_delayed_put, daemon=True)
    worker.start()

    with wait_ack_client(store) as client:
        response = client.get(
            "/api/manual-watering/wait-ack",
            params={"correlation_id": correlation_id, "timeout_s": 5},
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["correlation_id"] == correlation_id
    assert payload["result"] == AckResult.accepted.value


def test_wait_ack_returns_timeout_when_ack_missing() -> None:
    """╨Х╤Б╨╗╨╕ ╨┐╨╛╨┤╤В╨▓╨╡╤А╨╢╨┤╨╡╨╜╨╕╨╡ ╤В╨░╨║ ╨╕ ╨╜╨╡ ╨┐╤А╨╕╤И╨╗╨╛, ╤А╤Г╤З╨║╨░ ╨╛╤В╨▓╨╡╤З╨░╨╡╤В 408 ╤Б ╨┐╨╛╨╜╤П╤В╨╜╤Л╨╝ detail."""

    store = AckStore()

    with wait_ack_client(store) as client:
        response = client.get(
            "/api/manual-watering/wait-ack",
            params={"correlation_id": "not-found", "timeout_s": 1},
        )

    assert response.status_code == 408
    assert response.json() == {"detail": "ACK не получен в заданное время"}



