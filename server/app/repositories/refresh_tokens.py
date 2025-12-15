from __future__ import annotations

from datetime import datetime
from typing import Optional

from sqlalchemy.orm import Session

from app.models.database_models import UserRefreshTokenDB

__all__ = [
    "create_refresh_token",
    "get_refresh_token_by_hash",
    "revoke_refresh_token",
]


def create_refresh_token(
    db: Session,
    *,
    user_id: int,
    token_hash: str,
    expires_at: datetime,
    user_agent: Optional[str] = None,
    ip: Optional[str] = None,
) -> UserRefreshTokenDB:
    """Translitem: sozdanie zapisi refresh tokena v BD (token hranim tol'ko hashom)."""

    record = UserRefreshTokenDB(
        user_id=user_id,
        token_hash=token_hash,
        expires_at=expires_at,
        revoked_at=None,
        user_agent=user_agent,
        ip=ip,
    )
    db.add(record)
    db.flush()
    return record


def get_refresh_token_by_hash(db: Session, token_hash: str) -> Optional[UserRefreshTokenDB]:
    """Translitem: poiski refresh tokena po ego hash (raw tokina v BD net)."""

    return db.query(UserRefreshTokenDB).filter(UserRefreshTokenDB.token_hash == token_hash).first()


def revoke_refresh_token(db: Session, record: UserRefreshTokenDB) -> UserRefreshTokenDB:
    """Translitem: invalidaciya refresh tokena (revoked_at)."""

    if record.revoked_at is None:
        record.revoked_at = datetime.utcnow()
        db.add(record)
    return record

