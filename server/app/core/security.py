from datetime import datetime, timedelta
from typing import Any, Dict, Optional

from jose import jwt
from passlib.context import CryptContext

from config import get_settings

__all__ = [
    "pwd_context",
    "hash_password",
    "verify_password",
    "create_access_token",
    "decode_access_token",
]

# Translitem: obshchij kontekst dlya heshovaniya parolej
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


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
