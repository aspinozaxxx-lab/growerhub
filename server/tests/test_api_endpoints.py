"""
Integration tests for the core FastAPI endpoints of the GrowerHub server.

These tests spin up the application using an in-memory SQLite database so
that they can run in isolation without requiring a running PostgreSQL
instance.  Before importing the FastAPI application the database engine
and session factory defined in ``app.core.database`` are patched to use
SQLite.  The ``create_tables`` function is also replaced so that
``Base.metadata.create_all`` operates on the in-memory engine.  Finally
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

Running these tests requires pytest and FastAPI’s TestClient.  The
package dependencies are already declared in the project’s
``requirements.txt``.
"""

# This comment triggers CI run.
import pytest

"""
Test suite for GrowerHub FastAPI endpoints.  This module includes a
lightweight fallback mechanism for missing optional dependencies.  If
the third-party packages ``httpx`` or ``pytest_asyncio`` are not
available in the execution environment (as is the case in some CI
pipelines), the tests will automatically load local stub
implementations.  These stubs provide the minimal API surface needed
for the tests to run without installing external libraries.

The fallback for ``httpx`` loads the stub from ``server/httpx.py``
relative to this file.  The fallback for ``psycopg2`` registers a
dummy module in ``sys.modules`` so that SQLAlchemy does not attempt
to import the real Postgres driver when using an in-memory SQLite
database.  Finally, if ``pytest_asyncio`` is not installed, a simple
shim is defined which wraps ``pytest.fixture`` to allow the ``async``
fixture syntax used in the test functions below.
"""

# ---------------------------------------------------------------------------
# Define a fallback for pytest_asyncio when the package is missing.
try:
    import pytest_asyncio  # type: ignore[unused-import]
except ImportError:
    import types as _types

    def _asyncio_fixture(func=None, **kwargs):
        import pytest  # local import to avoid circular import at module level

        if func is not None:
            return pytest.fixture(func)
        return pytest.fixture(**kwargs)

    pytest_asyncio = _types.SimpleNamespace(fixture=_asyncio_fixture)  # type: ignore[assignment]

import sys
import types

# ---------------------------------------------------------------------------
# Ensure psycopg2 can be imported even if the real driver is not installed.
# SQLAlchemy’s PostgreSQL dialect tries to import psycopg2; this stub
# prevents ModuleNotFoundError during test setup when using SQLite.
try:
    import psycopg2  # type: ignore[unused-import]
except ModuleNotFoundError:
    psycopg2 = types.ModuleType("psycopg2")  # type: ignore[assignment]
    sys.modules["psycopg2"] = psycopg2  # type: ignore[assignment]

import importlib.util as _importlib_util
import pathlib

# ---------------------------------------------------------------------------
# Import httpx or fall back to the local stub if httpx is not installed.
# The stub is implemented in ``server/httpx.py`` and provides
# AsyncClient and ASGITransport used by the tests.
try:
    import httpx  # type: ignore[assignment]
except ModuleNotFoundError:
    stub_path = (pathlib.Path(__file__).resolve().parent.parent / "httpx.py").resolve()
    spec = _importlib_util.spec_from_file_location("httpx", stub_path)
    if spec is None or spec.loader is None:
        raise ImportError(f"Could not load httpx stub from {stub_path}")
    httpx = _importlib_util.module_from_spec(spec)  # type: ignore[assignment]
    spec.loader.exec_module(httpx)  # type: ignore[operator]
    sys.modules["httpx"] = httpx

import sqlalchemy
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base
import app.core.database as db

# ---------------------------------------------------------------------------
# Configure an in-memory SQLite database for testing
SQLALCHEMY_TEST_URL = "sqlite:///:memory:"
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


# Replace the ``create_tables`` function to use our in-memory engine
db.create_tables = _create_all_tables  # type: ignore[assignment]

# Import the FastAPI application only after the DB has been patched.
from app.main import app as fastapi_app  # noqa: E402  import after patch

# Ensure the tables exist.
_create_all_tables()

# Override get_db to provide a session bound to the test engine.
def override_get_db():
    db_session = TestingSessionLocal()
    try:
        yield db_session
    finally:
        db_session.close()

fastapi_app.dependency_overrides[db.get_db] = override_get_db

# ---------------------------------------------------------------------------
# Define an asynchronous client fixture for making requests against the FastAPI app.
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
