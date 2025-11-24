from __future__ import annotations

import importlib
from typing import Optional

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models.database_models import Base, DeviceDB, UserDB
from app.repositories.users import create_local_user, get_user_by_email


def _db_module():
    return importlib.import_module('app.core.database')


def _create_tables() -> None:
    db_mod = _db_module()
    if hasattr(db_mod, 'create_tables'):
        db_mod.create_tables()
    if hasattr(db_mod, 'engine'):
        Base.metadata.create_all(bind=db_mod.engine)


def _session() -> Session:
    db_mod = _db_module()
    return db_mod.SessionLocal()


def ensure_user(email: str, password: str, role: str = 'user', is_active: bool = True) -> UserDB:
    # Translitem: sozdaet ili vozvrashaet polzovatelya v tekushey test-baze.
    _create_tables()
    session = _session()
    try:
        user = get_user_by_email(session, email)
        if user is None:
            user = create_local_user(session, email, None, role, password)
        user.is_active = is_active
        session.add(user)
        session.commit()
        session.refresh(user)
        return user
    finally:
        session.close()


def ensure_device(device_id: str, user_id: Optional[int] = None) -> DeviceDB:
    # Translitem: sozdaet ili obnovlyaet ustrojstvo v tekushey test-baze.
    _create_tables()
    session = _session()
    try:
        device = session.query(DeviceDB).filter(DeviceDB.device_id == device_id).first()
        if device is None:
            device = DeviceDB(device_id=device_id, name=f'Device {device_id}')
            session.add(device)
        device.user_id = user_id
        session.commit()
        session.refresh(device)
        return device
    finally:
        session.close()


def login_and_get_token(client: TestClient, email: str, password: str, role: str = 'user', is_active: bool = True) -> str:
    ensure_user(email, password, role=role, is_active=is_active)
    response = client.post(
        '/api/auth/login',
        json={'email': email, 'password': password},
    )
    assert response.status_code == 200, response.text
    token = response.json().get('access_token')
    assert token
    return token


def auth_headers(client: TestClient, email: str, password: str, role: str = 'user', is_active: bool = True) -> dict[str, str]:
    token = login_and_get_token(client, email, password, role=role, is_active=is_active)
    return {'Authorization': f'Bearer {token}'}
