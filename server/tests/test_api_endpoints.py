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
import pytest_asyncio
import sqlalchemy
import sys
# Import httpx or fall back to the local stub if the package is not installed.
# When running in the CI environment the ``httpx`` dependency may not be
# available.  The tests rely on ``httpx.AsyncClient`` and ``httpx.ASGITransport``
# but we provide a lightweight stub in ``server/httpx.py``.  Attempt to import
# the real package first; if that fails load the stub module manually.
try:
    import httpx  # type: ignore[assignment]
except ModuleNotFoundError:
    import importlib.util
    import sys
    import pathlib

    # Construct the path to the stub relative to this test file.  The stub
    # lives one directory above (``server/httpx.py``).  Resolve the path
    # to ensure an absolute location.
    stub_path = (pathlib.Path(__file__).resolve().parent.parent / "httpx.py").resolve()
    spec = importlib.util.spec_from_file_location("httpx", stub_path)
    if spec is None or spec.loader is None:
        raise ImportError(f"Could not load httpx stub from {stub_path}")
    httpx = importlib.util.module_from_spec(spec)  # type: ignore[assignment]
    # Execute the module to populate its namespace.
    spec.loader.exec_module(httpx)
    # Insert the loaded module into sys.modules so subsequent imports of
    # ``httpx`` return this stub.
    sys.modules["httpx"] = httpx

# ---------------------------------------------------------------------------
# Ensure ``psycopg2`` can be imported in environments where the real driver
# is unavailable.  SQLAlchemy's PostgreSQL dialect attempts to import
# ``psycopg2`` when constructing an engine from a URL beginning with
# ``postgresql://``.  In our tests we patch the database engine to use
# SQLite, so the PostgreSQL driver is never actually used.  However, the
# import still happens at module import time and will raise a
# ModuleNotFoundError if ``psycopg2`` is not installed.  To prevent the
# error we provide a minimal stub module and register it in
# ``sys.modules`` before importing the application's database module.  This
# stub is intentionally empty because the tests never call any
# ``psycopg2`` APIs.
try:
    import psycopg2  # type: ignore[assignment]
except ModuleNotFoundError:
    import types
    # Create an empty module object and insert it into sys.modules.  This
    # satisfies import statements like ``import psycopg2`` used by
    # SQLAlchemy's PostgreSQL dialect.  Should any code attempt to use
    # attributes of this stub they will raise AttributeError, which is
    # acceptable because in the test environment we never hit that code path.
    psycopg2 = types.ModuleType("psycopg2")  # type: ignore[assignment]
    sys.modules["psycopg2"] = psycopg2  # type: ignore[assignment]
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

# Define an asynchronous client fixture for making requests against the FastAPI app.
#
# We use httpx.AsyncClient together with ASGITransport so that each test can await
# HTTP requests directly against the in-process FastAPI application.  The base_url
# is required when making requests with httpx.
@pytest_asyncio.fixture
async def async_client() -> httpx.AsyncClient:
    transport = httpx.ASGITransport(app=fastapi_app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        yield client


@pytest.mark.asyncio
async def test_get_all_devices_empty(async_client) -> None:
    """Ensure that the device list is empty when no devices have reported."""
    response = await async_client.get("/api/devices")
    assert response.status_code == 200
    assert response.json() == []


@pytest.mark.asyncio
async def test_update_device_status_creates_device_and_returns_message(async_client) -> None:
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
    response = await async_client.post("/api/device/testdevice/status", json=payload)
    assert response.status_code == 200
    assert response.json()["message"] == "Status updated"

    # The device list should now include ``testdevice``
    list_response = await async_client.get("/api/devices")
    assert list_response.status_code == 200
    assert any(d["device_id"] == "testdevice" for d in list_response.json())


@pytest.mark.asyncio
async def test_get_device_settings_returns_defaults_for_new_device(async_client) -> None:
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
    status_response = await async_client.post("/api/device/dev1/status", json=payload)
    assert status_response.status_code == 200

    # Fetch the settings and verify the defaults
    response = await async_client.get("/api/device/dev1/settings")
    assert response.status_code == 200
    data = response.json()
    assert data["target_moisture"] == 40.0
    assert data["watering_duration"] == 30
    assert data["watering_timeout"] == 300
    assert data["light_on_hour"] == 6
    assert data["light_off_hour"] == 22
    assert data["light_duration"] == 16
    assert data["update_available"] is False


@pytest.mark.asyncio
async def test_update_device_settings_updates_values(async_client) -> None:
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
    status_resp = await async_client.post("/api/device/dev2/status", json=payload_status)
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
    update_resp = await async_client.put("/api/device/dev2/settings", json=new_settings)
    assert update_resp.status_code == 200
    assert update_resp.json()["message"] == "Settings updated"

    # Retrieve settings to check that the values were updated
    settings_response = await async_client.get("/api/device/dev2/settings")
    assert settings_response.status_code == 200
    data = settings_response.json()
    for key, value in new_settings.items():
        assert data[key] == value


@pytest.mark.asyncio
async def test_firmware_check_no_update_available(async_client) -> None:
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
    resp = await async_client.post("/api/device/dev3/status", json=payload_status)
    assert resp.status_code == 200

    # Check firmware status
    response = await async_client.get("/api/device/dev3/firmware")
    assert response.status_code == 200
    data = response.json()
    assert data["update_available"] is False