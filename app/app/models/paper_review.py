import uuid
from sqlalchemy import Column, String, DateTime, ForeignKey, Numeric, Text, func
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship

from app.database import Base


class PaperReviewResult(Base):
    __tablename__ = "paper_review_results"

    result_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    submission_id = Column(UUID(as_uuid=True), ForeignKey("submissions.submission_id", ondelete="CASCADE"), nullable=False)
    overall_score = Column(Numeric(4, 2), nullable=True)
    abstract_review = Column(Text, nullable=True)
    methodology_review = Column(Text, nullable=True)
    literature_review = Column(Text, nullable=True)
    results_review = Column(Text, nullable=True)
    conclusion_review = Column(Text, nullable=True)
    strengths = Column(JSONB, nullable=True)
    weaknesses = Column(JSONB, nullable=True)
    suggestions = Column(JSONB, nullable=True)
    citations_quality = Column(String(30), nullable=True)   # 'Poor' | 'Fair' | 'Good' | 'Excellent'
    recommendation = Column(String(50), nullable=True)      # 'Accept' | 'Major Revision' | 'Minor Revision' | 'Reject'
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    submission = relationship("Submission", back_populates="paper_review_result")
