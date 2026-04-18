from app.schemas.auth import (
    RegisterRequest,
    LoginRequest,
    RegisterResponse,
    LoginResponse,
    LogoutResponse,
    UserInToken,
)
from app.schemas.user import (
    UpdateProfileRequest,
    UpdateUserStatusRequest,
    UserProfileResponse,
    UserStatusResponse,
    UserListResponse,
    SubmissionHistoryResponse,
)
from app.schemas.submission import (
    AIDetectionTextRequest,
    AIDetectionResponse,
    GrammarCheckTextRequest,
    GrammarCheckResponse,
    ParaphraseTextRequest,
    ParaphraseResponse,
    PlagiarismResponse,
    SummarizeTextRequest,
    SummarizeResponse,
    PaperReviewResponse,
    SubmissionDetailResponse,
)
from app.schemas.admin import PlatformStatsResponse, LogsResponse

__all__ = [
    "RegisterRequest", "LoginRequest", "RegisterResponse", "LoginResponse",
    "LogoutResponse", "UserInToken",
    "UpdateProfileRequest", "UpdateUserStatusRequest", "UserProfileResponse",
    "UserStatusResponse", "UserListResponse", "SubmissionHistoryResponse",
    "AIDetectionTextRequest", "AIDetectionResponse",
    "GrammarCheckTextRequest", "GrammarCheckResponse",
    "ParaphraseTextRequest", "ParaphraseResponse",
    "PlagiarismResponse",
    "SummarizeTextRequest", "SummarizeResponse",
    "PaperReviewResponse",
    "SubmissionDetailResponse",
    "PlatformStatsResponse", "LogsResponse",
]
