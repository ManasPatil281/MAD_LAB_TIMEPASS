import uuid
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.database import Base


class Submission(Base):
    __tablename__ = "submissions"

    submission_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.user_id", ondelete="SET NULL"), nullable=True)
    feature_type = Column(String(50), nullable=False)
    input_type = Column(String(10), nullable=False)  # 'text' | 'pdf'
    input_text = Column(Text, nullable=True)
    file_name = Column(String(255), nullable=True)
    file_path = Column(Text, nullable=True)
    file_size_kb = Column(Integer, nullable=True)
    language = Column(String(30), default="English")
    status = Column(String(20), default="pending")  # pending | processing | completed | failed
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    completed_at = Column(DateTime(timezone=True), nullable=True)

    # Relationships
    user = relationship("User", back_populates="submissions")
    ai_detection_result = relationship("AIDetectionResult", back_populates="submission", uselist=False, cascade="all, delete-orphan")
    grammar_check_result = relationship("GrammarCheckResult", back_populates="submission", uselist=False, cascade="all, delete-orphan")
    paraphrase_result = relationship("ParaphraseResult", back_populates="submission", uselist=False, cascade="all, delete-orphan")
    plagiarism_result = relationship("PlagiarismResult", back_populates="submission", uselist=False, cascade="all, delete-orphan")
    summarization_result = relationship("SummarizationResult", back_populates="submission", uselist=False, cascade="all, delete-orphan")
    paper_review_result = relationship("PaperReviewResult", back_populates="submission", uselist=False, cascade="all, delete-orphan")
