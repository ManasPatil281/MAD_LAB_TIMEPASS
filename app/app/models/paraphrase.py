import uuid
from sqlalchemy import Column, String, DateTime, ForeignKey, Numeric, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.database import Base


class ParaphraseResult(Base):
    __tablename__ = "paraphrase_results"

    result_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    submission_id = Column(UUID(as_uuid=True), ForeignKey("submissions.submission_id", ondelete="CASCADE"), nullable=False)
    paraphrased_text = Column(Text, nullable=True)
    similarity_score = Column(Numeric(5, 2), nullable=True)
    mode = Column(String(30), default="standard")   # 'standard' | 'formal' | 'creative'
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    submission = relationship("Submission", back_populates="paraphrase_result")
