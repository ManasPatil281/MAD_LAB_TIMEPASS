from datetime import datetime
from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel, EmailStr


# ── Request Schemas ─────────────────────────────────────────────────────────

class UpdateProfileRequest(BaseModel):
    full_name: Optional[str] = None
    institution: Optional[str] = None


class UpdateUserStatusRequest(BaseModel):
    is_active: bool


# ── Response Schemas ─────────────────────────────────────────────────────────

class UserProfileResponse(BaseModel):
    user_id: UUID
    full_name: str
    email: str
    role: str
    institution: Optional[str] = None
    is_active: bool
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class UserStatusResponse(BaseModel):
    user_id: UUID
    is_active: bool
    updated_at: datetime

    model_config = {"from_attributes": True}


class UserListItem(BaseModel):
    user_id: UUID
    full_name: str
    email: str
    role: str
    institution: Optional[str] = None
    is_active: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class UserListResponse(BaseModel):
    total: int
    users: List[UserListItem]


class SubmissionHistoryItem(BaseModel):
    submission_id: UUID
    feature_type: str
    input_type: str
    file_name: Optional[str] = None
    language: str
    status: str
    created_at: datetime
    completed_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class SubmissionHistoryResponse(BaseModel):
    total: int
    page: int
    limit: int
    submissions: List[SubmissionHistoryItem]
