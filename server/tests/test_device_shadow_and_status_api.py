"""Тесты API статуса ручного полива: заботимся о прогрессе и признаке онлайна."""

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
from app.mqtt.store import get_shadow_store

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Разворачиваем in-memory SQLite, чтобы не трогать настоящую базу."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Отдаём сессию SQLAlchemy и аккуратно закрываем её после использования."""

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
    """В тестах включаем DEBUG, чтобы использовать сервисный эндпоинт подготовки состояния."""

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    yield
    config.get_settings.cache_clear()


def _iso_utc(dt: datetime) -> str:
    """Форматируем timestamp в ISO 8601 с суффиксом Z."""

    return dt.replace(tzinfo=timezone.utc, microsecond=0).isoformat().replace("+00:00", "Z")


def _post_shadow_state(client: TestClient, payload: dict) -> None:
    """Вспомогательная запись в стор через отладочный эндпоинт."""

    response = client.post("/_debug/shadow/state", json=payload)
    assert response.status_code == 200, response.text


def test_status_idle_returns_idle_and_no_remaining() -> None:
    """Idle: нет таймера, устройство онлайн и offline_reason отсутствует."""

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
    assert data["offline_reason"] is None
    assert data["updated_at"] == data["last_seen_at"]
    assert data["last_seen_at"].endswith("Z")


def test_status_running_calculates_remaining_seconds() -> None:
    """Running: трекаем оставшееся время и подтверждаем, что устройство онлайн."""

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
    assert data["offline_reason"] is None


def test_status_running_expired_returns_zero() -> None:
    """Когда таймер истёк, offline_reason сообщает, что устройство считается оффлайн."""

    started_at = datetime.utcnow() - timedelta(seconds=20)

    base_settings = config.get_settings()
    custom_settings = replace(base_settings, DEVICE_ONLINE_THRESHOLD_S=-1)

    with patch("config.get_settings", return_value=custom_settings), patch("app.mqtt.store.get_settings", return_value=custom_settings):
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
    assert data["is_online"] is False
    assert data["offline_reason"] == "device_offline"


def test_status_without_state_returns_placeholder() -> None:
    """Если сервер ещё ни разу не видел state от устройства — приходят заглушки."""

    with TestClient(app) as client:
        response = client.get("/api/manual-watering/status", params={"device_id": "unknown"})

    assert response.status_code == 200
    data = response.json()
    assert data["is_online"] is False
    assert data["offline_reason"] == "no_state_yet"
    assert data["status"] == "idle"


def test_debug_shadow_state_disabled_when_debug_false(monkeypatch) -> None:
    """Если DEBUG=False, сервисный эндпоинт регистрации стейта недоступен."""

    monkeypatch.setenv("DEBUG", "false")
    config.get_settings.cache_clear()

    module_path = Path(__file__).resolve().parents[1] / "app" / "api" / "routers" / "manual_watering.py"
    spec = util.spec_from_file_location("manual_watering_temp", module_path)
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
    sys.modules.pop("manual_watering_temp", None)
