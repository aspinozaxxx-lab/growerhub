﻿"""Тесты API статуса ручного полива: проверяем оставшееся время и онлайн-признаки."""

from __future__ import annotations

import sys
import types
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from importlib import util
from pathlib import Path
from unittest.mock import patch

import config
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base
from app.main import app
from device_shadow import get_shadow_store

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Разворачиваем in-memory SQLite, чтобы не использовать реальную базу."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Отдаём сессию SQLAlchemy и корректно освобождаем ресурсы после использования."""

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


@pytest.fixture(autouse=True)
def ensure_debug_enabled(monkeypatch):
    """В тестах включаем DEBUG, чтобы был доступен сервисный эндпоинт для подготовки тени."""

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    yield
    config.get_settings.cache_clear()


def _iso_utc(dt: datetime) -> str:
    """Форматируем timestamp в ISO 8601 с суффиксом Z."""

    return dt.replace(tzinfo=timezone.utc, microsecond=0).isoformat().replace("+00:00", "Z")


def _post_shadow_state(client: TestClient, payload: dict) -> None:
    """Утилита для тестов: кладём состояние в стор через отладочный эндпоинт."""

    response = client.post("/_debug/shadow/state", json=payload)
    assert response.status_code == 200, response.text


def test_status_idle_returns_idle_and_no_remaining() -> None:
    """Idle: нет таймера, устройство онлайн и виден last_seen_at."""

    with TestClient(app) as client:
        _post_shadow_state(
            client,
            {
                "device_id": "abc123",
                "state": {
                    "manual_watering": {
                        "status": "idle",
                        "duration_s": None,
                        "started_at": None,
                        "correlation_id": None,
                    }
                },
            },
        )

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "idle"
    assert data["remaining_s"] is None
    assert data["source"] == "calculated"
    assert data["is_online"] is True
    assert data["updated_at"] == data["last_seen_at"]
    assert data["last_seen_at"].endswith("Z")


def test_status_running_calculates_remaining_seconds() -> None:
    """Running: оставшееся время уменьшается, устройство онлайн."""

    started_at = datetime.utcnow() - timedelta(seconds=5)

    with TestClient(app) as client:
        _post_shadow_state(
            client,
            {
                "device_id": "abc123",
                "state": {
                    "manual_watering": {
                        "status": "running",
                        "duration_s": 20,
                        "started_at": _iso_utc(started_at),
                        "correlation_id": "corr-running",
                    }
                },
            },
        )

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})

    assert response.status_code == 200
    data = response.json()
    expected_remaining = max(0, 20 - int((datetime.utcnow() - started_at).total_seconds()))
    assert data["status"] == "running"
    assert max(0, expected_remaining - 2) <= data["remaining_s"] <= expected_remaining + 2
    assert data["duration_s"] == 20
    assert data["correlation_id"] == "corr-running"
    assert data["is_online"] is True


def test_status_running_expired_returns_zero() -> None:
    """Когда таймер истёк, remaining_s равно нулю, но устройство ещё онлайн."""

    started_at = datetime.utcnow() - timedelta(seconds=20)

    with TestClient(app) as client:
        _post_shadow_state(
            client,
            {
                "device_id": "abc123",
                "state": {
                    "manual_watering": {
                        "status": "running",
                        "duration_s": 10,
                        "started_at": _iso_utc(started_at),
                        "correlation_id": "corr-expired",
                    }
                },
            },
        )

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "running"
    assert data["remaining_s"] == 0
    assert data["is_online"] is True


def test_status_marks_device_offline_when_state_is_stale() -> None:
    """Если порог свежести нулевой, даже свежее состояние выглядит оффлайном."""

    base_settings = config.get_settings()
    custom_settings = replace(base_settings, DEVICE_ONLINE_THRESHOLD_S=0)

    with patch("config.get_settings", return_value=custom_settings):
        with TestClient(app) as client:
            _post_shadow_state(
                client,
                {
                    "device_id": "abc123",
                    "state": {
                        "manual_watering": {
                            "status": "idle",
                            "duration_s": None,
                            "started_at": None,
                            "correlation_id": None,
                        }
                    },
                },
            )

            store = get_shadow_store()
            with store._lock:
                state, _ = store._storage["abc123"]
                store._storage["abc123"] = (state, datetime.utcnow() - timedelta(seconds=30))

            response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})

    assert response.status_code == 200
    data = response.json()
    assert data["is_online"] is False
    assert data["last_seen_at"].endswith("Z")
    assert data["updated_at"] == data["last_seen_at"]


def test_status_not_found_returns_404() -> None:
    """Неизвестный device_id даёт 404 и понятный detail."""

    with TestClient(app) as client:
        response = client.get("/api/manual-watering/status", params={"device_id": "no-such"})
        assert response.status_code == 404


def test_debug_shadow_state_disabled_when_debug_false(monkeypatch) -> None:
    """Проверяем, что сервисный эндпоинт скрыт, когда DEBUG=False."""

    monkeypatch.setenv("DEBUG", "false")
    config.get_settings.cache_clear()

    module_path = Path(__file__).resolve().parents[1] / "api_manual_watering.py"
    spec = util.spec_from_file_location("api_manual_watering_temp", module_path)
    module = util.module_from_spec(spec)
    spec.loader.exec_module(module)  # type: ignore[union-attr]

    tmp_app = FastAPI()
    tmp_app.include_router(module.router)

    client = TestClient(tmp_app)
    response = client.post(
        "/_debug/shadow/state",
        json={
            "device_id": "ghost",
            "state": {
                "manual_watering": {
                    "status": "idle",
                }
            },
        },
    )

    assert response.status_code == 404

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    sys.modules.pop("api_manual_watering_temp", None)
