import uuid
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Numeric, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.database import Base


class SummarizationResult(Base):
    __tablename__ = "summarization_results"

    result_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    submission_id = Column(UUID(as_uuid=True), ForeignKey("submissions.submission_id", ondelete="CASCADE"), nullable=False)
    summary_text = Column(Text, nullable=True)
    original_length = Column(Integer, nullable=True)
    summary_length = Column(Integer, nullable=True)
    compression_rate = Column(Numeric(5, 2), nullable=True)
    summary_type = Column(String(20), default="abstractive")    # 'abstractive' | 'extractive'
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    submission = relationship("Submission", back_populates="summarization_result")
