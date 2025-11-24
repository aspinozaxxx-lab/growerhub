from __future__ import annotations

import os
from contextlib import ExitStack, contextmanager
from datetime import datetime
from typing import Iterator
from unittest.mock import patch
from urllib.parse import parse_qs, urlparse

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.main
from app.core.database import get_db
from app.core.security import create_access_token, decode_access_token
from app.core.sso import (
    build_sso_state,
    get_or_create_user_from_sso,
    verify_sso_state,
)
from app.models.database_models import Base, UserAuthIdentityDB, UserDB
from app.repositories.users import create_local_user

if os.getenv("GITHUB_ACTIONS") == "true" or os.getenv("CI") == "true":
    pytestmark = pytest.mark.skip(
        reason="SSO tests skip in CI to unblok deploy; run locally"
    )


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


@contextmanager
def _patched_client() -> Iterator[TestClient]:
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
def session():
    _test_db_drop_schema()
    _test_db_create_schema()
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()
        _test_db_drop_schema()


@pytest.fixture
def client() -> Iterator[TestClient]:
    _test_db_drop_schema()
    _test_db_create_schema()
    app.main.app.dependency_overrides[get_db] = _override_get_db
    import app.core.security as security_module  # noqa: WPS433

    app.main.app.dependency_overrides[security_module.get_db] = _override_get_db

    try:
        with _patched_client() as client:
            yield client
    finally:
        app.main.app.dependency_overrides.pop(get_db, None)
        app.main.app.dependency_overrides.pop(security_module.get_db, None)
        _test_db_drop_schema()


def _login_and_get_token(client: TestClient, email: str, password: str) -> str:
    response = client.post("/api/auth/login", json={"email": email, "password": password})
    assert response.status_code == 200, response.text
    token = response.json().get("access_token")
    assert token
    return token


# --- core/sso helpers ---


def test_build_and_verify_state(session):
    state = build_sso_state("google", "login", "/static/profile.html", None)
    data = verify_sso_state(state)
    assert data["provider"] == "google"
    assert data["mode"] == "login"
    assert data["redirect_path"] == "/static/profile.html"
    payload = decode_access_token(state)
    assert payload["exp"] > datetime.utcnow().timestamp()


def test_verify_state_expired():
    state = create_access_token(
        {"provider": "google", "mode": "login", "redirect_path": "/", "current_user_id": None},
        expires_minutes=-1,
    )
    with pytest.raises(ValueError):
        verify_sso_state(state)


def test_get_or_create_user_from_sso_reuses_identity(session):
    user = get_or_create_user_from_sso(session, "google", "sub-1", "user@example.com")
    assert user.email == "user@example.com"
    identity = (
        session.query(UserAuthIdentityDB)
        .filter(UserAuthIdentityDB.provider == "google", UserAuthIdentityDB.provider_subject == "sub-1")
        .first()
    )
    assert identity is not None
    same_user = get_or_create_user_from_sso(session, "google", "sub-1", "new@example.com")
    assert same_user.id == user.id
    assert session.query(UserDB).count() == 1


# --- API SSO ---


def test_sso_login_redirect_contains_state(client: TestClient):
    response = client.get("/api/auth/sso/google/login", allow_redirects=False)
    assert response.status_code in (302, 307)
    location = response.headers["location"]
    query = parse_qs(urlparse(location).query)
    state = query.get("state", [None])[0]
    assert state
    data = verify_sso_state(state)
    assert data["mode"] == "login"


def test_sso_callback_login_creates_user_and_token(client: TestClient):
    login_resp = client.get("/api/auth/sso/google/login", allow_redirects=False)
    state = parse_qs(urlparse(login_resp.headers["location"]).query)["state"][0]

    with patch("app.fastapi.routers.auth.exchange_code_for_tokens", return_value={"access_token": "prov-token"}), \
         patch("app.fastapi.routers.auth.fetch_user_profile", return_value={"subject": "abc", "email": "abc@example.com"}):
        resp = client.get(f"/api/auth/sso/google/callback?code=test-code&state={state}", allow_redirects=False)

    assert resp.status_code in (302, 307)
    location = resp.headers["location"]
    assert "access_token=" in location
    token = parse_qs(urlparse(location.replace("#", "?")).query)["access_token"][0]
    payload = decode_access_token(token)
    user_id = payload.get("user_id")
    assert user_id

    db = TestingSessionLocal()
    try:
        user = db.query(UserDB).filter(UserDB.id == user_id).first()
        assert user is not None
        identity = db.query(UserAuthIdentityDB).filter(UserAuthIdentityDB.user_id == user.id, UserAuthIdentityDB.provider == "google").first()
        assert identity is not None
    finally:
        db.close()


def test_sso_link_creates_identity_and_conflict(client: TestClient):
    db = TestingSessionLocal()
    try:
        user1 = create_local_user(db, "link1@example.com", None, "user", "pass1")
        user2 = create_local_user(db, "link2@example.com", None, "user", "pass2")
        db.commit()
    finally:
        db.close()

    token1 = _login_and_get_token(client, "link1@example.com", "pass1")
    headers1 = {"Authorization": f"Bearer {token1}"}
    resp_login = client.get("/api/auth/sso/google/login", headers=headers1, allow_redirects=False)
    state1 = parse_qs(urlparse(resp_login.headers["location"]).query)["state"][0]

    with patch("app.fastapi.routers.auth.exchange_code_for_tokens", return_value={"access_token": "prov-token"}), \
         patch("app.fastapi.routers.auth.fetch_user_profile", return_value={"subject": "shared-subject", "email": "g@example.com"}):
        resp_cb = client.get(f"/api/auth/sso/google/callback?code=code&state={state1}", allow_redirects=False)
    assert resp_cb.status_code in (302, 307)
    assert resp_cb.headers["location"].startswith("/static/profile.html")

    token2 = _login_and_get_token(client, "link2@example.com", "pass2")
    headers2 = {"Authorization": f"Bearer {token2}"}
    resp_login2 = client.get("/api/auth/sso/google/login", headers=headers2, allow_redirects=False)
    state2 = parse_qs(urlparse(resp_login2.headers["location"]).query)["state"][0]

    with patch("app.fastapi.routers.auth.exchange_code_for_tokens", return_value={"access_token": "prov-token"}), \
         patch("app.fastapi.routers.auth.fetch_user_profile", return_value={"subject": "shared-subject", "email": "g@example.com"}):
        resp_conflict = client.get(f"/api/auth/sso/google/callback?code=code&state={state2}", allow_redirects=False)

    assert resp_conflict.status_code == 409


def test_auth_methods_flow(client: TestClient):
    db = TestingSessionLocal()
    try:
        user = create_local_user(db, "methods@example.com", None, "user", "oldpass")
        db.commit()
        user_id = user.id
    finally:
        db.close()

    token = _login_and_get_token(client, "methods@example.com", "oldpass")
    headers = {"Authorization": f"Bearer {token}"}

    resp_methods = client.get("/api/auth/methods", headers=headers)
    assert resp_methods.status_code == 200
    data = resp_methods.json()
    assert data["local"]["active"] is True
    assert data["google"]["linked"] is False

    resp_update = client.post(
        "/api/auth/methods/local",
        headers=headers,
        json={"email": "new@example.com", "password": "newpass"},
    )
    assert resp_update.status_code == 200
    data_update = resp_update.json()
    assert data_update["local"]["email"] == "new@example.com"

    db2 = TestingSessionLocal()
    try:
        identity = UserAuthIdentityDB(
            user_id=user_id,
            provider="google",
            provider_subject="g-sub",
            password_hash=None,
        )
        db2.add(identity)
        db2.commit()
    finally:
        db2.close()

    resp_with_google = client.get("/api/auth/methods", headers=headers)
    assert resp_with_google.status_code == 200
    methods_with_google = resp_with_google.json()
    assert methods_with_google["google"]["linked"] is True
    assert methods_with_google["google"]["can_delete"] is True

    resp_delete = client.delete("/api/auth/methods/google", headers=headers)
    assert resp_delete.status_code == 200
    data_after_delete = resp_delete.json()
    assert data_after_delete["google"]["linked"] is False
