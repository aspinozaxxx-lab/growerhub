import os
from contextlib import ExitStack, contextmanager

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from unittest.mock import patch

import app.main
from app.core.database import get_db
from app.fastapi.routers import manual_watering as manual_watering_router
from app.models.database_models import Base, DeviceDB, UserDB
from app.repositories.users import create_local_user


TEST_DATABASE_URL = "sqlite+pysqlite:///:memory:"

test_engine = create_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
    future=True,
)

TestingSessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=test_engine,
)


def _test_db_create_schema() -> None:
    Base.metadata.create_all(bind=test_engine)


def _test_db_drop_schema() -> None:
    Base.metadata.drop_all(bind=test_engine)


def _override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


class DummyPublisher:
    def publish_cmd(self, device_id, cmd):
        return None


class DummyShadowStore:
    def get_manual_watering_view(self, device_id):
        return None


class DummyAckStore:
    def get(self, correlation_id):
        return None


@contextmanager
def _patched_client() -> TestClient:
    stack = ExitStack()

    async def _async_noop(*args, **kwargs):
        return None

    stack.enter_context(patch("app.main.init_publisher", lambda: None))
    stack.enter_context(patch("app.main.init_state_subscriber", lambda *args, **kwargs: None))
    stack.enter_context(patch("app.main.start_state_subscriber", lambda: None))
    stack.enter_context(patch("app.main.init_ack_subscriber", lambda *args, **kwargs: None))
    stack.enter_context(patch("app.main.start_ack_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_state_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_ack_subscriber", lambda: None))
    stack.enter_context(patch("app.main.shutdown_publisher", lambda: None))
    stack.enter_context(patch("app.main.start_ack_cleanup_loop", _async_noop))
    stack.enter_context(patch("app.main.stop_ack_cleanup_loop", _async_noop))

    try:
        with TestClient(app.main.app) as client:
            yield client
    finally:
        stack.close()


@pytest.fixture
def client():
    _test_db_drop_schema()
    _test_db_create_schema()
    app.main.app.dependency_overrides[get_db] = _override_get_db
    import app.core.security as security_module  # noqa: WPS433

    app.main.app.dependency_overrides[security_module.get_db] = _override_get_db
    app.main.app.dependency_overrides[manual_watering_router.get_mqtt_dep] = lambda: DummyPublisher()
    app.main.app.dependency_overrides[manual_watering_router.get_shadow_dep] = lambda: DummyShadowStore()
    app.main.app.dependency_overrides[manual_watering_router.get_ack_dep] = lambda: DummyAckStore()
    from app.repositories import state_repo

    state_repo.SessionLocal = TestingSessionLocal

    try:
        with _patched_client() as client:
            yield client
    finally:
        app.main.app.dependency_overrides.pop(get_db, None)
        app.main.app.dependency_overrides.pop(security_module.get_db, None)
        app.main.app.dependency_overrides.pop(manual_watering_router.get_mqtt_dep, None)
        app.main.app.dependency_overrides.pop(manual_watering_router.get_shadow_dep, None)
        app.main.app.dependency_overrides.pop(manual_watering_router.get_ack_dep, None)
        _test_db_drop_schema()


def _create_user(email: str, password: str, role: str = "user", is_active: bool = True) -> UserDB:
    session = TestingSessionLocal()
    try:
        user = create_local_user(session, email, None, role, password)
        user.is_active = is_active
        session.add(user)
        session.commit()
        session.refresh(user)
        return user
    finally:
        session.close()


def _create_device(device_id: str, user_id: int | None = None) -> DeviceDB:
    session = TestingSessionLocal()
    try:
        device = DeviceDB(device_id=device_id, name=f"Device {device_id}", user_id=user_id)
        session.add(device)
        session.commit()
        session.refresh(device)
        return device
    finally:
        session.close()


def _login(client: TestClient, email: str, password: str) -> str:
    response = client.post(
        "/api/auth/login",
        json={"email": email, "password": password},
    )
    assert response.status_code == 200, response.text
    token = response.json().get("access_token")
    assert token
    return token


def _auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.skipif(os.getenv("GITHUB_ACTIONS") == "true" or os.getenv("CI") == "true", reason="Skipped in CI")
def test_assign_and_unassign_device_flow(client: TestClient):
    user1 = _create_user("user1@example.com", "secret1")
    user2 = _create_user("user2@example.com", "secret2")
    admin = _create_user("admin@example.com", "adminpass", role="admin")
    device = _create_device("dev-1")

    token1 = _login(client, "user1@example.com", "secret1")
    token2 = _login(client, "user2@example.com", "secret2")
    admin_token = _login(client, "admin@example.com", "adminpass")

    resp = client.post("/api/devices/assign-to-me", headers=_auth_headers(token1), json={"device_id": device.id})
    assert resp.status_code == 200
    data = resp.json()
    assert data["user_id"] == user1.id

    resp_conflict = client.post("/api/devices/assign-to-me", headers=_auth_headers(token2), json={"device_id": device.id})
    assert resp_conflict.status_code == 400

    resp_unassign = client.post(f"/api/devices/{device.id}/unassign", headers=_auth_headers(token1))
    assert resp_unassign.status_code == 200
    assert resp_unassign.json()["user_id"] is None

    resp_admin_assign = client.post(
        f"/api/admin/devices/{device.id}/assign",
        headers=_auth_headers(admin_token),
        json={"user_id": user2.id},
    )
    assert resp_admin_assign.status_code == 200
    assert resp_admin_assign.json()["owner"]["id"] == user2.id

    resp_admin_unassign = client.post(
        f"/api/admin/devices/{device.id}/unassign",
        headers=_auth_headers(admin_token),
    )
    assert resp_admin_unassign.status_code == 200
    assert resp_admin_unassign.json()["owner"] is None


@pytest.mark.skipif(os.getenv("GITHUB_ACTIONS") == "true" or os.getenv("CI") == "true", reason="Skipped in CI")
def test_devices_my_filtering(client: TestClient):
    user1 = _create_user("alice@example.com", "secret1")
    user2 = _create_user("bob@example.com", "secret2")
    dev1 = _create_device("dev-a", user_id=user1.id)
    _create_device("dev-b", user_id=user2.id)

    token1 = _login(client, "alice@example.com", "secret1")

    resp = client.get("/api/devices/my", headers=_auth_headers(token1))
    assert resp.status_code == 200
    payload = resp.json()
    assert len(payload) == 1
    assert payload[0]["device_id"] == dev1.device_id


@pytest.mark.skipif(os.getenv("GITHUB_ACTIONS") == "true" or os.getenv("CI") == "true", reason="Skipped in CI")
def test_delete_user_keeps_devices(client: TestClient):
    admin = _create_user("admin2@example.com", "adminpass", role="admin")
    user = _create_user("delete_me@example.com", "secret")
    device = _create_device("dev-z", user_id=user.id)

    admin_token = _login(client, "admin2@example.com", "adminpass")

    resp = client.delete(f"/api/users/{user.id}", headers=_auth_headers(admin_token))
    assert resp.status_code == 204

    session = TestingSessionLocal()
    try:
        db_device = session.query(DeviceDB).filter(DeviceDB.id == device.id).first()
        assert db_device is not None
        assert db_device.user_id is None
    finally:
        session.close()


@pytest.mark.skipif(os.getenv("GITHUB_ACTIONS") == "true" or os.getenv("CI") == "true", reason="Skipped in CI")
def test_change_password_flow(client: TestClient):
    user = _create_user("pass@example.com", "oldpass")
    token = _login(client, "pass@example.com", "oldpass")

    bad_resp = client.post(
        "/api/auth/change-password",
        headers=_auth_headers(token),
        json={"current_password": "wrong", "new_password": "newpass"},
    )
    assert bad_resp.status_code == 400

    ok_resp = client.post(
        "/api/auth/change-password",
        headers=_auth_headers(token),
        json={"current_password": "oldpass", "new_password": "newpass"},
    )
    assert ok_resp.status_code == 200

    new_token = _login(client, "pass@example.com", "newpass")
    assert new_token


@pytest.mark.skipif(os.getenv("GITHUB_ACTIONS") == "true" or os.getenv("CI") == "true", reason="Skipped in CI")
def test_manual_watering_access_control(client: TestClient):
    owner = _create_user("owner@example.com", "secret")
    stranger = _create_user("stranger@example.com", "secret")
    admin = _create_user("boss@example.com", "adminpass", role="admin")
    device = _create_device("dev-water", user_id=owner.id)

    owner_token = _login(client, "owner@example.com", "secret")
    stranger_token = _login(client, "stranger@example.com", "secret")
    admin_token = _login(client, "boss@example.com", "adminpass")

    ok_resp = client.get(
        f"/api/manual-watering/status?device_id={device.device_id}",
        headers=_auth_headers(owner_token),
    )
    assert ok_resp.status_code == 200

    forbidden_resp = client.get(
        f"/api/manual-watering/status?device_id={device.device_id}",
        headers=_auth_headers(stranger_token),
    )
    assert forbidden_resp.status_code == 403

    admin_resp = client.get(
        f"/api/manual-watering/status?device_id={device.device_id}",
        headers=_auth_headers(admin_token),
    )
    assert admin_resp.status_code == 200
