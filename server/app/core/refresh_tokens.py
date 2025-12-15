from __future__ import annotations

import hashlib
import hmac
import secrets
from datetime import datetime, timedelta
from typing import Optional

from fastapi import Response

from config import get_settings


def generate_refresh_token() -> str:
    """Translitem: generiruem sluchajnyj dolgogivushchij refresh token (ne JWT)."""

    return secrets.token_urlsafe(48)


def hash_refresh_token(token: str) -> str:
    """Translitem: hash s pepper (SECRET_KEY), chtoby v BD ne hranit raw token."""

    settings = get_settings()
    digest = hmac.new(
        settings.SECRET_KEY.encode("utf-8"),
        token.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return digest


def refresh_expires_at(now: Optional[datetime] = None) -> datetime:
    """Translitem: srok godnosti refresh tokena."""

    settings = get_settings()
    base = now or datetime.utcnow()
    return base + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)


def set_refresh_cookie(response: Response, refresh_token: str) -> None:
    """Translitem: ustanovit' httpOnly cookie s refresh tokenom."""

    settings = get_settings()
    response.set_cookie(
        key=settings.REFRESH_TOKEN_COOKIE_NAME,
        value=refresh_token,
        httponly=True,
        secure=settings.REFRESH_TOKEN_COOKIE_SECURE,
        samesite=settings.REFRESH_TOKEN_COOKIE_SAMESITE,
        path=settings.REFRESH_TOKEN_COOKIE_PATH,
        max_age=settings.REFRESH_TOKEN_EXPIRE_DAYS * 24 * 60 * 60,
    )


def clear_refresh_cookie(response: Response) -> None:
    """Translitem: udalit' refresh cookie (logout)."""

    settings = get_settings()
    response.delete_cookie(
        key=settings.REFRESH_TOKEN_COOKIE_NAME,
        path=settings.REFRESH_TOKEN_COOKIE_PATH,
    )

