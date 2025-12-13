import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch
from datetime import datetime

import app.main
from app.core.database import get_db
from app.models.database_models import Base, DeviceDB, PlantDeviceDB, PlantJournalEntryDB, PlantJournalPhotoDB
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


def test_create_plant_sets_owner(client: TestClient):
    user = _create_user("plant-owner@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}

    response = client.post("/api/plants", json={"name": "Basil"}, headers=headers)

    assert response.status_code == 200
    payload = response.json()
    assert payload["user_id"] == user.id


def test_create_plant_persists_plant_fields(client: TestClient):
    """Translitem: POST /api/plants dolzhen sohranyat i vozvrashchat plant_type/strain/growth_stage."""

    user = _create_user("plant-fields-create@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}

    create_resp = client.post(
        "/api/plants",
        json={
            "name": "Rose",
            "plant_type": "flowering",
            "strain": "Mint",
            "growth_stage": "seedling",
        },
        headers=headers,
    )
    assert create_resp.status_code == 200
    created = create_resp.json()
    assert created["plant_type"] == "flowering"
    assert created["strain"] == "Mint"
    assert created["growth_stage"] == "seedling"

    list_resp = client.get("/api/plants", headers=headers)
    assert list_resp.status_code == 200
    plants = list_resp.json()
    found = next((p for p in plants if p.get("id") == created["id"]), None)
    assert found is not None
    assert found["plant_type"] == "flowering"
    assert found["strain"] == "Mint"
    assert found["growth_stage"] == "seedling"


def test_patch_plant_updates_plant_fields(client: TestClient):
    """Translitem: PATCH /api/plants/{id} dolzhen obnovlyat tol'ko peredannye polya plants."""

    user = _create_user("plant-fields-patch@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}

    create_resp = client.post(
        "/api/plants",
        json={
            "name": "Lemon",
            "plant_type": "flowering",
            "strain": "Old",
            "growth_stage": "vegetative",
        },
        headers=headers,
    )
    assert create_resp.status_code == 200
    plant_id = create_resp.json()["id"]

    patch_resp = client.patch(
        f"/api/plants/{plant_id}",
        json={"strain": "New", "growth_stage": "flowering"},
        headers=headers,
    )
    assert patch_resp.status_code == 200
    patched = patch_resp.json()
    assert patched["plant_type"] == "flowering"
    assert patched["strain"] == "New"
    assert patched["growth_stage"] == "flowering"

    get_resp = client.get(f"/api/plants/{plant_id}", headers=headers)
    assert get_resp.status_code == 200
    fetched = get_resp.json()
    assert fetched["plant_type"] == "flowering"
    assert fetched["strain"] == "New"
    assert fetched["growth_stage"] == "flowering"


def test_attach_device_to_plant(client: TestClient):
    user = _create_user("plant-device@example.com", "secret")
    session = TestingSessionLocal()
    try:
        device = DeviceDB(device_id="dev-plant", name="Device Plant", user_id=user.id)
        session.add(device)
        session.commit()
        session.refresh(device)
    finally:
        session.close()

    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}
    plant_resp = client.post("/api/plants", json={"name": "Rose"}, headers=headers)
    plant_id = plant_resp.json()["id"]

    link_resp = client.post(
        f"/api/plants/{plant_id}/devices/{device.id}",
        headers=headers,
    )
    assert link_resp.status_code == 200

    list_resp = client.get("/api/plants", headers=headers)
    assert list_resp.status_code == 200
    plants = list_resp.json()
    assert any(
        any(dev["id"] == device.id for dev in plant.get("devices", []))
        for plant in plants
    )

    session = TestingSessionLocal()
    try:
        link = session.query(PlantDeviceDB).filter(PlantDeviceDB.plant_id == plant_id).first()
        assert link is not None
    finally:
        session.close()


def test_create_journal_entry(client: TestClient):
    user = _create_user("plant-journal@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}
    plant_id = client.post("/api/plants", json={"name": "Mint"}, headers=headers).json()["id"]

    entry_resp = client.post(
        f"/api/plants/{plant_id}/journal",
        json={"type": "note", "text": "poliv 1l"},
        headers=headers,
    )
    assert entry_resp.status_code == 200

    list_resp = client.get(f"/api/plants/{plant_id}/journal", headers=headers)
    assert list_resp.status_code == 200
    entries = list_resp.json()
    assert any(entry.get("text") == "poliv 1l" for entry in entries)

    session = TestingSessionLocal()
    try:
        db_entry = (
            session.query(PlantJournalEntryDB)
            .filter(PlantJournalEntryDB.plant_id == plant_id)
            .order_by(PlantJournalEntryDB.id.desc())
            .first()
        )
        assert db_entry is not None
    finally:
        session.close()


def test_update_journal_entry(client: TestClient):
    user = _create_user("plant-journal-update@example.com", "secret")
    token = _login_and_get_token(client, user.email, "secret")
    headers = {"Authorization": f"Bearer {token}"}
    plant_id = client.post("/api/plants", json={"name": "Lemon"}, headers=headers).json()["id"]

    create_resp = client.post(
        f"/api/plants/{plant_id}/journal",
        json={"type": "note", "text": "old text"},
        headers=headers,
    )
    assert create_resp.status_code == 200
    entry_id = create_resp.json()["id"]

    patch_resp = client.patch(
        f"/api/plants/{plant_id}/journal/{entry_id}",
        json={"text": "new text", "type": "other"},
        headers=headers,
    )
    assert patch_resp.status_code == 200
    payload = patch_resp.json()
    assert payload["id"] == entry_id
    assert payload["text"] == "new text"
    assert payload["type"] == "other"

    session = TestingSessionLocal()
    try:
        db_entry = session.query(PlantJournalEntryDB).filter(PlantJournalEntryDB.id == entry_id).first()
        assert db_entry is not None
        assert db_entry.text == "new text"
        assert db_entry.type == "other"
    finally:
        session.close()


def test_journal_photo_download_requires_owner(client: TestClient):
    owner = _create_user("photo-owner@example.com", "secret")
    owner_token = _login_and_get_token(client, owner.email, "secret")
    owner_headers = {"Authorization": f"Bearer {owner_token}"}
    plant_id = client.post("/api/plants", json={"name": "PhotoPlant"}, headers=owner_headers).json()["id"]

    session = TestingSessionLocal()
    try:
        entry = PlantJournalEntryDB(
            plant_id=plant_id,
            user_id=owner.id,
            type="photo",
            text="with data",
            event_at=datetime.utcnow(),
        )
        session.add(entry)
        session.flush()
        photo = PlantJournalPhotoDB(
            journal_entry_id=entry.id,
            data=b"hello-world",
            content_type="text/plain",
            url=None,
        )
        session.add(photo)
        session.commit()
        photo_id = photo.id
    finally:
        session.close()

    resp_owner = client.get(f"/api/journal/photos/{photo_id}", headers=owner_headers)
    assert resp_owner.status_code == 200
    assert resp_owner.headers.get("content-type", "").startswith("text/plain")
    assert resp_owner.content == b"hello-world"

    resp_unauth = client.get(f"/api/journal/photos/{photo_id}")
    assert resp_unauth.status_code == 401

    other = _create_user("photo-other@example.com", "secret")
    other_token = _login_and_get_token(client, other.email, "secret")
    other_headers = {"Authorization": f"Bearer {other_token}"}
    resp_other = client.get(f"/api/journal/photos/{photo_id}", headers=other_headers)
    assert resp_other.status_code in (403, 404)
