import sys
import types

from fastapi import HTTPException
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Sozdaet sqlite shemu v pamyati dlya testov reboot."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Generator s sessiyei SQLAlchemy dlya testov."""

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

from app.fastapi.routers.manual_watering import get_mqtt_dep  # noqa: E402
from app.main import app  # noqa: E402
from app.mqtt.interfaces import IMqttPublisher  # noqa: E402
from app.mqtt.serialization import CmdReboot, CommandType  # noqa: E402


class FakePublisher(IMqttPublisher):
    """Feikovyy MQTT publisher dlya otdachi komandy reboot."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdReboot]] = []

    def publish_cmd(self, device_id: str, cmd: CmdReboot) -> None:
        """Zapominaet komandу reboot vmesto realnoy otpravki."""

        self.published.append((device_id, cmd))


def test_manual_watering_reboot_endpoint() -> None:
    """Proveryaet uspeshnuyu publikaciyu komandy reboot."""

    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    with TestClient(app) as client:
        response = client.post(
            "/api/manual-watering/reboot",
            json={"device_id": "abc123"},
        )

    app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    correlation_id = payload.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id
    assert payload.get("message") == "reboot command published"

    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdReboot)
    assert cmd.type == CommandType.reboot.value
    assert cmd.correlation_id == correlation_id
    assert cmd.issued_at > 0


def test_manual_watering_reboot_returns_503_when_publisher_unavailable() -> None:
    """Proveryaet vozvrat 503 pri nedostupnom MQTT publisher."""

    def _raise_unavailable():
        raise HTTPException(status_code=503, detail="MQTT publisher unavailable")

    app.dependency_overrides[get_mqtt_dep] = _raise_unavailable

    with TestClient(app) as client:
        response = client.post(
            "/api/manual-watering/reboot",
            json={"device_id": "abc123"},
        )

    app.dependency_overrides.clear()

    assert response.status_code == 503
    assert response.json()["detail"] == "MQTT publisher unavailable"


def test_manual_watering_reboot_validates_device_id() -> None:
    """Proveryaet chto pustoj device_id vyzyvaet 422."""

    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    with TestClient(app) as client:
        response = client.post(
            "/api/manual-watering/reboot",
            json={"device_id": ""},
        )

    app.dependency_overrides.clear()

    assert response.status_code == 422
