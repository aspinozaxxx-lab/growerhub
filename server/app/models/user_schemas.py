from __future__ import annotations

from datetime import datetime
from typing import Optional

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, EmailStr


class Token(BaseModel):
    """Translitem: otvet s dannymi tokena dlya klienta."""

    access_token: str
    token_type: str


class LoginRequest(BaseModel):
    """Translitem: vhodnye dannye dlya logina po email/parolyu."""

    email: EmailStr
    password: str


class UserOut(BaseModel):
    """Translitem: publichnye polya polzovatelya dlya API otvetov."""

    id: int
    email: str
    username: Optional[str] = None
    role: str
    is_active: bool
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


class UserProfileUpdate(BaseModel):
    """Translitem: payload dlya obnovleniya profilya tekushchego polzovatelya."""

    email: Optional[EmailStr] = None
    username: Optional[str] = None


class UserCreate(BaseModel):
    """Translitem: payload dlya sozdaniya lokal'nogo polzovatelya."""

    email: str
    username: Optional[str] = None
    role: Optional[str] = "user"
    password: str


class UserUpdate(BaseModel):
    """Translitem: dannye dlya chastichnogo obnovleniya polzovatelya."""

    username: Optional[str] = None
    role: Optional[str] = None
    is_active: Optional[bool] = None


class PasswordChangeIn(BaseModel):
    """Translitem: payload dlya smeny parolya tekushchego polzovatelya."""

    current_password: str
    new_password: str
