import sys
import types

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base

# --- Настраиваем изолированную БД, чтобы не зависеть от реального сервера. ---
engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_tables() -> None:
    """Создаём схему БД (таблицы) в памяти перед запуском тестов."""

    Base.metadata.create_all(bind=engine)


def _get_db():
    """Выдаём сессию SQLAlchemy и закрываем её после использования."""

    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# --- Подменяем модуль app.core.database на заглушку с нашей in-memory БД. ---
stub_database = types.ModuleType("app.core.database")
stub_database.engine = engine
stub_database.SessionLocal = SessionLocal
stub_database.create_tables = _create_tables
stub_database.get_db = _get_db
sys.modules["app.core.database"] = stub_database

from api_manual_watering import get_mqtt_dep  # noqa: E402  # импорт после подмены БД
from app.main import app  # noqa: E402
from mqtt_protocol import CmdPumpStop, CommandType  # noqa: E402
from mqtt_publisher import IMqttPublisher  # noqa: E402


class FakePublisher(IMqttPublisher):
    """Фейковый MQTT-паблишер: вместо реального брокера запоминает команды."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStop]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStop) -> None:
        """Сохраняем команду в список, чтобы затем проверить содержимое."""

        self.published.append((device_id, cmd))


def test_manual_watering_stop_endpoint():
    """Проверяем, что эндпоинт остановки полива публикует pump.stop и отдаёт correlation_id."""

    # Подменяем зависимость FastAPI на фейковый паблишер: он не ходит в сеть.
    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    # Отправляем POST-запрос на остановку полива.
    with TestClient(app) as client:
        response = client.post(
            "/api/manual-watering/stop",
            json={"device_id": "abc123"},
        )

    # Возвращаем зависимости в исходное состояние, чтобы не влиять на другие тесты.
    app.dependency_overrides.clear()

    # Проверяем успешный статус и наличие корреляционного идентификатора.
    assert response.status_code == 200
    data = response.json()
    correlation_id = data.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    # Проверяем, что опубликована ровно одна команда и это действительно pump.stop.
    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdPumpStop)
    assert cmd.type == CommandType.pump_stop.value
    assert cmd.correlation_id == correlation_id
    assert cmd.ts is not None
