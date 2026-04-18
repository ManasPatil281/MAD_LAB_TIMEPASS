from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.dependencies import get_current_user, require_admin
from app.database import get_db
from app.models.submission import Submission
from app.models.user import User
from app.schemas.user import (
    SubmissionHistoryResponse,
    SubmissionHistoryItem,
    UpdateProfileRequest,
    UpdateUserStatusRequest,
    UserListItem,
    UserListResponse,
    UserProfileResponse,
    UserStatusResponse,
)

router = APIRouter(prefix="/users", tags=["Users"])


@router.get("/me", response_model=UserProfileResponse)
def get_my_profile(current_user: User = Depends(get_current_user)):
    """Get the authenticated user's profile."""
    return current_user


@router.put("/me", response_model=UserProfileResponse)
def update_my_profile(
    payload: UpdateProfileRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update the authenticated user's editable profile fields."""
    if payload.full_name is not None:
        current_user.full_name = payload.full_name
    if payload.institution is not None:
        current_user.institution = payload.institution
    current_user.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(current_user)
    return current_user


@router.get("/me/history", response_model=SubmissionHistoryResponse)
def get_my_history(
    feature_type: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(10, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Paginated submission history for the authenticated user."""
    query = db.query(Submission).filter(Submission.user_id == current_user.user_id)
    if feature_type:
        query = query.filter(Submission.feature_type == feature_type)

    total = query.count()
    items = query.order_by(Submission.created_at.desc()).offset((page - 1) * limit).limit(limit).all()

    return SubmissionHistoryResponse(
        total=total,
        page=page,
        limit=limit,
        submissions=[SubmissionHistoryItem.model_validate(s) for s in items],
    )


# ── Admin-only ────────────────────────────────────────────────────────────────

@router.get("", response_model=UserListResponse)
def list_users(
    role: Optional[str] = Query(None),
    is_active: Optional[bool] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    _admin: User = Depends(require_admin),
):
    """(Admin only) List all registered users with optional filters."""
    query = db.query(User)
    if role:
        query = query.filter(User.role == role)
    if is_active is not None:
        query = query.filter(User.is_active == is_active)

    total = query.count()
    users = query.order_by(User.created_at.desc()).offset((page - 1) * limit).limit(limit).all()

    return UserListResponse(
        total=total,
        users=[UserListItem.model_validate(u) for u in users],
    )


@router.patch("/{user_id}/status", response_model=UserStatusResponse)
def update_user_status(
    user_id: UUID,
    payload: UpdateUserStatusRequest,
    db: Session = Depends(get_db),
    _admin: User = Depends(require_admin),
):
    """(Admin only) Activate or deactivate a user account."""
    user = db.query(User).filter(User.user_id == user_id).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    user.is_active = payload.is_active
    user.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(user)
    return user
