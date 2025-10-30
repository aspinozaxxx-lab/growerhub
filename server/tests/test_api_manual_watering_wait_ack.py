"""Интеграционные тесты ожидания ACK: проверяем быстрый, отложенный и таймаут-сценарии."""

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

import ack_store as ack_store_module
from app.models.database_models import Base

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Разворачиваем in-memory SQLite, чтобы тесты не зависели от настоящей БД."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Отдаём сессию SQLAlchemy и гарантированно закрываем её после использования."""

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

from ack_store import AckStore  # noqa: E402
from api_manual_watering import get_ack_dep  # noqa: E402
from app.main import app  # noqa: E402
from service.mqtt.serialization import Ack, AckResult  # noqa: E402


class _DummySubscriber:
    """Заглушка для MQTT-сабскрайберов: не делает ничего, но удовлетворяет интерфейс."""

    def start(self) -> None:
        """Ничего не делаем на старте — тесты не нуждаются в реальном брокере."""

    def stop(self) -> None:
        """Также ничего не делаем на остановке, чтобы завершение было мгновенным."""


@contextmanager
def wait_ack_client(store: AckStore) -> Iterator[TestClient]:
    """Создаём TestClient с подменёнными зависимостями и заранее подготовленным AckStore."""

    stack = ExitStack()
    dummy_state = _DummySubscriber()
    dummy_ack = _DummySubscriber()

    stack.enter_context(patch("app.main.init_publisher", lambda: None))
    stack.enter_context(patch("app.main.init_state_subscriber", lambda shadow: None))
    stack.enter_context(patch("app.main.get_state_subscriber", lambda: dummy_state))
    stack.enter_context(patch("app.main.init_ack_subscriber", lambda ack_store: None))
    stack.enter_context(patch("app.main.get_ack_subscriber", lambda: dummy_ack))
    stack.enter_context(patch("app.main.shutdown_state_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_ack_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_publisher", lambda: None))

    def _init_ack_store_stub() -> None:
        """При старте приложения привязываем глобальный AckStore к нашему in-memory экземпляру."""

        ack_store_module._ack_store = store

    stack.enter_context(patch("app.main.init_ack_store", _init_ack_store_stub))
    stack.enter_context(patch("app.main.shutdown_ack_store", lambda: None))

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
    """Создаём минимальный ACK с заданным результатом."""

    return Ack(correlation_id=correlation_id, result=result)


def test_wait_ack_returns_existing_ack() -> None:
    """Если ACK уже лежит в сторе, ручка должна вернуть его мгновенно."""

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
    """ACK может появиться с задержкой — проверяем, что long-poll дожидается ответа."""

    store = AckStore()
    correlation_id = "ack-delayed"

    def _delayed_put() -> None:
        """Имитация устройства: через секунду записываем ACK в стор."""

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
    """Если подтверждение так и не пришло, ручка отвечает 408 с понятным detail."""

    store = AckStore()

    with wait_ack_client(store) as client:
        response = client.get(
            "/api/manual-watering/wait-ack",
            params={"correlation_id": "not-found", "timeout_s": 1},
        )

    assert response.status_code == 408
    assert response.json() == {"detail": "ACK не получен в заданное время"}
