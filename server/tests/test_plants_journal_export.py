import pytest
from datetime import datetime
from fastapi.testclient import TestClient
from unittest.mock import patch

import app.main
from app.core.database import get_db
from app.models.database_models import (
    Base,
    PlantJournalEntryDB,
    PlantJournalWateringDetailsDB,
)
from tests.test_auth_users import (
    TestingSessionLocal,
    _create_user,
    _login_and_get_token,
    _override_get_db,
    _patched_client,
    _test_db_create_schema,
    _test_db_drop_schema,
)


@pytest.fixture
def client() -> TestClient:
    _test_db_drop_schema()
    _test_db_create_schema()
    import app.core.security as security_module  # noqa: WPS433

    app.main.app.dependency_overrides[get_db] = _override_get_db
    app.main.app.dependency_overrides[security_module.get_db] = _override_get_db
    state_patcher = patch(
        "app.repositories.state_repo.DeviceStateLastRepository.get_state",
        return_value=None,
    )
    state_patcher.start()
    try:
        with _patched_client() as patched:
            yield patched
    finally:
        app.main.app.dependency_overrides.pop(get_db, None)
        app.main.app.dependency_overrides.pop(security_module.get_db, None)
        _test_db_drop_schema()
        state_patcher.stop()


def _insert_entry(plant_id: int, user_id: int, type_: str, text: str, event_at: datetime) -> int:
    """Translitem: sozdaet zapis' zhurnala s optional detalyami poliva."""

    session = TestingSessionLocal()
    try:
        entry = PlantJournalEntryDB(
            plant_id=plant_id,
            user_id=user_id,
            type=type_,
            text=text,
            event_at=event_at,
        )
        session.add(entry)
        session.flush()
        if type_ == "watering":
            session.add(
                PlantJournalWateringDetailsDB(
                    journal_entry_id=entry.id,
                    water_volume_l=1.5,
                    duration_s=120,
                    fertilizers_per_liter="G8 M12 B16",
                )
            )
        session.commit()
        return entry.id
    finally:
        session.close()


def test_export_journal_md_success(client: TestClient):
    owner = _create_user("export-md@example.com", "secret")
    token = _login_and_get_token(client, owner.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}

    plant_resp = client.post(
        "/api/plants",
        json={"name": "ExportPlant", "planted_at": "2025-01-01T00:00:00Z"},
        headers=headers,
    )
    assert plant_resp.status_code == 200
    plant_id = plant_resp.json()["id"]

    _insert_entry(plant_id, owner.id, "watering", "", datetime(2025, 1, 2, 8, 30))
    _insert_entry(plant_id, owner.id, "feeding", "podrezka listev", datetime(2025, 1, 2, 12, 0))
    _insert_entry(plant_id, owner.id, "note", "pozhelteli verhnye listya", datetime(2025, 1, 3, 9, 15))
    _insert_entry(plant_id, owner.id, "photo", "", datetime(2025, 1, 3, 18, 0))

    resp = client.get(f"/api/plants/{plant_id}/journal/export?format=md", headers=headers)
    assert resp.status_code == 200
    assert "attachment" in resp.headers.get("content-disposition", "").lower()
    assert resp.headers.get("content-type", "").startswith("text/markdown")

    body = resp.text
    assert "Название: ExportPlant" in body
    assert "Дата посадки: 2025-01-01" in body
    assert "Текущий возраст:" in body
    assert "## 2025-01-02" in body
    assert "## 2025-01-03" in body
    assert "💧 Полив" in body
    assert "1,5 л" in body
    assert "удобрен" in body.lower() or "udobreniya" in body
    assert "🧹 Уход: podrezka listev" in body
    assert "👁 Наблюдение: pozhelteli verhnye listya" in body
    assert "📷 Фото" in body
    assert "![](" not in body
    assert "http" not in body


def test_export_journal_md_auth_required(client: TestClient):
    user = _create_user("export-auth@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}
    plant_id = client.post("/api/plants", json={"name": "AuthPlant"}, headers=headers).json()["id"]

    unauth = client.get(f"/api/plants/{plant_id}/journal/export?format=md")
    assert unauth.status_code == 401

    other = _create_user("export-other@example.com", "secret")
    other_token = _login_and_get_token(client, other.email, "secret")
    other_headers = {"Authorization": f"Bearer {other_token}"}
    forbidden = client.get(f"/api/plants/{plant_id}/journal/export?format=md", headers=other_headers)
    assert forbidden.status_code in (403, 404)


def test_export_journal_invalid_format(client: TestClient):
    user = _create_user("export-format@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}
    plant_id = client.post("/api/plants", json={"name": "FmtPlant"}, headers=headers).json()["id"]

    resp = client.get(f"/api/plants/{plant_id}/journal/export?format=txt", headers=headers)
    assert resp.status_code == 400
