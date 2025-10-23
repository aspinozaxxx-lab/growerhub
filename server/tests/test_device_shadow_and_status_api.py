from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.main import app


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


def test_status_not_found_returns_404():
    """Для неизвестного устройства возвращаем 404, чтобы фронт показал заглушку."""

    with TestClient(app) as client:
        response = client.get("/api/manual-watering/status", params={"device_id": "no-such"})
        assert response.status_code == 404
