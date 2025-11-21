from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import create_access_token, get_current_user
from app.models.database_models import UserDB
from app.models.user_schemas import LoginRequest, Token, UserOut
from app.repositories.users import authenticate_local_user

router = APIRouter()


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

    return UserOut(
        id=current_user.id,
        email=current_user.email,
        username=current_user.username,
        role=current_user.role,
        is_active=current_user.is_active,
        created_at=current_user.created_at,
        updated_at=current_user.updated_at,
    )
