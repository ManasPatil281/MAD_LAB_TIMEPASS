from datetime import datetime
from typing import Any, Dict, List, Optional
from uuid import UUID

from pydantic import BaseModel


# ── Shared ───────────────────────────────────────────────────────────────────

class SubmissionBase(BaseModel):
    language: str = "English"
    model_type: str = "fast"  # 'fast' (llama3-8b) | 'advanced' (llama3-70b/gpt)


# ── AI Detection ──────────────────────────────────────────────────────────────

class AIDetectionTextRequest(SubmissionBase):
    text: str


class AIDetectionResponse(BaseModel):
    submission_id: UUID
    status: str
    ai_probability: Optional[float] = None
    human_probability: Optional[float] = None
    confidence_score: Optional[float] = None
    verdict: Optional[str] = None
    highlighted_spans: Optional[List[Dict[str, Any]]] = None
    model_used: Optional[str] = None


# ── Grammar Check ─────────────────────────────────────────────────────────────

class GrammarCheckTextRequest(SubmissionBase):
    text: str


class GrammarCheckResponse(BaseModel):
    submission_id: UUID
    status: str
    corrected_text: Optional[str] = None
    error_count: Optional[int] = None
    errors: Optional[List[Dict[str, Any]]] = None
    style_suggestions: Optional[List[str]] = None
    readability_score: Optional[float] = None


# ── Paraphrase ────────────────────────────────────────────────────────────────

class ParaphraseTextRequest(SubmissionBase):
    text: str
    mode: str = "standard"   # 'standard' | 'formal' | 'creative'


class ParaphraseResponse(BaseModel):
    submission_id: UUID
    status: str
    paraphrased_text: Optional[str] = None
    similarity_score: Optional[float] = None
    mode: Optional[str] = None


# ── Plagiarism ────────────────────────────────────────────────────────────────

class PlagiarismResponse(BaseModel):
    submission_id: UUID
    status: str
    plagiarism_score: Optional[float] = None
    unique_score: Optional[float] = None
    matched_sources: Optional[List[Dict[str, Any]]] = None
    total_words: Optional[int] = None
    plagiarized_words: Optional[int] = None


# ── Summarization ─────────────────────────────────────────────────────────────

class SummarizeTextRequest(SubmissionBase):
    text: str
    summary_type: str = "abstractive"   # 'abstractive' | 'extractive'


class SummarizeResponse(BaseModel):
    submission_id: UUID
    status: str
    summary_text: Optional[str] = None
    original_length: Optional[int] = None
    summary_length: Optional[int] = None
    compression_rate: Optional[float] = None
    summary_type: Optional[str] = None


# ── Paper Review ──────────────────────────────────────────────────────────────

class PaperReviewResponse(BaseModel):
    submission_id: UUID
    status: str
    overall_score: Optional[float] = None
    abstract_review: Optional[str] = None
    methodology_review: Optional[str] = None
    literature_review: Optional[str] = None
    results_review: Optional[str] = None
    conclusion_review: Optional[str] = None
    strengths: Optional[List[str]] = None
    weaknesses: Optional[List[str]] = None
    suggestions: Optional[List[str]] = None
    citations_quality: Optional[str] = None
    recommendation: Optional[str] = None


# ── Generic Submission Retrieval ──────────────────────────────────────────────

class SubmissionDetailResponse(BaseModel):
    submission_id: UUID
    feature_type: str
    input_type: str
    language: str
    status: str
    created_at: datetime
    completed_at: Optional[datetime] = None
    result: Optional[Dict[str, Any]] = None
