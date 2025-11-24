import sys
import types
from datetime import datetime

import config
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.security import create_access_token
from app.models.database_models import Base, DeviceDB
from app.repositories.users import create_local_user

# --- Sozdaem izolirovannyy in-memory DB, chtoby testy ne zaviseli ot realnoy bazy. ---
engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(autouse=True)
def enable_debug(monkeypatch):
    """Vklyuchaet DEBUG dlya dostupnosti debug endpointov v testah."""

    monkeypatch.setenv("DEBUG", "true")
    config.get_settings.cache_clear()
    config.get_settings()
    yield
    config.get_settings.cache_clear()


def _create_tables() -> None:
    """Sozdaet sqlite shemu v pamyati dlya testov."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Generator s testovoy sessiyey SQLAlchemy."""

    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# --- Podmenyaem modul app.core.database na zaglushku s nashey in-memory bazoy. ---
stub_database = types.ModuleType("app.core.database")
stub_database.engine = engine
stub_database.SessionLocal = SessionLocal
stub_database.create_tables = _create_tables
stub_database.get_db = _get_db
sys.modules["app.core.database"] = stub_database

from app.fastapi.routers.manual_watering import get_mqtt_dep  # noqa: E402  # import posle podmeny modula
from app.main import app  # noqa: E402
from app.mqtt.serialization import CmdPumpStop, CommandType  # noqa: E402
from app.mqtt.interfaces import IMqttPublisher  # noqa: E402


def _create_user(email: str, password: str) -> int:
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        user = create_local_user(session, email, None, "user", password)
        session.refresh(user)
        return user.id
    finally:
        session.close()


def _insert_device(device_id: str, user_id: int) -> None:
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        session.query(DeviceDB).filter(DeviceDB.device_id == device_id).delete()
        device = DeviceDB(device_id=device_id, name=f"Device {device_id}", user_id=user_id)
        session.add(device)
        session.commit()
    finally:
        session.close()


@pytest.fixture(autouse=True)
def override_db_dependencies():
    import app.core.database as database_module  # noqa: WPS433
    import app.core.security as security_module  # noqa: WPS433

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    app.dependency_overrides[database_module.get_db] = _get_db
    app.dependency_overrides[security_module.get_db] = _get_db
    yield
    app.dependency_overrides.pop(database_module.get_db, None)
    app.dependency_overrides.pop(security_module.get_db, None)


class FakePublisher(IMqttPublisher):
    """Feikovyi MQTT-pablisher dlya testa stop manual watering."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStop]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStop) -> None:
        """Sohranyaet komandu stop dlya proverki v teste."""

        self.published.append((device_id, cmd))


def test_manual_watering_stop_endpoint():
    """Proveryaet chto endpoint stop vozvrashaet correlation_id i publikuet pump.stop."""

    # Podmenyaem zavisimost FastAPI na feikovyy publisher: on ne hodit v broker.
    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    # Vypolnyaem POST-zapros na ostanovku poliva.
    with TestClient(app) as client:
        user_id = _create_user("owner@example.com", "secret")
        _insert_device("abc123", user_id=user_id)
        token = create_access_token({"user_id": user_id})
        headers = {"Authorization": f"Bearer {token}"}
        seed_state = {
            "device_id": "abc123",
            "state": {
                "manual_watering": {
                    "status": "running",
                    "duration_s": 30,
                    "started_at": datetime.utcnow().replace(microsecond=0).isoformat() + "Z",
                    "correlation_id": "shadow-correlation",
                }
            },
        }
        shadow_response = client.post("/_debug/shadow/state", json=seed_state)
        assert shadow_response.status_code == 200

        response = client.post(
            "/api/manual-watering/stop",
            json={"device_id": "abc123"},
            headers=headers,
        )

    # Vozvrashaem zavisimosti v iskhodnoe sostoyanie, chtoby ne vliyat na drugie testy.
    app.dependency_overrides.clear()

    # Proveryaem status otveta i nalichie korrelation_id.
    assert response.status_code == 200
    data = response.json()
    correlation_id = data.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    # Ubezhdaemsya chto opublikovana rovno odna komanda i eto pump.stop.
    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdPumpStop)
    assert cmd.type == CommandType.pump_stop.value
    assert cmd.correlation_id == correlation_id
    assert cmd.ts is not None
