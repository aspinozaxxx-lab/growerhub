import sys
import types

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database_models import Base

engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def _create_tables() -> None:
    """Создаём таблицы в изолированной in-memory БД для тестов."""
    Base.metadata.create_all(bind=engine)


def _get_db():
    """Выдаём сессию SQLAlchemy и гарантированно закрываем её после использования."""
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

from api_manual_watering import get_mqtt_dep
from app.main import app
from service.mqtt.serialization import CmdPumpStart, CommandType
from service.mqtt.interfaces import IMqttPublisher


class FakePublisher(IMqttPublisher):
    """Тестовый паблишер, который просто накапливает опубликованные команды."""

    def __init__(self) -> None:
        self.published: list[tuple[str, CmdPumpStart]] = []

    def publish_cmd(self, device_id: str, cmd: CmdPumpStart) -> None:
        self.published.append((device_id, cmd))


def test_manual_watering_start_endpoint():
    """Проверяем, что эндпоинт запуска полива публикует pump.start и возвращает correlation_id."""

    # Подменяем зависимость на наш фейковый паблишер, чтобы не обращаться к реальному брокеру.
    fake = FakePublisher()
    app.dependency_overrides[get_mqtt_dep] = lambda: fake

    # Отправляем запрос на запуск полива и сохраняем ответ.
    with TestClient(app) as client:
        response = client.post(
            "/api/manual-watering/start",
            json={"device_id": "abc123", "duration_s": 20},
        )

    app.dependency_overrides.clear()

    # Проверяем, что сервер вернул успешный статус и выдал корреляционный идентификатор.
    assert response.status_code == 200
    data = response.json()
    correlation_id = data.get("correlation_id")
    assert isinstance(correlation_id, str) and correlation_id

    # Убеждаемся, что публикация ровно одна и в неё попал ожидаемый набор данных.
    assert len(fake.published) == 1
    device_id, cmd = fake.published[0]
    assert device_id == "abc123"
    assert isinstance(cmd, CmdPumpStart)
    assert cmd.duration_s == 20
    assert cmd.correlation_id == correlation_id
    assert cmd.type == CommandType.pump_start.value
