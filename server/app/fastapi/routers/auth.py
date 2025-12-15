from __future__ import annotations

from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, Response, status
from fastapi.responses import JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.refresh_tokens import (
    clear_refresh_cookie,
    generate_refresh_token,
    hash_refresh_token,
    refresh_expires_at,
    set_refresh_cookie,
)
from app.core.security import (
    create_access_token,
    decode_access_token,
    get_current_user,
    hash_password,
    verify_password,
)
from app.core.sso import (
    SUPPORTED_PROVIDERS,
    build_auth_url,
    build_sso_state,
    exchange_code_for_tokens,
    fetch_user_profile,
    get_or_create_user_from_sso,
    verify_sso_state,
)
from app.models.database_models import UserAuthIdentityDB, UserDB
from app.models.user_schemas import (
    AuthMethodLocalIn,
    LoginRequest,
    PasswordChangeIn,
    Token,
    UserOut,
    UserProfileUpdate,
)
from app.repositories.users import (
    authenticate_local_user,
    find_identity_by_subject,
    get_identities_by_user,
    get_identity_by_provider,
    get_user_by_id,
)
from app.repositories.refresh_tokens import (
    create_refresh_token,
    get_refresh_token_by_hash,
    revoke_refresh_token,
)
from config import get_settings

router = APIRouter()


def _user_to_out(user: UserDB) -> UserOut:
    """Translitem: transformaciya modeli UserDB v pydantic UserOut."""

    return UserOut(
        id=user.id,
        email=user.email,
        username=user.username,
        role=user.role,
        is_active=user.is_active,
        created_at=user.created_at,
        updated_at=user.updated_at,
    )


def _optional_current_user(
    db: Session = Depends(get_db),
    authorization: Optional[str] = Header(None),
) -> Optional[UserDB]:
    """Translitem: neobyazatel'nyj tekushchij user dlya rezhima link SSO."""

    if not authorization:
        return None
    try:
        scheme, token = authorization.split(" ", 1)
    except ValueError:
        return None
    if scheme.lower() != "bearer":
        return None
    try:
        payload = decode_access_token(token)
        user_id = int(payload.get("user_id"))
    except Exception:
        return None
    return get_user_by_id(db, user_id)


def _build_callback_uri(provider: str) -> str:
    """Translitem: sobiraet redirect_uri s uchetom bazovogo hosta iz nastroek."""

    settings = get_settings()
    path = f"/api/auth/sso/{provider}/callback"
    if settings.AUTH_SSO_REDIRECT_BASE:
        return settings.AUTH_SSO_REDIRECT_BASE.rstrip("/") + path
    return path


def _auth_methods_response(db: Session, user: UserDB) -> dict:
    """Translitem: statys sposobov vhoda dlya profilya."""

    identities = get_identities_by_user(db, user.id)
    total = len(identities)
    local_identity = next((i for i in identities if i.provider == "local"), None)
    google_identity = next((i for i in identities if i.provider == "google"), None)
    yandex_identity = next((i for i in identities if i.provider == "yandex"), None)

    local_active = bool(local_identity and local_identity.password_hash)
    can_delete = total > 1

    return {
        "local": {
            "active": local_active,
            "email": user.email,
            "can_delete": can_delete if local_identity else False,
        },
        "google": {
            "linked": google_identity is not None,
            "provider_subject": google_identity.provider_subject if google_identity else None,
            "can_delete": can_delete if google_identity else False,
        },
        "yandex": {
            "linked": yandex_identity is not None,
            "provider_subject": yandex_identity.provider_subject if yandex_identity else None,
            "can_delete": can_delete if yandex_identity else False,
        },
    }


@router.post("/api/auth/login", response_model=Token)
def login(
    form: LoginRequest,
    request: Request,
    response: Response,
    db: Session = Depends(get_db),
) -> Token:
    """Translitem: login po email i parolyu, vozvrashchaet JWT."""

    user = authenticate_local_user(db, form.email, form.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Nevernyj email ili parol'",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = create_access_token({"user_id": user.id})

    refresh_raw = generate_refresh_token()
    refresh_hash = hash_refresh_token(refresh_raw)
    expires_at = refresh_expires_at()
    create_refresh_token(
        db,
        user_id=user.id,
        token_hash=refresh_hash,
        expires_at=expires_at,
        user_agent=request.headers.get("user-agent"),
        ip=getattr(getattr(request, "client", None), "host", None),
    )
    db.commit()
    set_refresh_cookie(response, refresh_raw)

    return Token(access_token=token, token_type="bearer")


@router.get("/api/auth/sso/{provider}/login")
async def sso_login(
    provider: str,
    request: Request,
    redirect_path: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: Optional[UserDB] = Depends(_optional_current_user),
):
    """Translitem: start OAuth2 potoka dlya login ili link."""

    if provider not in SUPPORTED_PROVIDERS:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Provider not found")

    mode = "login"
    current_user_id: Optional[int] = None
    if current_user:
        mode = "link"
        current_user_id = current_user.id

    if not redirect_path:
        # default redirect dlya login/link
        redirect_path = "/static/profile.html" if mode == "link" else "/app"

    redirect_uri = _build_callback_uri(provider)
    try:
        state = build_sso_state(provider, mode, redirect_path, current_user_id)
        auth_url = build_auth_url(provider, redirect_uri, state)
    except ValueError:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid SSO request")

    accept = request.headers.get("accept", "")
    if "application/json" in accept.lower():
        return JSONResponse({"url": auth_url})
    return RedirectResponse(url=auth_url, status_code=status.HTTP_302_FOUND)


@router.get("/api/auth/sso/{provider}/callback")
def sso_callback(
    provider: str,
    request: Request,
    code: str,
    state: str,
    db: Session = Depends(get_db),
):
    """Translitem: obrabotka callbacka ot provajdera SSO."""

    if provider not in SUPPORTED_PROVIDERS:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Provider not found")

    try:
        state_data = verify_sso_state(state)
    except ValueError:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid state")

    if state_data.get("provider") != provider:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Provider mismatch")

    redirect_path = state_data.get("redirect_path") or "/app"
    redirect_uri = _build_callback_uri(provider)

    try:
        tokens = exchange_code_for_tokens(provider, code, redirect_uri)
        profile = fetch_user_profile(provider, tokens)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    subject = profile.get("subject")
    email = profile.get("email")
    if not subject:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No subject from provider")

    mode = state_data.get("mode")
    if mode == "login":
        user = get_or_create_user_from_sso(db, provider, subject, email)
        token = create_access_token({"user_id": user.id})
        redirect_target = redirect_path or "/app"
        separator = "&" if "?" in redirect_target else "?"
        redirect_with_token = f"{redirect_target}{separator}access_token={token}"

        refresh_raw = generate_refresh_token()
        refresh_hash = hash_refresh_token(refresh_raw)
        expires_at = refresh_expires_at()
        create_refresh_token(
            db,
            user_id=user.id,
            token_hash=refresh_hash,
            expires_at=expires_at,
            user_agent=request.headers.get("user-agent"),
            ip=getattr(getattr(request, "client", None), "host", None),
        )
        db.commit()

        resp = RedirectResponse(url=redirect_with_token, status_code=status.HTTP_302_FOUND)
        set_refresh_cookie(resp, refresh_raw)
        return resp

    current_user_id = state_data.get("current_user_id")
    try:
        current_user_id_int = int(current_user_id)
    except (TypeError, ValueError):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User required for link")

    current_user = get_user_by_id(db, current_user_id_int)
    if not current_user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    redirect_target = redirect_path or "/static/profile.html"

    identity_same_subject = find_identity_by_subject(db, provider, subject)
    if identity_same_subject and identity_same_subject.user_id != current_user.id:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Identity already linked")

    identity_for_user = get_identity_by_provider(db, current_user.id, provider)
    if not identity_for_user:
        identity_for_user = UserAuthIdentityDB(
            user_id=current_user.id,
            provider=provider,
            provider_subject=subject,
            password_hash=None,
        )
        db.add(identity_for_user)
    else:
        identity_for_user.provider_subject = subject
        identity_for_user.password_hash = None

    db.commit()
    return RedirectResponse(url=redirect_target, status_code=status.HTTP_302_FOUND)


@router.get("/api/auth/me", response_model=UserOut)
def read_me(current_user: UserDB = Depends(get_current_user)) -> UserOut:
    """Translitem: vozvrashchaet dannye tekushchego avtorizovannogo polzovatelya."""

    return _user_to_out(current_user)


@router.post("/api/auth/refresh", response_model=Token)
def refresh_access_token(
    request: Request,
    response: Response,
    db: Session = Depends(get_db),
) -> Token:
    """Translitem: vydaet novyj access JWT po refresh tokenu iz httpOnly cookie."""

    settings = get_settings()
    refresh_raw = request.cookies.get(settings.REFRESH_TOKEN_COOKIE_NAME)
    if not refresh_raw:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token required")

    refresh_hash = hash_refresh_token(refresh_raw)
    record = get_refresh_token_by_hash(db, refresh_hash)
    if not record:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    if record.revoked_at is not None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token revoked")
    if record.expires_at <= datetime.utcnow():
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token expired")

    user = get_user_by_id(db, int(record.user_id))
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="User disabled")

    # Translitem: rotaciya refresh tokena - staryj invalidiruem, novyj vydajem i sohranyaem v BD.
    revoke_refresh_token(db, record)
    next_refresh_raw = generate_refresh_token()
    next_refresh_hash = hash_refresh_token(next_refresh_raw)
    next_expires_at = refresh_expires_at()
    create_refresh_token(
        db,
        user_id=user.id,
        token_hash=next_refresh_hash,
        expires_at=next_expires_at,
        user_agent=request.headers.get("user-agent"),
        ip=getattr(getattr(request, "client", None), "host", None),
    )
    db.commit()
    set_refresh_cookie(response, next_refresh_raw)

    token = create_access_token({"user_id": user.id})
    return Token(access_token=token, token_type="bearer")


@router.post("/api/auth/logout")
def logout(
    request: Request,
    response: Response,
    db: Session = Depends(get_db),
) -> dict:
    """Translitem: invalidiruet refresh token iz cookie i ochishchaet cookie."""

    settings = get_settings()
    refresh_raw = request.cookies.get(settings.REFRESH_TOKEN_COOKIE_NAME)
    if refresh_raw:
        refresh_hash = hash_refresh_token(refresh_raw)
        record = get_refresh_token_by_hash(db, refresh_hash)
        if record and record.revoked_at is None:
            revoke_refresh_token(db, record)
            db.commit()

    clear_refresh_cookie(response)
    return {"message": "logged out"}


@router.patch("/api/auth/me", response_model=UserOut)
def update_me(
    payload: UserProfileUpdate,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
) -> UserOut:
    """Translitem: obnovlenie email/username tekushchego polzovatelya."""

    if payload.email is not None:
        existing = (
            db.query(UserDB)
            .filter(UserDB.email == payload.email, UserDB.id != current_user.id)
            .first()
        )
        if existing:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Polzovatel' s takim email uzhe sushhestvuet",
            )
        current_user.email = payload.email

    if payload.username is not None:
        current_user.username = payload.username

    db.commit()
    db.refresh(current_user)
    return _user_to_out(current_user)


@router.post("/api/auth/change-password")
def change_password(
    payload: PasswordChangeIn,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
) -> dict:
    """Translitem: eto endpoint dlja smeny parolja lokal'nogo pol'zovatelja."""

    identity = (
        db.query(UserAuthIdentityDB)
        .filter(
            UserAuthIdentityDB.user_id == current_user.id,
            UserAuthIdentityDB.provider == "local",
        )
        .first()
    )
    if identity is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Local identity ne najdena")

    if not verify_password(payload.current_password, identity.password_hash):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="nevernyj tekushhij parol'",
        )

    identity.password_hash = hash_password(payload.new_password)
    db.commit()

    return {"message": "password updated"}


@router.get("/api/auth/methods")
def auth_methods(
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    """Translitem: vozvrashchaet status dostupnyh sposobov vhoda tekushchego polzovatelya."""

    return _auth_methods_response(db, current_user)


@router.post("/api/auth/methods/local")
def configure_local_method(
    payload: AuthMethodLocalIn,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    """Translitem: vklyuchaet ili obnovlyaet lokal'nyj email/parol' dlya vhoda."""

    existing = (
        db.query(UserDB)
        .filter(UserDB.email == payload.email, UserDB.id != current_user.id)
        .first()
    )
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email uzhe zanyat")

    current_user.email = payload.email
    identity = get_identity_by_provider(db, current_user.id, "local")
    password_hash = hash_password(payload.password)
    if identity:
        identity.password_hash = password_hash
    else:
        identity = UserAuthIdentityDB(
            user_id=current_user.id,
            provider="local",
            provider_subject=None,
            password_hash=password_hash,
        )
        db.add(identity)

    db.commit()
    db.refresh(current_user)
    return _auth_methods_response(db, current_user)


@router.delete("/api/auth/methods/{provider}")
def delete_auth_method(
    provider: str,
    db: Session = Depends(get_db),
    current_user: UserDB = Depends(get_current_user),
):
    """Translitem: udalяет sposob vhoda, esli on ne poslednij."""

    if provider not in {"local", "google", "yandex"}:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Provider not supported")

    identities = get_identities_by_user(db, current_user.id)
    if len(identities) <= 1:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Nelzya udalit's poslednij sposob vhoda")

    identity = next((i for i in identities if i.provider == provider), None)
    if not identity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sposob vhoda ne najden")

    db.delete(identity)
    db.commit()
    return _auth_methods_response(db, current_user)
