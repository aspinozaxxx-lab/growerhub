from datetime import datetime, timedelta
from typing import Any, Dict, Optional

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import jwt
from sqlalchemy.orm import Session
from passlib.context import CryptContext

from app.core.database import get_db
from app.models.database_models import UserDB
from config import get_settings

__all__ = [
    "pwd_context",
    "oauth2_scheme",
    "hash_password",
    "verify_password",
    "create_access_token",
    "decode_access_token",
    "get_current_user",
    "get_current_admin",
]

# Translitem: obshchij kontekst dlya heshovaniya parolej (bez problema wrap bug na win).
pwd_context = CryptContext(schemes=["pbkdf2_sha256"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login")


def hash_password(plain_password: str) -> str:
    """Translitem: vozvrashchaet bezopasnyj hesh parolya dlya hraneniya v BD."""

    return pwd_context.hash(plain_password)


def verify_password(plain_password: str, password_hash: str) -> bool:
    """Translitem: sravnivaet vhodnoj parol' s zafiksirovannym heshom."""

    return pwd_context.verify(plain_password, password_hash)


def create_access_token(data: Dict[str, Any], expires_minutes: Optional[int] = None) -> str:
    """Translitem: sozdanie JWT tokena dlya avtorizatsii."""

    settings = get_settings()
    to_encode = data.copy()
    expire_delta = timedelta(minutes=expires_minutes if expires_minutes is not None else settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode["exp"] = datetime.utcnow() + expire_delta
    return jwt.encode(to_encode, settings.SECRET_KEY, algorithm=settings.AUTH_JWT_ALGORITHM)


def decode_access_token(token: str) -> Dict[str, Any]:
    """Translitem: proverka i raskodirovanie JWT tokena."""

    settings = get_settings()
    return jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.AUTH_JWT_ALGORITHM])


def get_current_user(
    db: Session = Depends(get_db),
    token: str = Depends(oauth2_scheme),
) -> UserDB:
    """Translitem: validaciya JWT i vozvrat tekushchego polzovatelya."""

    from app.repositories.users import get_user_by_id

    try:
        payload = decode_access_token(token)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Ne udalos' raspoznavat' token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    user_id = payload.get("user_id")
    try:
        user_id_int = int(user_id)
    except (TypeError, ValueError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Ne udalos' raspoznavat' token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    user = get_user_by_id(db, user_id_int)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Polzovatel' ne najden",
            headers={"WWW-Authenticate": "Bearer"},
        )
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Polzovatel' otkljuchen")

    return user


def get_current_admin(
    current_user: UserDB = Depends(get_current_user),
) -> UserDB:
    """Translitem: proverka roli admina dlya dostupa k zaritshchennym resursam."""

    if current_user.role != "admin":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Nedostatochno prav")

    return current_user
