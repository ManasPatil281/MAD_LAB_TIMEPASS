from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, EmailStr, field_validator


# ── Request Schemas ─────────────────────────────────────────────────────────

class RegisterRequest(BaseModel):
    full_name: str
    email: EmailStr
    password: str
    role: str = "researcher"
    institution: Optional[str] = None

    @field_validator("role")
    @classmethod
    def validate_role(cls, v: str) -> str:
        if v not in ("researcher", "admin"):
            raise ValueError("role must be 'researcher' or 'admin'")
        return v

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("password must be at least 8 characters")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


# ── Response Schemas ─────────────────────────────────────────────────────────

class UserInToken(BaseModel):
    user_id: UUID
    email: str
    full_name: str
    role: str
    institution: Optional[str] = None

    model_config = {"from_attributes": True}


class RegisterResponse(BaseModel):
    user_id: UUID
    email: str
    role: str
    token: str


class LoginResponse(BaseModel):
    token: str
    expires_at: datetime
    user: UserInToken


class LogoutResponse(BaseModel):
    message: str
