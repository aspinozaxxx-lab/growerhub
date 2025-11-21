from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import get_current_admin
from app.models.database_models import UserAuthIdentityDB, UserDB
from app.models.user_schemas import UserCreate, UserOut, UserUpdate
from app.repositories.users import create_local_user, get_user_by_id

router = APIRouter()


def _user_to_out(user: UserDB) -> UserOut:
    """Translitem: konvertaciya modeli BD v pydantic otvet."""

    return UserOut(
        id=user.id,
        email=user.email,
        username=user.username,
        role=user.role,
        is_active=user.is_active,
        created_at=user.created_at,
        updated_at=user.updated_at,
    )


@router.get("/api/users", response_model=list[UserOut])
def list_users(
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
) -> list[UserOut]:
    """Translitem: vozvrashchaet spisok vseh polzovateley (tolko dlya admina)."""

    users = db.query(UserDB).all()
    return [_user_to_out(user) for user in users]


@router.get("/api/users/{user_id}", response_model=UserOut)
def get_user(
    user_id: int,
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
) -> UserOut:
    """Translitem: vozvrashchaet dannye odnog polzovatelya po id."""

    user = get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Polzovatel' ne najden")
    return _user_to_out(user)


@router.post("/api/users", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def create_user(
    payload: UserCreate,
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
) -> UserOut:
    """Translitem: sozdaet novogo lokal'nogo polzovatelya (admin-only)."""

    try:
        user = create_local_user(
            db,
            payload.email,
            payload.username,
            payload.role or "user",
            payload.password,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Polzovatel' s takim email uzhe sushhestvuet",
        ) from exc

    return _user_to_out(user)


@router.patch("/api/users/{user_id}", response_model=UserOut)
def update_user(
    user_id: int,
    payload: UserUpdate,
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
) -> UserOut:
    """Translitem: chastichnoe obnovlenie polzovatelya (admin-only)."""

    user = get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Polzovatel' ne najden")

    if payload.username is not None:
        user.username = payload.username
    if payload.role is not None:
        user.role = payload.role
    if payload.is_active is not None:
        user.is_active = payload.is_active

    db.commit()
    db.refresh(user)
    return _user_to_out(user)


@router.delete("/api/users/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_user(
    user_id: int,
    db: Session = Depends(get_db),
    admin: UserDB = Depends(get_current_admin),
) -> Response:
    """Translitem: udalyaet polzovatelya i ego identity (admin-only)."""

    user = get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Polzovatel' ne najden")

    db.query(UserAuthIdentityDB).filter(UserAuthIdentityDB.user_id == user_id).delete()
    db.delete(user)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
