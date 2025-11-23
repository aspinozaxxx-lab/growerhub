from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import (
    create_access_token,
    get_current_user,
    hash_password,
    verify_password,
)
from app.models.database_models import UserAuthIdentityDB, UserDB
from app.models.user_schemas import (
    LoginRequest,
    PasswordChangeIn,
    Token,
    UserOut,
    UserProfileUpdate,
)
from app.repositories.users import authenticate_local_user

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


@router.post("/api/auth/login", response_model=Token)
def login(form: LoginRequest, db: Session = Depends(get_db)) -> Token:
    """Translitem: login po email i parolyu, vozvrashchaet JWT."""

    user = authenticate_local_user(db, form.email, form.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Nevernyj email ili parol'",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = create_access_token({"user_id": user.id})
    return Token(access_token=token, token_type="bearer")


@router.get("/api/auth/me", response_model=UserOut)
def read_me(current_user: UserDB = Depends(get_current_user)) -> UserOut:
    """Translitem: vozvrashchaet dannye tekushchego avtorizovannogo polzovatelya."""

    return _user_to_out(current_user)


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
