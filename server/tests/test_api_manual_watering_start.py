import sys
import types

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.core.security import create_access_token
from app.models.database_models import Base, DeviceDB
from app.repositories.users import create_local_user

engine = create_engine(
    "sqlite:///./test_manual_watering_start.db",
    connect_args={"check_same_thread": False},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def _create_tables() -> None:
    """Sozdaet sqlite shemu v pamyati dlya testov."""
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

from app.fastapi.routers.manual_watering import get_mqtt_dep
from app.main import app
from app.mqtt.serialization import CmdPumpStart, CommandType
from app.mqtt.interfaces import IMqttPublisher


def _create_user(email: str, password: str) -> int:
    _create_tables()
    session = SessionLocal()
    try:
        user = create_local_user(session, email, None, "user", password)
        session.refresh(user)
        return user.id
    finally:
        session.close()


def _insert_device(device_id: str, user_id: int) -> None:
    _create_tables()
    session = SessionLocal()
    try:
        session.query(DeviceDB).filter(DeviceDB.device_id == device_id).delete()
        device = DeviceDB(device_id=device_id, user_id=user_id)
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
    """Feikovyi MQTT-pablisher dlya testa starta manual watering."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStart]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStart) -> None:
        self.published.append((device_id, cmd))


def test_manual_watering_start_endpoint():
    """Proveryaet chto endpoint start publikuet pump.start i vozvrashaet correlation_id."""

    # Podmenyaem zavisimost na nash feikovyi publisher, chtoby ne obraschatsya k realnomu brokeru.
    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    # Otpravlyaem zapros na start poliva i poluchaem otvet.
    with TestClient(app) as client:
        _create_tables()
        user_id = _create_user("owner@example.com", "secret")
        _insert_device("abc123", user_id=user_id)
        token = create_access_token({"user_id": user_id})
        headers = {"Authorization": f"Bearer {token}"}
        response = client.post(
            "/api/manual-watering/start",
            json={"device_id": "abc123", "duration_s": 20},
            headers=headers,
        )

    app.dependency_overrides.clear()

    # Ubezhdaemsya chto otvet uspeshnyi i vydan correlation_id.
    assert response.status_code == 200
    data = response.json()
    correlation_id = data.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    # Proveryaem chto publikaciya rovno odna i s ozhidaemymi dannymi.
    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdPumpStart)
    assert cmd.duration_s == 20
    assert cmd.correlation_id == correlation_id
    assert cmd.type == CommandType.pump_start.value
