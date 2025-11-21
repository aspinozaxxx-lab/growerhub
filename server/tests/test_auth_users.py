from __future__ import annotations

from contextlib import ExitStack, contextmanager
from typing import Iterator
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.main
from app.core.database import get_db
from app.core.security import create_access_token
from app.models.database_models import Base, UserDB
from app.repositories.users import create_local_user


engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    expire_on_commit=False,
    bind=engine,
)


def _reset_db() -> None:
    """Translitem: polnostyu peresoedinyaet shemu v testovoj SQLite v pamyati."""

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)


def _get_db():
    """Translitem: zavisimost get_db, vozvrashchaet sessiyu s nashim engine."""

    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


@contextmanager
def _patched_client() -> Iterator[TestClient]:
    """Translitem: TestClient s zaglushkami MQTT/startup chtoby izbezhat soedinenij."""

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


@pytest.fixture(autouse=True)
def setup_db():
    """Translitem: sbrasivaet bazu i podmenyaet get_db dlya kajdogo testa."""

    _reset_db()
    app.main.app.dependency_overrides[get_db] = _get_db
    try:
        yield
    finally:
        app.main.app.dependency_overrides.pop(get_db, None)
        _reset_db()


@pytest.fixture
def client() -> Iterator[TestClient]:
    """Translitem: vozvrashaet TestClient s otklyuchennym MQTT startom."""

    with _patched_client() as client:
        yield client


def _create_user(email: str, password: str, role: str = "user", is_active: bool = True) -> UserDB:
    """Translitem: helper dlya sozdaniya polzovatelya cherez repo."""

    session = TestingSessionLocal()
    try:
        user = create_local_user(session, email, None, role, password)
        if not is_active:
            user.is_active = False
            session.commit()
            session.refresh(user)
        return user
    finally:
        session.close()


def _auth_headers(token: str) -> dict[str, str]:
    """Translitem: sborka zagolovka Authorization Bearer."""

    return {"Authorization": f"Bearer {token}"}


def _login_and_get_token(client: TestClient, email: str, password: str) -> str:
    """Translitem: login helper, vozvrashaet access_token iz otveta."""

    response = client.post(
        "/api/auth/login",
        json={"email": email, "password": password},
    )
    assert response.status_code == 200, response.text
    payload = response.json()
    token = payload.get("access_token")
    assert isinstance(token, str) and token
    return token


# --- A. /api/auth/login ---


def test_login_returns_bearer_token(client: TestClient):
    """Proveryaet uspeshnyj login i vydachu bearer JWT."""

    _create_user("admin@example.com", "secret", role="admin")

    response = client.post(
        "/api/auth/login",
        json={"email": "admin@example.com", "password": "secret"},
    )

    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data and data["access_token"]
    assert data.get("token_type") == "bearer"


def test_login_wrong_password_returns_401(client: TestClient):
    """Proveryaet 401 pri nevernom parole."""

    _create_user("admin2@example.com", "right-pass", role="admin")

    response = client.post(
        "/api/auth/login",
        json={"email": "admin2@example.com", "password": "wrong-pass"},
    )

    assert response.status_code == 401


def test_login_inactive_user_returns_401(client: TestClient):
    """Proveryaet otkaz logina dlya neaktivnogo polzovatelya."""

    _create_user("inactive@example.com", "secret", role="admin", is_active=False)

    response = client.post(
        "/api/auth/login",
        json={"email": "inactive@example.com", "password": "secret"},
    )

    assert response.status_code == 401


# --- B. /api/auth/me ---


def test_auth_me_returns_current_user(client: TestClient):
    """Proveryaet vozvrat dannyh tekushchego polzovatelya."""

    _create_user("admin3@example.com", "secret", role="admin")
    token = _login_and_get_token(client, "admin3@example.com", "secret")

    response = client.get("/api/auth/me", headers=_auth_headers(token))

    assert response.status_code == 200
    payload = response.json()
    assert payload["email"] == "admin3@example.com"
    assert payload["role"] == "admin"
    assert payload["is_active"] is True


def test_auth_me_without_token_returns_401(client: TestClient):
    """Proveryaet 401 esli zagolovok Authorization otsutstvuet."""

    response = client.get("/api/auth/me")

    assert response.status_code == 401


def test_auth_me_inactive_user_returns_403(client: TestClient):
    """Proveryaet 403 esli u polzovatelya is_active=False."""

    user = _create_user("inactive-me@example.com", "secret", role="admin", is_active=False)
    token = create_access_token({"user_id": user.id})

    response = client.get("/api/auth/me", headers=_auth_headers(token))

    assert response.status_code == 403


# --- D. /api/users CRUD (admin only) ---


def test_admin_can_list_users(client: TestClient):
    """Admin poluchaet spisok polzovateley i vidit sebya."""

    _create_user("admin-list@example.com", "secret", role="admin")
    token = _login_and_get_token(client, "admin-list@example.com", "secret")

    response = client.get("/api/users", headers=_auth_headers(token))

    assert response.status_code == 200
    users = response.json()
    emails = [item["email"] for item in users]
    assert "admin-list@example.com" in emails


def test_admin_can_create_user(client: TestClient):
    """Admin sozdaet novogo polzovatelya."""

    _create_user("admin-create@example.com", "secret", role="admin")
    token = _login_and_get_token(client, "admin-create@example.com", "secret")

    response = client.post(
        "/api/users",
        headers=_auth_headers(token),
        json={
            "email": "new-user@example.com",
            "username": "newbie",
            "role": "user",
            "password": "pass123",
        },
    )

    assert response.status_code == 201
    payload = response.json()
    assert payload["email"] == "new-user@example.com"
    assert payload["role"] == "user"
    assert payload["username"] == "newbie"
    assert payload["is_active"] is True


def test_admin_get_user_by_id_and_not_found(client: TestClient):
    """Admin mozhet poluchit polzovatelya po id ili poluchit 404."""

    _create_user("admin-get@example.com", "secret", role="admin")
    token = _login_and_get_token(client, "admin-get@example.com", "secret")

    create_resp = client.post(
        "/api/users",
        headers=_auth_headers(token),
        json={
            "email": "target@example.com",
            "username": "target",
            "role": "user",
            "password": "pass123",
        },
    )
    assert create_resp.status_code == 201
    user_id = create_resp.json()["id"]

    response = client.get(f"/api/users/{user_id}", headers=_auth_headers(token))
    assert response.status_code == 200
    assert response.json()["email"] == "target@example.com"

    missing = client.get("/api/users/9999", headers=_auth_headers(token))
    assert missing.status_code == 404


def test_admin_can_patch_user(client: TestClient):
    """Admin chastichno obnovlyaet polya polzovatelya."""

    _create_user("admin-patch@example.com", "secret", role="admin")
    token = _login_and_get_token(client, "admin-patch@example.com", "secret")

    create_resp = client.post(
        "/api/users",
        headers=_auth_headers(token),
        json={
            "email": "patchme@example.com",
            "username": "old-name",
            "role": "user",
            "password": "pass123",
        },
    )
    assert create_resp.status_code == 201
    user_id = create_resp.json()["id"]

    response = client.patch(
        f"/api/users/{user_id}",
        headers=_auth_headers(token),
        json={"username": "new-name", "role": "admin", "is_active": False},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["username"] == "new-name"
    assert payload["role"] == "admin"
    assert payload["is_active"] is False


def test_admin_can_delete_user(client: TestClient):
    """Admin udalyaet polzovatelya i ego identity."""

    _create_user("admin-delete@example.com", "secret", role="admin")
    token = _login_and_get_token(client, "admin-delete@example.com", "secret")

    create_resp = client.post(
        "/api/users",
        headers=_auth_headers(token),
        json={
            "email": "deleteme@example.com",
            "username": "victim",
            "role": "user",
            "password": "pass123",
        },
    )
    assert create_resp.status_code == 201
    user_id = create_resp.json()["id"]

    delete_resp = client.delete(f"/api/users/{user_id}", headers=_auth_headers(token))
    assert delete_resp.status_code == 204

    get_resp = client.get(f"/api/users/{user_id}", headers=_auth_headers(token))
    assert get_resp.status_code == 404


# --- E. Dostup ---


def test_users_endpoints_require_auth(client: TestClient):
    """Bez tokena zaprosy k /api/users vozvrashchayut 401."""

    response = client.get("/api/users")
    assert response.status_code == 401


def test_non_admin_forbidden_for_users_api(client: TestClient):
    """Polzovatel' s role=user poluchaet 403 na adminskie endpointy."""

    _create_user("regular@example.com", "secret", role="user")
    token = _login_and_get_token(client, "regular@example.com", "secret")

    response = client.get("/api/users", headers=_auth_headers(token))
    assert response.status_code == 403

    create_resp = client.post(
        "/api/users",
        headers=_auth_headers(token),
        json={
            "email": "should-not-create@example.com",
            "username": "forbidden",
            "role": "user",
            "password": "pass123",
        },
    )
    assert create_resp.status_code == 403
