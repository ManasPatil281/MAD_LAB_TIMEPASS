from datetime import datetime
from typing import Any, Dict, List, Optional
from uuid import UUID

from pydantic import BaseModel


class SubmissionsByFeature(BaseModel):
    ai_detection: int = 0
    grammar_check: int = 0
    paraphrase: int = 0
    plagiarism: int = 0
    summarize: int = 0
    paper_review: int = 0


class PlatformStatsResponse(BaseModel):
    total_users: int
    active_users_today: int
    total_submissions: int
    submissions_by_feature: SubmissionsByFeature
    avg_response_time_ms: Optional[float] = None


class LogEntry(BaseModel):
    log_id: UUID
    user_id: Optional[UUID] = None
    endpoint: str
    method: str
    feature_type: Optional[str] = None
    response_time_ms: Optional[int] = None
    status_code: Optional[int] = None
    error_message: Optional[str] = None
    ip_address: Optional[str] = None
    created_at: datetime

    model_config = {"from_attributes": True}


class LogsResponse(BaseModel):
    total: int
    page: int
    limit: int
    logs: List[LogEntry]
