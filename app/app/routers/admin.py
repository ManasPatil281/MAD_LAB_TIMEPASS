from datetime import datetime, timezone, timedelta
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.core.dependencies import require_admin
from app.database import get_db
from app.models.submission import Submission
from app.models.usage_log import UsageLog
from app.models.user import User
from app.schemas.admin import (
    LogEntry,
    LogsResponse,
    PlatformStatsResponse,
    SubmissionsByFeature,
)

router = APIRouter(prefix="/admin", tags=["Admin"])


@router.get("/stats", response_model=PlatformStatsResponse)
def get_platform_stats(
    db: Session = Depends(get_db),
    _admin: User = Depends(require_admin),
):
    """(Admin only) Platform-wide usage statistics."""
    total_users = db.query(func.count(User.user_id)).scalar()

    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    active_users_today = (
        db.query(func.count(func.distinct(UsageLog.user_id)))
        .filter(UsageLog.created_at >= today_start)
        .scalar()
    )

    total_submissions = db.query(func.count(Submission.submission_id)).scalar()

    # Submissions per feature
    feature_counts = (
        db.query(Submission.feature_type, func.count(Submission.submission_id))
        .group_by(Submission.feature_type)
        .all()
    )
    feature_map = {ft: cnt for ft, cnt in feature_counts}

    subs_by_feature = SubmissionsByFeature(
        ai_detection=feature_map.get("ai_detection", 0),
        grammar_check=feature_map.get("grammar_check", 0),
        paraphrase=feature_map.get("paraphrase", 0),
        plagiarism=feature_map.get("plagiarism", 0),
        summarize=feature_map.get("summarize", 0),
        paper_review=feature_map.get("paper_review", 0),
    )

    avg_response_time = (
        db.query(func.avg(UsageLog.response_time_ms))
        .filter(UsageLog.response_time_ms.isnot(None))
        .scalar()
    )

    return PlatformStatsResponse(
        total_users=total_users or 0,
        active_users_today=active_users_today or 0,
        total_submissions=total_submissions or 0,
        submissions_by_feature=subs_by_feature,
        avg_response_time_ms=float(avg_response_time) if avg_response_time else None,
    )


@router.get("/logs", response_model=LogsResponse)
def get_logs(
    user_id: Optional[UUID] = Query(None),
    status_code: Optional[int] = Query(None),
    from_date: Optional[datetime] = Query(None, alias="from"),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    _admin: User = Depends(require_admin),
):
    """(Admin only) Paginated usage and error logs."""
    query = db.query(UsageLog)

    if user_id:
        query = query.filter(UsageLog.user_id == user_id)
    if status_code:
        query = query.filter(UsageLog.status_code == status_code)
    if from_date:
        query = query.filter(UsageLog.created_at >= from_date)

    total = query.count()
    logs = query.order_by(UsageLog.created_at.desc()).offset((page - 1) * limit).limit(limit).all()

    return LogsResponse(
        total=total,
        page=page,
        limit=limit,
        logs=[LogEntry.model_validate(log) for log in logs],
    )
