"""
Integration tests for the core FastAPI endpoints of the GrowerHub server.

These tests spin up the application using an in‑memory SQLite database so
that they can run in isolation without requiring a running PostgreSQL
instance.  Before importing the FastAPI application the database engine
and session factory defined in ``app.core.database`` are patched to use
SQLite.  The ``create_tables`` function is also replaced so that
``Base.metadata.create_all`` operates on the in‑memory engine.  Finally
the ``get_db`` dependency is overridden so that each request gets a
fresh session bound to the test engine.

The five tests included cover the most important behaviours of the
server:

* retrieving an empty list of devices when none have reported status;
* creating a new device via the status endpoint and verifying it
  appears in the device list;
* reading the default settings for a newly created device and checking
  they match the defaults defined in the data model;
* updating the settings for an existing device and ensuring those
  changes persist;
* checking for firmware updates on a device which has no pending
  update and confirming the API signals that appropriately.

Running these tests requires pytest and FastAPI's TestClient.  The
package dependencies are already declared in the project's
``requirements.txt``.
"""

import pytest
import sqlalchemy
from fastapi.testclient import TestClient
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base
import app.core.database as db


# ---------------------------------------------------------------------------
# Configure an in‑memory SQLite database for testing
#
# The application relies on ``app.core.database`` to provide the SQLAlchemy
# engine, a ``SessionLocal`` factory and a ``create_tables`` function.  At
# import time the FastAPI app calls ``create_tables()`` which would
# otherwise connect to the production PostgreSQL instance.  Here we
# override the relevant attributes before importing the app so that
# everything points at our SQLite engine instead.

# Use a SQLite database living purely in memory.  ``check_same_thread=False``
# is required when using SQLite with SQLAlchemy in a multi‑threaded context
# (the TestClient runs the application in a separate thread).
SQLALCHEMY_TEST_URL = "sqlite:///:memory:"

# Create our test engine and session factory
engine = sqlalchemy.create_engine(
    SQLALCHEMY_TEST_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(
    autocommit=False, autoflush=False, bind=engine
)

# Patch the database configuration used by the application
db.SQLALCHEMY_DATABASE_URL = SQLALCHEMY_TEST_URL  # type: ignore[assignment]
db.engine = engine  # type: ignore[assignment]
db.SessionLocal = TestingSessionLocal  # type: ignore[assignment]


def _create_all_tables() -> None:
    """Create all database tables on the test engine."""
    Base.metadata.create_all(bind=engine)


# Replace the ``create_tables`` function to use our in‑memory engine
db.create_tables = _create_all_tables  # type: ignore[assignment]


# Only now import the FastAPI application.  On import the application will
# call ``create_tables()`` (our patched version) so that the database
# schema is created on our SQLite engine.
from app.main import app as fastapi_app  # noqa: E402  import after patch


# Ensure the tables exist.  This call is idempotent and safe to run
# multiple times.
_create_all_tables()


def override_get_db():
    """Dependency override to provide a session bound to the test engine."""
    db_session = TestingSessionLocal()
    try:
        yield db_session
    finally:
        db_session.close()


# Apply the dependency override
fastapi_app.dependency_overrides[db.get_db] = override_get_db

# Construct a TestClient using the patched FastAPI application
client = TestClient(fastapi_app)


def test_get_all_devices_empty() -> None:
    """Ensure that the device list is empty when no devices have reported."""
    response = client.get("/api/devices")
    assert response.status_code == 200
    assert response.json() == []


def test_update_device_status_creates_device_and_returns_message() -> None:
    """
    Posting status for a new device should return a success message and
    cause the device to appear in the list of devices.
    """
    payload = {
        "device_id": "testdevice",
        "soil_moisture": 30.0,
        "air_temperature": 22.5,
        "air_humidity": 45.0,
        "is_watering": False,
        "is_light_on": True,
        "last_watering": None,
    }
    response = client.post("/api/device/testdevice/status", json=payload)
    assert response.status_code == 200
    assert response.json()["message"] == "Status updated"

    # The device list should now include ``testdevice``
    list_response = client.get("/api/devices")
    assert list_response.status_code == 200
    assert any(d["device_id"] == "testdevice" for d in list_response.json())


def test_get_device_settings_returns_defaults_for_new_device() -> None:
    """
    When requesting settings for a device that has only reported status
    once, the API should return the default settings defined in the
    ``DeviceDB`` model.  These defaults are asserted explicitly here.
    """
    payload = {
        "device_id": "dev1",
        "soil_moisture": 55.0,
        "air_temperature": 20.0,
        "air_humidity": 40.0,
        "is_watering": False,
        "is_light_on": False,
        "last_watering": None,
    }
    # Create the device by posting its status
    status_response = client.post("/api/device/dev1/status", json=payload)
    assert status_response.status_code == 200

    # Fetch the settings and verify the defaults
    response = client.get("/api/device/dev1/settings")
    assert response.status_code == 200
    data = response.json()
    assert data["target_moisture"] == 40.0
    assert data["watering_duration"] == 30
    assert data["watering_timeout"] == 300
    assert data["light_on_hour"] == 6
    assert data["light_off_hour"] == 22
    assert data["light_duration"] == 16
    assert data["update_available"] is False


def test_update_device_settings_updates_values() -> None:
    """
    Updating a device's settings should persist the provided values.
    After calling the PUT endpoint the subsequent GET request should
    reflect the new values.
    """
    # Create a device first
    payload_status = {
        "device_id": "dev2",
        "soil_moisture": 50.0,
        "air_temperature": 19.0,
        "air_humidity": 35.0,
        "is_watering": False,
        "is_light_on": False,
        "last_watering": None,
    }
    status_resp = client.post("/api/device/dev2/status", json=payload_status)
    assert status_resp.status_code == 200

    # Now update its settings
    new_settings = {
        "target_moisture": 60.0,
        "watering_duration": 45,
        "watering_timeout": 400,
        "light_on_hour": 7,
        "light_off_hour": 21,
        "light_duration": 14,
    }
    update_resp = client.put("/api/device/dev2/settings", json=new_settings)
    assert update_resp.status_code == 200
    assert update_resp.json()["message"] == "Settings updated"

    # Retrieve settings to check that the values were updated
    settings_response = client.get("/api/device/dev2/settings")
    assert settings_response.status_code == 200
    data = settings_response.json()
    for key, value in new_settings.items():
        assert data[key] == value


def test_firmware_check_no_update_available() -> None:
    """
    If a device has no pending firmware update, the firmware check
    endpoint should return ``{"update_available": false}``.
    """
    # Register a device
    payload_status = {
        "device_id": "dev3",
        "soil_moisture": 25.0,
        "air_temperature": 18.0,
        "air_humidity": 50.0,
        "is_watering": False,
        "is_light_on": False,
        "last_watering": None,
    }
    resp = client.post("/api/device/dev3/status", json=payload_status)
    assert resp.status_code == 200

    # Check firmware status
    response = client.get("/api/device/dev3/firmware")
    assert response.status_code == 200
    data = response.json()
    assert data["update_available"] is False