import sys
import types

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def _create_tables() -> None:
    """╨б╨╛╨╖╨┤╨░╤С╨╝ ╤В╨░╨▒╨╗╨╕╤Ж╤Л ╨▓ ╨╕╨╖╨╛╨╗╨╕╤А╨╛╨▓╨░╨╜╨╜╨╛╨╣ in-memory ╨С╨Ф ╨┤╨╗╤П ╤В╨╡╤Б╤В╨╛╨▓."""
    Base.metadata.create_all(bind=engine)


def _get_db():
    """╨Т╤Л╨┤╨░╤С╨╝ ╤Б╨╡╤Б╤Б╨╕╤О SQLAlchemy ╨╕ ╨│╨░╤А╨░╨╜╤В╨╕╤А╨╛╨▓╨░╨╜╨╜╨╛ ╨╖╨░╨║╤А╤Л╨▓╨░╨╡╨╝ ╨╡╤С ╨┐╨╛╤Б╨╗╨╡ ╨╕╤Б╨┐╨╛╨╗╤М╨╖╨╛╨▓╨░╨╜╨╕╤П."""
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

from app.api.routers.manual_watering import get_mqtt_dep
from app.main import app
from service.mqtt.serialization import CmdPumpStart, CommandType
from service.mqtt.interfaces import IMqttPublisher


class FakePublisher(IMqttPublisher):
    """╨в╨╡╤Б╤В╨╛╨▓╤Л╨╣ ╨┐╨░╨▒╨╗╨╕╤И╨╡╤А, ╨║╨╛╤В╨╛╤А╤Л╨╣ ╨┐╤А╨╛╤Б╤В╨╛ ╨╜╨░╨║╨░╨┐╨╗╨╕╨▓╨░╨╡╤В ╨╛╨┐╤Г╨▒╨╗╨╕╨║╨╛╨▓╨░╨╜╨╜╤Л╨╡ ╨║╨╛╨╝╨░╨╜╨┤╤Л."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStart]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStart) -> None:
        self.published.append((device_id, cmd))


def test_manual_watering_start_endpoint():
    """╨Я╤А╨╛╨▓╨╡╤А╤П╨╡╨╝, ╤З╤В╨╛ ╤Н╨╜╨┤╨┐╨╛╨╕╨╜╤В ╨╖╨░╨┐╤Г╤Б╨║╨░ ╨┐╨╛╨╗╨╕╨▓╨░ ╨┐╤Г╨▒╨╗╨╕╨║╤Г╨╡╤В pump.start ╨╕ ╨▓╨╛╨╖╨▓╤А╨░╤Й╨░╨╡╤В correlation_id."""

    # ╨Я╨╛╨┤╨╝╨╡╨╜╤П╨╡╨╝ ╨╖╨░╨▓╨╕╤Б╨╕╨╝╨╛╤Б╤В╤М ╨╜╨░ ╨╜╨░╤И ╤Д╨╡╨╣╨║╨╛╨▓╤Л╨╣ ╨┐╨░╨▒╨╗╨╕╤И╨╡╤А, ╤З╤В╨╛╨▒╤Л ╨╜╨╡ ╨╛╨▒╤А╨░╤Й╨░╤В╤М╤Б╤П ╨║ ╤А╨╡╨░╨╗╤М╨╜╨╛╨╝╤Г ╨▒╤А╨╛╨║╨╡╤А╤Г.
    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    # ╨Ю╤В╨┐╤А╨░╨▓╨╗╤П╨╡╨╝ ╨╖╨░╨┐╤А╨╛╤Б ╨╜╨░ ╨╖╨░╨┐╤Г╤Б╨║ ╨┐╨╛╨╗╨╕╨▓╨░ ╨╕ ╤Б╨╛╤Е╤А╨░╨╜╤П╨╡╨╝ ╨╛╤В╨▓╨╡╤В.
    with TestClient(app) as client:
        response = client.post(
            "/api/manual-watering/start",
            json={"device_id": "abc123", "duration_s": 20},
        )

    app.dependency_overrides.clear()

    # ╨Я╤А╨╛╨▓╨╡╤А╤П╨╡╨╝, ╤З╤В╨╛ ╤Б╨╡╤А╨▓╨╡╤А ╨▓╨╡╤А╨╜╤Г╨╗ ╤Г╤Б╨┐╨╡╤И╨╜╤Л╨╣ ╤Б╤В╨░╤В╤Г╤Б ╨╕ ╨▓╤Л╨┤╨░╨╗ ╨║╨╛╤А╤А╨╡╨╗╤П╤Ж╨╕╨╛╨╜╨╜╤Л╨╣ ╨╕╨┤╨╡╨╜╤В╨╕╤Д╨╕╨║╨░╤В╨╛╤А.
    assert response.status_code == 200
    data = response.json()
    correlation_id = data.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    # ╨г╨▒╨╡╨╢╨┤╨░╨╡╨╝╤Б╤П, ╤З╤В╨╛ ╨┐╤Г╨▒╨╗╨╕╨║╨░╤Ж╨╕╤П ╤А╨╛╨▓╨╜╨╛ ╨╛╨┤╨╜╨░ ╨╕ ╨▓ ╨╜╨╡╤С ╨┐╨╛╨┐╨░╨╗ ╨╛╨╢╨╕╨┤╨░╨╡╨╝╤Л╨╣ ╨╜╨░╨▒╨╛╤А ╨┤╨░╨╜╨╜╤Л╤Е.
    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdPumpStart)
    assert cmd.duration_s == 20
    assert cmd.correlation_id == correlation_id
    assert cmd.type == CommandType.pump_start.value
