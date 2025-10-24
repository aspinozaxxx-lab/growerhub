"""Тесты статуса ручного полива: проверяем расчёт оставшегося времени и онлайн-признаков."""

from datetime import datetime, timedelta, timezone
from fastapi.testclient import TestClient

from app.main import app
from device_shadow import get_shadow_store


def _iso_utc(dt: datetime) -> str:
    """Форматируем дату в ISO 8601 с Z, чтобы совпадало с тем, что ожидает Pydantic."""

    return dt.replace(tzinfo=timezone.utc, microsecond=0).isoformat().replace("+00:00", "Z")


def test_status_idle_returns_idle_and_no_remaining():
    """Проверяем сценарий простоя: remaining_s отсутствует, статус idle."""

    with TestClient(app) as client:
        payload = {
            "device_id": "abc123",
            "state": {
                "manual_watering": {
                    "status": "idle",
                    "duration_s": None,
                    "started_at": None,
                    "correlation_id": None,
                }
            },
        }
        client.post("/_debug/shadow/state", json=payload)

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "idle"
        assert data["remaining_s"] is None
        assert data["source"] == "calculated"
        assert data["is_online"] is True
        assert data["updated_at"] == data["last_seen_at"]
        assert data["last_seen_at"].endswith('Z')


def test_status_running_calculates_remaining_seconds():
    """Устройство в состоянии running: сервер уменьшает оставшееся время."""

    now = datetime.utcnow()
    started_at = now - timedelta(seconds=5)

    with TestClient(app) as client:
        payload = {
            "device_id": "abc123",
            "state": {
                "manual_watering": {
                    "status": "running",
                    "duration_s": 20,
                    "started_at": _iso_utc(started_at),
                    "correlation_id": "corr-running",
                }
            },
        }
        client.post("/_debug/shadow/state", json=payload)

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "running"
        expected_remaining = max(0, 20 - int((datetime.utcnow() - started_at).total_seconds()))
        lower_bound = max(0, expected_remaining - 2)
        upper_bound = expected_remaining + 2
        assert lower_bound <= data["remaining_s"] <= upper_bound
        assert data["duration_s"] == 20
        assert data["correlation_id"] == "corr-running"
        assert data["is_online"] is True
        assert data["updated_at"] == data["last_seen_at"]


def test_status_running_expired_returns_zero():
    """Если насос уже должен был остановиться, remaining_s обнуляется."""

    now = datetime.utcnow()
    started_at = now - timedelta(seconds=20)

    with TestClient(app) as client:
        payload = {
            "device_id": "abc123",
            "state": {
                "manual_watering": {
                    "status": "running",
                    "duration_s": 10,
                    "started_at": _iso_utc(started_at),
                    "correlation_id": "corr-expired",
                }
            },
        }
        client.post("/_debug/shadow/state", json=payload)

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "running"
        assert data["remaining_s"] == 0
        assert data["is_online"] is True


def test_status_marks_device_offline_when_state_is_stale():
    """Если тень давно не обновлялась, is_online должен стать False, а last_seen_at показать старый таймстемп."""

    with TestClient(app) as client:
        payload = {
            "device_id": "abc123",
            "state": {
                "manual_watering": {
                    "status": "idle",
                    "duration_s": None,
                    "started_at": None,
                    "correlation_id": None,
                }
            },
        }
        client.post("/_debug/shadow/state", json=payload)

        store = get_shadow_store()
        stale_at = datetime.utcnow() - timedelta(seconds=30)
        with store._lock:
            state, _ = store._storage["abc123"]
            store._storage["abc123"] = (state, stale_at)

        response = client.get("/api/manual-watering/status", params={"device_id": "abc123"})
        assert response.status_code == 200
        data = response.json()
        expected_seen = _iso_utc(stale_at)
        assert data["is_online"] is False
        assert data["last_seen_at"] == expected_seen
        assert data["updated_at"] == expected_seen


def test_status_not_found_returns_404():
    """Для неизвестного устройства возвращаем 404, чтобы фронт показал заглушку."""

    with TestClient(app) as client:
        response = client.get("/api/manual-watering/status", params={"device_id": "no-such"})
        assert response.status_code == 404
