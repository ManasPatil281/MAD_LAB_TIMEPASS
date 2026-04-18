import uuid
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Numeric, Text, func
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship

from app.database import Base


class GrammarCheckResult(Base):
    __tablename__ = "grammar_check_results"

    result_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    submission_id = Column(UUID(as_uuid=True), ForeignKey("submissions.submission_id", ondelete="CASCADE"), nullable=False)
    corrected_text = Column(Text, nullable=True)
    error_count = Column(Integer, default=0)
    errors = Column(JSONB, nullable=True)               # [{type, original, suggestion, position}]
    style_suggestions = Column(JSONB, nullable=True)
    readability_score = Column(Numeric(4, 2), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    submission = relationship("Submission", back_populates="grammar_check_result")
