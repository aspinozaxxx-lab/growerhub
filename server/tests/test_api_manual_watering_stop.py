import sys
import types
from datetime import datetime

import config
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base

# --- ╨Э╨░╤Б╤В╤А╨░╨╕╨▓╨░╨╡╨╝ ╨╕╨╖╨╛╨╗╨╕╤А╨╛╨▓╨░╨╜╨╜╤Г╤О ╨С╨Ф, ╤З╤В╨╛╨▒╤Л ╨╜╨╡ ╨╖╨░╨▓╨╕╤Б╨╡╤В╤М ╨╛╤В ╤А╨╡╨░╨╗╤М╨╜╨╛╨│╨╛ ╤Б╨╡╤А╨▓╨╡╤А╨░. ---
engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
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


# --- ╨Я╨╛╨┤╨╝╨╡╨╜╤П╨╡╨╝ ╨╝╨╛╨┤╤Г╨╗╤М app.core.database ╨╜╨░ ╨╖╨░╨│╨╗╤Г╤И╨║╤Г ╤Б ╨╜╨░╤И╨╡╨╣ in-memory ╨С╨Ф. ---
stub_database = types.ModuleType("app.core.database")
stub_database.engine = engine
stub_database.SessionLocal = SessionLocal
stub_database.create_tables = _create_tables
stub_database.get_db = _get_db
sys.modules["app.core.database"] = stub_database

from app.fastapi.routers.manual_watering import get_mqtt_dep  # noqa: E402  # ╨╕╨╝╨┐╨╛╤А╤В ╨┐╨╛╤Б╨╗╨╡ ╨┐╨╛╨┤╨╝╨╡╨╜╤Л ╨С╨Ф
from app.main import app  # noqa: E402
from app.mqtt.serialization import CmdPumpStop, CommandType  # noqa: E402
from app.mqtt.interfaces import IMqttPublisher  # noqa: E402


class FakePublisher(IMqttPublisher):
    """╨д╨╡╨╣╨║╨╛╨▓╤Л╨╣ MQTT-╨┐╨░╨▒╨╗╨╕╤И╨╡╤А: ╨▓╨╝╨╡╤Б╤В╨╛ ╤А╨╡╨░╨╗╤М╨╜╨╛╨│╨╛ ╨▒╤А╨╛╨║╨╡╤А╨░ ╨╖╨░╨┐╨╛╨╝╨╕╨╜╨░╨╡╤В ╨║╨╛╨╝╨░╨╜╨┤╤Л."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStop]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStop) -> None:
        """Sohranyaet komandу stop dlya proverki v teste."""

        self.published.append((device_id, cmd))


def test_manual_watering_stop_endpoint():
    """Proveryaet chto endpoint stop vozvrashaet correlation_id i publikuet pump.stop."""

    # ╨Я╨╛╨┤╨╝╨╡╨╜╤П╨╡╨╝ ╨╖╨░╨▓╨╕╤Б╨╕╨╝╨╛╤Б╤В╤М FastAPI ╨╜╨░ ╤Д╨╡╨╣╨║╨╛╨▓╤Л╨╣ ╨┐╨░╨▒╨╗╨╕╤И╨╡╤А: ╨╛╨╜ ╨╜╨╡ ╤Е╨╛╨┤╨╕╤В ╨▓ ╤Б╨╡╤В╤М.
    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    # ╨Ю╤В╨┐╤А╨░╨▓╨╗╤П╨╡╨╝ POST-╨╖╨░╨┐╤А╨╛╤Б ╨╜╨░ ╨╛╤Б╤В╨░╨╜╨╛╨▓╨║╤Г ╨┐╨╛╨╗╨╕╨▓╨░.
    with TestClient(app) as client:
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
        )

    # ╨Т╨╛╨╖╨▓╤А╨░╤Й╨░╨╡╨╝ ╨╖╨░╨▓╨╕╤Б╨╕╨╝╨╛╤Б╤В╨╕ ╨▓ ╨╕╤Б╤Е╨╛╨┤╨╜╨╛╨╡ ╤Б╨╛╤Б╤В╨╛╤П╨╜╨╕╨╡, ╤З╤В╨╛╨▒╤Л ╨╜╨╡ ╨▓╨╗╨╕╤П╤В╤М ╨╜╨░ ╨┤╤А╤Г╨│╨╕╨╡ ╤В╨╡╤Б╤В╤Л.
    app.dependency_overrides.clear()

    # ╨Я╤А╨╛╨▓╨╡╤А╤П╨╡╨╝ ╤Г╤Б╨┐╨╡╤И╨╜╤Л╨╣ ╤Б╤В╨░╤В╤Г╤Б ╨╕ ╨╜╨░╨╗╨╕╤З╨╕╨╡ ╨║╨╛╤А╤А╨╡╨╗╤П╤Ж╨╕╨╛╨╜╨╜╨╛╨│╨╛ ╨╕╨┤╨╡╨╜╤В╨╕╤Д╨╕╨║╨░╤В╨╛╤А╨░.
    assert response.status_code == 200
    data = response.json()
    correlation_id = data.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    # ╨Я╤А╨╛╨▓╨╡╤А╤П╨╡╨╝, ╤З╤В╨╛ ╨╛╨┐╤Г╨▒╨╗╨╕╨║╨╛╨▓╨░╨╜╨░ ╤А╨╛╨▓╨╜╨╛ ╨╛╨┤╨╜╨░ ╨║╨╛╨╝╨░╨╜╨┤╨░ ╨╕ ╤Н╤В╨╛ ╨┤╨╡╨╣╤Б╤В╨▓╨╕╤В╨╡╨╗╤М╨╜╨╛ pump.stop.
    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdPumpStop)
    assert cmd.type == CommandType.pump_stop.value
    assert cmd.correlation_id == correlation_id
    assert cmd.ts is not None
