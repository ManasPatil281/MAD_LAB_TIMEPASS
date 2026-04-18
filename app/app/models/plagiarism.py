import uuid
from sqlalchemy import Column, Integer, DateTime, ForeignKey, Numeric, func
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship

from app.database import Base


class PlagiarismResult(Base):
    __tablename__ = "plagiarism_results"

    result_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    submission_id = Column(UUID(as_uuid=True), ForeignKey("submissions.submission_id", ondelete="CASCADE"), nullable=False)
    plagiarism_score = Column(Numeric(5, 2), nullable=True)
    unique_score = Column(Numeric(5, 2), nullable=True)
    matched_sources = Column(JSONB, nullable=True)      # [{url, title, match_percent, matched_text}]
    total_words = Column(Integer, nullable=True)
    plagiarized_words = Column(Integer, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    submission = relationship("Submission", back_populates="plagiarism_result")
