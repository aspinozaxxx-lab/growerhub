from datetime import datetime, timedelta
from typing import Iterator

import pytest
from fastapi.testclient import TestClient

import app.main
from app.core.database import get_db
from app.models.database_models import (
    DeviceDB,
    PlantDB,
    PlantDeviceDB,
    PlantJournalEntryDB,
    PlantJournalWateringDetailsDB,
    SensorDataDB,
)
from tests.test_auth_users import (
    TestingSessionLocal,
    _override_get_db,
    _patched_client,
    _test_db_create_schema,
    _test_db_drop_schema,
)


@pytest.fixture
def client() -> Iterator[TestClient]:
    _test_db_drop_schema()
    _test_db_create_schema()
    import app.core.security as security_module  # noqa: WPS433

    app.main.app.dependency_overrides[get_db] = _override_get_db
    app.main.app.dependency_overrides[security_module.get_db] = _override_get_db
    try:
        with _patched_client() as patched:
            yield patched
    finally:
        app.main.app.dependency_overrides.pop(get_db, None)
        app.main.app.dependency_overrides.pop(security_module.get_db, None)
        _test_db_drop_schema()


def _iso_to_datetime(value: str) -> datetime:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except Exception:  # pragma: no cover - defensive fallback
        return datetime.min


def test_sensor_history_filters_by_hours(client: TestClient) -> None:
    now = datetime.utcnow()
    session = TestingSessionLocal()
    try:
        fresh = SensorDataDB(
            device_id="test-device-1",
            timestamp=now - timedelta(hours=2),
            soil_moisture=31.5,
            air_temperature=24.2,
            air_humidity=52.1,
        )
        stale = SensorDataDB(
            device_id="test-device-1",
            timestamp=now - timedelta(days=2),
            soil_moisture=28.0,
            air_temperature=19.4,
            air_humidity=40.0,
        )
        other_device = SensorDataDB(
            device_id="other-device",
            timestamp=now - timedelta(hours=1),
            soil_moisture=44.0,
            air_temperature=21.0,
            air_humidity=48.0,
        )
        session.add_all([fresh, stale, other_device])
        session.commit()
    finally:
        session.close()

    response = client.get("/api/device/test-device-1/sensor-history", params={"hours": 24})

    assert response.status_code == 200
    points = response.json()
    assert len(points) == 1
    point = points[0]
    assert set(point.keys()) == {"timestamp", "soil_moisture", "air_temperature", "air_humidity"}
    ts = _iso_to_datetime(point["timestamp"])
    assert ts >= now - timedelta(hours=24)
    assert point["soil_moisture"] == pytest.approx(31.5)
    assert point["air_temperature"] == pytest.approx(24.2)
    assert point["air_humidity"] == pytest.approx(52.1)


def test_watering_logs_filters_by_days(client: TestClient) -> None:
    now = datetime.utcnow()
    session = TestingSessionLocal()
    try:
        device = DeviceDB(device_id="test-device-2", name="History Device")
        plant = PlantDB(name="History Plant")
        session.add_all([device, plant])
        session.commit()
        session.add(PlantDeviceDB(plant_id=plant.id, device_id=device.id))
        recent_entry = PlantJournalEntryDB(
            plant_id=plant.id,
            user_id=None,
            type="watering",
            text="recent watering",
            event_at=now - timedelta(days=1),
        )
        old_entry = PlantJournalEntryDB(
            plant_id=plant.id,
            user_id=None,
            type="watering",
            text="old watering",
            event_at=now - timedelta(days=10),
        )
        session.add_all([recent_entry, old_entry])
        session.flush()
        recent_details = PlantJournalWateringDetailsDB(
            journal_entry_id=recent_entry.id,
            water_volume_l=0.5,
            duration_s=120,
            ph=6.2,
            fertilizers_per_liter="NPK 10-10-10",
        )
        old_details = PlantJournalWateringDetailsDB(
            journal_entry_id=old_entry.id,
            water_volume_l=0.3,
            duration_s=90,
            ph=None,
            fertilizers_per_liter=None,
        )
        session.add_all([recent_details, old_details])
        session.commit()
    finally:
        session.close()

    response = client.get("/api/device/test-device-2/watering-logs", params={"days": 7})

    assert response.status_code == 200
    logs = response.json()
    assert len(logs) == 1
    log = logs[0]
    assert "start_time" in log
    assert "duration" in log
    assert "water_used" in log
    assert "ph" in log
    assert "fertilizers_per_liter" in log
    ts = _iso_to_datetime(log["start_time"])
    assert ts >= now - timedelta(days=7)
    assert log["duration"] == 120
    assert log["water_used"] == pytest.approx(0.5)
    assert log["ph"] == pytest.approx(6.2)
    assert log["fertilizers_per_liter"] == "NPK 10-10-10"


def test_history_missing_device_returns_empty_list(client: TestClient) -> None:
    sensor_resp = client.get("/api/device/absent-device/sensor-history", params={"hours": 24})
    watering_resp = client.get("/api/device/absent-device/watering-logs", params={"days": 7})

    assert sensor_resp.status_code == 200
    assert watering_resp.status_code == 200
    assert sensor_resp.json() == []
    assert watering_resp.json() == []


def test_sensor_history_downsamples_to_200_points(client: TestClient) -> None:
    now = datetime.utcnow()
    session = TestingSessionLocal()
    try:
        bulk = [
            SensorDataDB(
                device_id="device-many-points",
                timestamp=now - timedelta(minutes=i),
                soil_moisture=30.0 + (i % 5),
                air_temperature=20.0 + (i % 3),
                air_humidity=50.0 + (i % 4),
            )
            for i in range(1000)
        ]
        session.add_all(bulk)
        session.commit()
    finally:
        session.close()

    resp = client.get("/api/device/device-many-points/sensor-history", params={"hours": 1000})
    assert resp.status_code == 200
    payload = resp.json()
    assert len(payload) <= 200
    # chronological order preserved
    timestamps = [_iso_to_datetime(item["timestamp"]) for item in payload]
    assert timestamps == sorted(timestamps)


def test_sensor_history_filters_outliers(client: TestClient) -> None:
    now = datetime.utcnow()
    session = TestingSessionLocal()
    try:
        normal = [
            SensorDataDB(
                device_id="device-outliers",
                timestamp=now - timedelta(minutes=i),
                soil_moisture=40.0,
                air_temperature=22.0,
                air_humidity=55.0,
            )
            for i in range(10)
        ]
        outliers = [
            SensorDataDB(
                device_id="device-outliers",
                timestamp=now - timedelta(minutes=200),
                soil_moisture=150.0,
                air_temperature=-273.0,
                air_humidity=1000.0,
            ),
            SensorDataDB(
                device_id="device-outliers",
                timestamp=now - timedelta(minutes=201),
                soil_moisture=-10.0,
                air_temperature=999.0,
                air_humidity=-5.0,
            ),
        ]
        session.add_all(normal + outliers)
        session.commit()
    finally:
        session.close()

    resp = client.get("/api/device/device-outliers/sensor-history", params={"hours": 500})
    assert resp.status_code == 200
    payload = resp.json()
    assert len(payload) == len(normal)  # все выбросы отфильтрованы
    for point in payload:
        assert -20.0 <= point["air_temperature"] <= 60.0
        assert 0.0 <= point["air_humidity"] <= 100.0
        assert 0.0 <= point["soil_moisture"] <= 100.0
