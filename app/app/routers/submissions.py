from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.dependencies import get_current_user
from app.database import get_db
from app.models.submission import Submission
from app.models.user import User
from app.schemas.submission import SubmissionDetailResponse

router = APIRouter(prefix="/submissions", tags=["Submissions"])


def _extract_result(submission: Submission) -> dict | None:
    """Pull the single result record for the given submission and serialise to dict."""
    feature = submission.feature_type
    result_obj = None

    if feature == "ai_detection":
        result_obj = submission.ai_detection_result
    elif feature == "grammar_check":
        result_obj = submission.grammar_check_result
    elif feature == "paraphrase":
        result_obj = submission.paraphrase_result
    elif feature == "plagiarism":
        result_obj = submission.plagiarism_result
    elif feature == "summarize":
        result_obj = submission.summarization_result
    elif feature == "paper_review":
        result_obj = submission.paper_review_result

    if result_obj is None:
        return None

    # Convert ORM object to dict, filtering out SQLAlchemy internals
    return {
        k: v
        for k, v in result_obj.__dict__.items()
        if not k.startswith("_")
    }


@router.get("/{submission_id}", response_model=SubmissionDetailResponse)
def get_submission(
    submission_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Retrieve a specific submission and its result.
    - Researchers can only access their own submissions.
    - Admins can access any submission.
    """
    submission = db.query(Submission).filter(Submission.submission_id == submission_id).first()

    if not submission:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Submission not found")

    # Ownership check for non-admins
    if current_user.role != "admin" and submission.user_id != current_user.user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

    return SubmissionDetailResponse(
        submission_id=submission.submission_id,
        feature_type=submission.feature_type,
        input_type=submission.input_type,
        language=submission.language,
        status=submission.status,
        created_at=submission.created_at,
        completed_at=submission.completed_at,
        result=_extract_result(submission),
    )
