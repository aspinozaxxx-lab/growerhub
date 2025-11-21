from typing import Optional

from sqlalchemy.orm import Session

from app.models.database_models import UserAuthIdentityDB, UserDB
from app.core.security import hash_password, verify_password

__all__ = [
    "get_user_by_id",
    "get_user_by_email",
    "create_local_user",
    "authenticate_local_user",
]


def get_user_by_id(db: Session, user_id: int) -> Optional[UserDB]:
    """Translitem: prosto poiski polzovatelya po id."""

    return db.query(UserDB).filter(UserDB.id == user_id).first()


def get_user_by_email(db: Session, email: str) -> Optional[UserDB]:
    """Translitem: poiski polzovatelya po email."""

    return db.query(UserDB).filter(UserDB.email == email).first()


def create_local_user(db: Session, email: str, username: Optional[str], role: str, password: str) -> UserDB:
    """Translitem: sozdanie polzovatelya s lokal'nym parolem i identity."""

    existing = get_user_by_email(db, email)
    if existing:
        raise ValueError("User with this email already exists")

    user = UserDB(
        email=email,
        username=username,
        role=role,
        is_active=True,
    )
    db.add(user)
    db.flush()  # Translitem: nuzhen id pered sozdaniem identity

    identity = UserAuthIdentityDB(
        user_id=user.id,
        provider="local",
        provider_subject=None,
        password_hash=hash_password(password),
    )
    db.add(identity)

    db.commit()
    db.refresh(user)
    return user


def authenticate_local_user(db: Session, email: str, password: str) -> Optional[UserDB]:
    """Translitem: bazovaya proverka logina/parolya dlya lokal'nogo polzovatelya."""

    user = get_user_by_email(db, email)
    if not user:
        return None
    if not user.is_active:
        return None

    identity = (
        db.query(UserAuthIdentityDB)
        .filter(
            UserAuthIdentityDB.user_id == user.id,
            UserAuthIdentityDB.provider == "local",
        )
        .first()
    )
    if not identity:
        return None

    if not verify_password(password, identity.password_hash):
        return None

    return user
