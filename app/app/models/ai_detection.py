import uuid
from sqlalchemy import Column, String, DateTime, ForeignKey, Numeric, Text, func
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship

from app.database import Base


class AIDetectionResult(Base):
    __tablename__ = "ai_detection_results"

    result_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    submission_id = Column(UUID(as_uuid=True), ForeignKey("submissions.submission_id", ondelete="CASCADE"), nullable=False)
    ai_probability = Column(Numeric(5, 2), nullable=True)
    human_probability = Column(Numeric(5, 2), nullable=True)
    confidence_score = Column(Numeric(5, 2), nullable=True)
    verdict = Column(String(30), nullable=True)           # 'AI-generated' | 'Human-written' | 'Mixed'
    highlighted_spans = Column(JSONB, nullable=True)      # [{start, end, score}]
    model_used = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    submission = relationship("Submission", back_populates="ai_detection_result")
