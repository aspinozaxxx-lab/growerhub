from __future__ import annotations

from typing import Any, Dict, Optional

import httpx
from jose import JWTError
from sqlalchemy.orm import Session

from app.core.security import create_access_token, decode_access_token
from app.models.database_models import UserAuthIdentityDB, UserDB
from config import get_settings

# Translitem: spisok dopustimyh SSO provajderov.
SUPPORTED_PROVIDERS = {"google", "yandex"}


def _validate_provider(provider: str) -> str:
    """Translitem: proveryaem chto provajder iz podderzhivaemyh."""

    if provider not in SUPPORTED_PROVIDERS:
        raise ValueError("Unsupported provider")
    return provider


def _require_mode(mode: str) -> str:
    """Translitem: razreshennye rezhimy state."""

    if mode not in {"login", "link"}:
        raise ValueError("Unsupported mode")
    return mode


def build_sso_state(provider: str, mode: str, redirect_path: Optional[str], current_user_id: Optional[int]) -> str:
    """Translitem: kodiruet state dlya SSO kak kratkozhivushchij JWT."""

    _validate_provider(provider)
    _require_mode(mode)
    payload: Dict[str, Any] = {
        "provider": provider,
        "mode": mode,
        "redirect_path": redirect_path,
        "current_user_id": current_user_id,
    }
    # Translitem: malenkij TTL chtoby minimizirovat risk podlogi state.
    return create_access_token(payload, expires_minutes=10)


def verify_sso_state(state: str) -> Dict[str, Any]:
    """Translitem: dekodiruet state i proveryaet osnovnye polya."""

    try:
        payload = decode_access_token(state)
    except JWTError as exc:
        raise ValueError("Invalid state") from exc

    provider = payload.get("provider")
    mode = payload.get("mode")
    redirect_path = payload.get("redirect_path")
    current_user_id = payload.get("current_user_id")

    _validate_provider(provider)
    _require_mode(mode)

    return {
        "provider": provider,
        "mode": mode,
        "redirect_path": redirect_path,
        "current_user_id": current_user_id,
    }


def _provider_settings(provider: str) -> Dict[str, str]:
    """Translitem: dostavayem URL i klientov dlya provajdera."""

    settings = get_settings()
    if provider == "google":
        return {
            "client_id": settings.AUTH_GOOGLE_CLIENT_ID,
            "client_secret": settings.AUTH_GOOGLE_CLIENT_SECRET,
            "auth_url": settings.AUTH_GOOGLE_AUTH_URL,
            "token_url": settings.AUTH_GOOGLE_TOKEN_URL,
            "userinfo_url": settings.AUTH_GOOGLE_USERINFO_URL,
            "scope": "openid email profile",
            "access_type": "offline",
            "prompt": "consent",
        }
    if provider == "yandex":
        return {
            "client_id": settings.AUTH_YANDEX_CLIENT_ID,
            "client_secret": settings.AUTH_YANDEX_CLIENT_SECRET,
            "auth_url": settings.AUTH_YANDEX_AUTH_URL,
            "token_url": settings.AUTH_YANDEX_TOKEN_URL,
            "userinfo_url": settings.AUTH_YANDEX_USERINFO_URL,
            "scope": "login:email",
        }
    raise ValueError("Unsupported provider")


def build_auth_url(provider: str, redirect_uri: str, state: str) -> str:
    """Translitem: formiruet URL avtorizacii u provajdera."""

    _validate_provider(provider)
    cfg = _provider_settings(provider)

    params = {
        "response_type": "code",
        "client_id": cfg["client_id"],
        "redirect_uri": redirect_uri,
        "scope": cfg["scope"],
        "state": state,
    }
    if provider == "google":
        params.update(
            {
                "access_type": cfg.get("access_type", "online"),
                "prompt": cfg.get("prompt", "consent"),
                "include_granted_scopes": "true",
            }
        )

    query = str(httpx.QueryParams(params))
    return f"{cfg['auth_url']}?{query}"


def exchange_code_for_tokens(provider: str, code: str, redirect_uri: str) -> Dict[str, Any]:
    """Translitem: obmenivaet authorization code na tokeny provajdera."""

    _validate_provider(provider)
    cfg = _provider_settings(provider)
    data = {
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": redirect_uri,
        "client_id": cfg["client_id"],
        "client_secret": cfg["client_secret"],
    }

    response = httpx.post(cfg["token_url"], data=data, timeout=10.0)
    if response.status_code >= 400:
        raise ValueError("Failed to exchange code for tokens")
    return response.json()


def fetch_user_profile(provider: str, tokens: Dict[str, Any]) -> Dict[str, Any]:
    """Translitem: poluchaet profil polzovatelya u provajdera i normalizuet ego."""

    _validate_provider(provider)
    cfg = _provider_settings(provider)
    access_token = tokens.get("access_token")
    if not access_token:
        raise ValueError("No access token provided")

    headers = {"Authorization": f"Bearer {access_token}"}
    response = httpx.get(cfg["userinfo_url"], headers=headers, timeout=10.0)
    if response.status_code >= 400:
        raise ValueError("Failed to fetch userinfo")
    raw = response.json()

    if provider == "google":
        subject = raw.get("sub")
        email = raw.get("email")
        email_verified = raw.get("email_verified")
    else:  # yandex
        subject = raw.get("id") or raw.get("uid")
        email = raw.get("default_email") or raw.get("emails", [None])[0]
        email_verified = raw.get("is_email_verified")

    return {
        "subject": subject,
        "email": email,
        "email_verified": bool(email_verified) if email_verified is not None else None,
    }


def get_or_create_user_from_sso(db: Session, provider: str, subject: str, email: Optional[str]) -> UserDB:
    """Translitem: vozvrashchaet suschestvuyushchego polzovatelya ili sozdaet novogo ot SSO."""

    _validate_provider(provider)
    identity = (
        db.query(UserAuthIdentityDB)
        .filter(
            UserAuthIdentityDB.provider == provider,
            UserAuthIdentityDB.provider_subject == subject,
        )
        .first()
    )
    if identity:
        return db.query(UserDB).filter(UserDB.id == identity.user_id).first()

    existing_email_user = db.query(UserDB).filter(UserDB.email == email).first() if email else None
    user_email = email or f"{provider}_{subject}@example.invalid"  # Translitem: tehnicheskij email esli SSO ne vernul pochty
    if existing_email_user:
        user_email = f"{provider}_{subject}@example.invalid"  # Translitem: rezervnyj tehnicheskij email esli ukazannyj uzhe zanyat
    user = UserDB(email=user_email, username=None, role="user", is_active=True)
    db.add(user)
    db.flush()

    new_identity = UserAuthIdentityDB(
        user_id=user.id,
        provider=provider,
        provider_subject=subject,
        password_hash=None,
    )
    db.add(new_identity)
    db.commit()
    db.refresh(user)
    return user
