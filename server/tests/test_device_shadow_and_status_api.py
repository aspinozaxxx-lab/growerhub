"""Testy API statusa ruchnogo poliva: zabotimsya o progresse i priznake onlayna."""

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
from sqlalchemy.pool import StaticPool

from app.models.database_models import Base, DeviceDB
from app.main import app
from app.mqtt.store import get_shadow_store
from app.fastapi.routers import manual_watering as manual_watering_router
from app.repositories.users import create_local_user
from app.core.security import create_access_token

engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Sozdaet sqlite shemu v pamyati dlya testov statusa ustroystv."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Generator s testovoy sessiyey SQLAlchemy."""

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
app.dependency_overrides[manual_watering_router.get_db] = _get_db


@pytest.fixture(autouse=True)
def ensure_debug_enabled(monkeypatch):
    """Ukazyvaet DEBUG=TRUE chtoby debug endpointy byli dostupny v testah."""

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    yield
    config.get_settings.cache_clear()


@pytest.fixture(autouse=True)
def override_db_dependency():
    """Garantiruet povtornoe podmenenie get_db pered kazhdym testom posle vozmozhnyh clear."""

    import app.core.database as database_module  # noqa: WPS433
    import app.core.security as security_module  # noqa: WPS433

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    from app.repositories import state_repo  # noqa: WPS433

    state_repo.SessionLocal = SessionLocal
    from app.mqtt.store import init_shadow_store, shutdown_shadow_store  # noqa: WPS433

    shutdown_shadow_store()
    init_shadow_store()
    app.dependency_overrides[manual_watering_router.get_db] = _get_db
    app.dependency_overrides[database_module.get_db] = _get_db
    app.dependency_overrides[security_module.get_db] = _get_db
    yield
    shutdown_shadow_store()
    init_shadow_store()
    app.dependency_overrides.pop(manual_watering_router.get_db, None)
    app.dependency_overrides.pop(database_module.get_db, None)
    app.dependency_overrides.pop(security_module.get_db, None)


def _iso_utc(dt: datetime) -> str:
    """Formatiruet datetime v ISO UTC bez mikrosekund."""

    return dt.replace(tzinfo=timezone.utc, microsecond=0).isoformat().replace("+00:00", "Z")


def _post_shadow_state(client: TestClient, payload: dict) -> None:
    """Otpravlyaet sostoyanie ustroystva v shadow debug endpoint."""

    response = client.post("/_debug/shadow/state", json=payload)
    assert response.status_code == 200, response.text


def _insert_device(device_id: str, *, last_seen: datetime | None, user_id: int | None = None) -> None:
    """Dobavlyaet ali obnovlyaet zapis ustroystva v sqlite dlya fallback stsenariev."""

    _create_tables()
    session = SessionLocal()
    try:
        session.query(DeviceDB).filter(DeviceDB.device_id == device_id).delete()
        device = DeviceDB(device_id=device_id)
        device.last_seen = last_seen
        device.user_id = user_id
        session.add(device)
        session.commit()
    finally:
        session.close()


def _create_user(email: str, password: str) -> int:
    """Translitem: sozdaet polzovatelya v lokalnoj testovoj baze."""

    _create_tables()
    session = SessionLocal()
    try:
        user = create_local_user(session, email, None, "user", password)
        session.refresh(user)
        return user.id
    finally:
        session.close()


def _owner_headers(client: TestClient, device_id: str, *, last_seen: datetime | None = None) -> dict[str, str]:
    owner_id = _create_user("owner@example.com", "secret")
    _insert_device(device_id, last_seen=last_seen, user_id=owner_id)
    token = create_access_token({"user_id": owner_id})
    return {"Authorization": f"Bearer {token}"}


def test_status_idle_returns_idle_and_no_remaining() -> None:
    """Proveryaet otrabotku idle statusa bez vremeni ozhidaniya."""

    with TestClient(app) as client:
        headers = _owner_headers(client, "abc123")
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

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"}, headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "idle"
    assert data["remaining_s"] is None
    assert data["source"] == "calculated"
    assert data["is_online"] is True
    assert data["offline_reason"] is None
    assert data["updated_at"] == data["last_seen_at"]
    assert data["last_seen_at"].endswith("Z")
    assert data["start_time"] is None
    assert data["duration"] is None


def test_status_running_calculates_remaining_seconds() -> None:
    """Proveryaet raschet ostavshegosya vremeni pri running."""

    started_at = datetime.utcnow() - timedelta(seconds=5)

    with TestClient(app) as client:
        headers = _owner_headers(client, "abc123")
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

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"}, headers=headers)

    assert response.status_code == 200
    data = response.json()
    expected_remaining = max(0, 20 - int((datetime.utcnow() - started_at).total_seconds()))
    assert data["status"] == "running"
    assert max(0, expected_remaining - 2) <= data["remaining_s"] <= expected_remaining + 2
    assert data["duration_s"] == 20
    assert data["duration"] == 20
    assert data["correlation_id"] == "corr-running"
    assert data["is_online"] is True
    assert data["offline_reason"] is None
    assert data["start_time"] == _iso_utc(started_at)


def test_status_running_expired_returns_zero() -> None:
    """Proveryaet chto po istechenii vremeni ostavshiesya sekundy raven nulju i offline_reason ustanovlen."""

    started_at = datetime.utcnow() - timedelta(seconds=20)

    base_settings = config.get_settings()
    custom_settings = replace(base_settings, DEVICE_ONLINE_THRESHOLD_S=-1)

    with patch("config.get_settings", return_value=custom_settings), patch("app.mqtt.store.get_settings", return_value=custom_settings):
        with TestClient(app) as client:
            headers = _owner_headers(client, "abc123")
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

            response = client.get("/api/manual-watering/status", params={"device_id": "abc123"}, headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "running"
    assert data["remaining_s"] == 0
    assert data["is_online"] is False
    assert data["offline_reason"] == "device_offline"
    assert data["duration"] == 10
    assert data["start_time"] == _iso_utc(started_at)


def test_status_without_state_returns_placeholder() -> None:
    """Proveryaet placeholder pri otsutstvii shadow dannyh."""

    with TestClient(app) as client:
        headers = _owner_headers(
            client,
            "unknown",
            last_seen=datetime.utcnow() - timedelta(minutes=10),
        )
        response = client.get("/api/manual-watering/status", params={"device_id": "unknown"}, headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert data["is_online"] is False
    assert data["offline_reason"] == "device_offline"
    assert data["status"] == "idle"
    assert data["source"] in {"db_fallback", "fallback"}
    assert data["start_time"] is None
    assert data["duration"] in {0, None}


def test_status_db_fallback_uses_device_record_for_online_state() -> None:
    """Proveryaet chto pri otsutstvii teni, no so svyazannoy zapisyu v Baze, vozvrashaetsya db_fallback."""

    device_id = "db-fallback-online"
    last_seen = datetime.utcnow().replace(microsecond=0)
    with TestClient(app) as client:
        headers = _owner_headers(client, device_id, last_seen=last_seen)
        response = client.get("/api/manual-watering/status", params={"device_id": device_id}, headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert data["source"] == "db_fallback"
    assert data["status"] == "idle"
    assert data["duration_s"] is None
    assert data["is_online"] is True
    assert data["offline_reason"] is None
    assert data["last_seen_at"] == _iso_utc(last_seen)
    assert data["updated_at"] == data["last_seen_at"]
    assert data["duration"] == 0
    assert data["start_time"] is None


def test_status_db_fallback_marks_offline_for_stale_device() -> None:
    """Proveryaet chto pri starom last_seen fallback stroit offline otvet s prichinoy."""

    device_id = "db-fallback-offline"
    last_seen = datetime.utcnow().replace(microsecond=0) - timedelta(minutes=5)
    with TestClient(app) as client:
        headers = _owner_headers(client, device_id, last_seen=last_seen)
        response = client.get("/api/manual-watering/status", params={"device_id": device_id}, headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert data["source"] == "db_fallback"
    assert data["status"] == "idle"
    assert data["is_online"] is False
    assert data["offline_reason"] == "device_offline"
    assert data["last_seen_at"] == _iso_utc(last_seen)
    assert data["duration"] == 0
    assert data["start_time"] is None


def test_debug_shadow_state_disabled_when_debug_false(monkeypatch) -> None:
    """Proveryaet chto debug endpoint nedostupen kogda DEBUG=FALSE."""

    monkeypatch.setenv("DEBUG", "false")
    config.get_settings.cache_clear()

    module_path = Path(__file__).resolve().parents[1] / "app" / "fastapi" / "routers" / "manual_watering.py"
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

