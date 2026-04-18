"""
Submit router — covers all 6 ScholarMate features:
  POST /submit/ai-detection
  POST /submit/grammar-check
  POST /submit/paraphrase
  POST /submit/plagiarism
  POST /submit/summarize
  POST /submit/paper-review

Each endpoint:
  1. Creates a Submission record
  2. Persists the result to its dedicated result table
  3. Marks the submission as 'completed'
  4. Returns the structured response

NOTE: AI model calls are stubbed with realistic placeholder values.
      Swap `_run_ai_*` helpers with real model integrations (Groq/Gemini/HuggingFace).
"""

import os
import random
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from app.core.dependencies import get_current_user
from app.database import get_db
from app.models.ai_detection import AIDetectionResult
from app.models.grammar_check import GrammarCheckResult
from app.models.paper_review import PaperReviewResult
from app.models.paraphrase import ParaphraseResult
from app.models.plagiarism import PlagiarismResult
from app.models.submission import Submission
from app.models.summarization import SummarizationResult
from app.models.user import User
from app.schemas.submission import (
    AIDetectionResponse,
    AIDetectionTextRequest,
    GrammarCheckResponse,
    GrammarCheckTextRequest,
    PaperReviewResponse,
    ParaphraseResponse,
    ParaphraseTextRequest,
    PlagiarismResponse,
    SummarizeResponse,
    SummarizeTextRequest,
)

router = APIRouter(prefix="/submit", tags=["Feature Submissions"])

UPLOADS_DIR = "uploads"
os.makedirs(UPLOADS_DIR, exist_ok=True)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _save_submission(
    db: Session,
    user_id,
    feature_type: str,
    input_type: str,
    language: str = "English",
    input_text: str | None = None,
    file_name: str | None = None,
    file_path: str | None = None,
    file_size_kb: int | None = None,
) -> Submission:
    sub = Submission(
        user_id=user_id,
        feature_type=feature_type,
        input_type=input_type,
        input_text=input_text,
        file_name=file_name,
        file_path=file_path,
        file_size_kb=file_size_kb,
        language=language,
        status="processing",
    )
    db.add(sub)
    db.flush()
    return sub


def _complete_submission(db: Session, sub: Submission):
    sub.status = "completed"
    sub.completed_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(sub)


async def _save_uploaded_file(file: UploadFile, dir_path: str = UPLOADS_DIR) -> tuple[str, str, int]:
    """Save uploaded PDF, return (file_name, file_path, size_kb)."""
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Only PDF files are accepted",
        )
    content = await file.read()
    file_path = os.path.join(dir_path, file.filename)
    with open(file_path, "wb") as f:
        f.write(content)
    return file.filename, file_path, len(content) // 1024


# ── Stubs (replace with real AI calls) ───────────────────────────────────────

from app.core.ai_engine import (
    ai_detect_text,
    ai_detect_pdf,
    grammar_check_text,
    paraphrase_text,
    paraphrase_pdf,
    detect_plagiarism_pdf,
    summarize_text,
    summarize_pdf,
    review_pdf,
)

def _stub_ai_detection(text: str | None = None, file_path: str | None = None, model_type: str = "fast") -> dict:
    if file_path:
        response = ai_detect_pdf(file_path, model_type)
    else:
        response = ai_detect_text(text, model_type)
    
    # We fake some probabilities since the LLM returns a raw string
    return {
        "ai_probability": 0.0,
        "human_probability": 0.0,
        "confidence_score": 0.0,
        "verdict": response,
        "highlighted_spans": [],
        "model_used": "groq/llama-3.3-70b" if model_type == "advanced" else "groq/llama-3-8b",
    }



def _stub_grammar_check(text: str, model_type: str = "fast") -> dict:
    response = grammar_check_text(text, model_type)
    return {
        "corrected_text": response,
        "error_count": 0,
        "errors": [],
        "style_suggestions": [],
        "readability_score": 0.0,
    }



def _stub_paraphrase(text: str | None = None, file_path: str | None = None, mode: str = "standard", language: str = "English", model_type: str = "fast") -> dict:
    if file_path:
        response = paraphrase_pdf(file_path, language, model_type)
    else:
        response = paraphrase_text(text, language, model_type)
    return {
        "paraphrased_text": response,
        "similarity_score": 0.0,
    }



def _stub_plagiarism(word_count: int, file_path: str, model_type: str = "fast") -> dict:
    response = detect_plagiarism_pdf(file_path, model_type)
    return {
        "plagiarism_score": 0.0,
        "unique_score": 0.0,
        "matched_sources": [
            {
                "url": "",
                "title": "Analysis Result",
                "match_percent": 0.0,
                "matched_text": response,
            }
        ],
        "total_words": word_count,
        "plagiarized_words": 0,
    }



def _stub_summarize(text: str | None = None, file_path: str | None = None, summary_type: str = "abstractive", language: str = "English", model_type: str = "fast") -> dict:
    if file_path:
        response = summarize_pdf(file_path, language, model_type)
    else:
        response = summarize_text(text, language, model_type)
        
    return {
        "summary_text": response,
        "original_length": 0,
        "summary_length": 0,
        "compression_rate": 0.0,
        "summary_type": summary_type,
    }


def _stub_paper_review(file_path: str, model_type: str = "fast") -> dict:
    response = review_pdf(file_path, model_type)
    return {
        "overall_score": 0.0,
        "abstract_review": response,
        "methodology_review": "",
        "literature_review": "",
        "results_review": "",
        "conclusion_review": "",
        "strengths": [],
        "weaknesses": [],
        "suggestions": [],
        "citations_quality": "",
        "recommendation": "",
    }



# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/ai-detection", response_model=AIDetectionResponse, status_code=status.HTTP_201_CREATED)
async def submit_ai_detection(
    # Text input (JSON)
    text: str | None = Form(None),
    language: str = Form("English"),
    model_type: str = Form("fast"),
    # PDF input
    file: UploadFile | None = File(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):

    """Detect AI-generated content from text or a PDF upload."""
    if file:
        file_name, file_path, size_kb = await _save_uploaded_file(file)
        sub = _save_submission(
            db, current_user.user_id, "ai_detection", "pdf",
            language=language, file_name=file_name, file_path=file_path, file_size_kb=size_kb,
        )
        input_text = f"[PDF: {file_name}]"
    elif text:
        sub = _save_submission(
            db, current_user.user_id, "ai_detection", "text", language=language, input_text=text,
        )
        input_text = text
    else:
        raise HTTPException(status_code=422, detail="Provide 'text' or upload a PDF file")

    if file:
        result_data = _stub_ai_detection(text=input_text, file_path=file_path, model_type=model_type)
    else:
        result_data = _stub_ai_detection(text=input_text, model_type=model_type)

    result = AIDetectionResult(submission_id=sub.submission_id, **result_data)
    db.add(result)
    _complete_submission(db, sub)

    return AIDetectionResponse(submission_id=sub.submission_id, status=sub.status, **result_data)


@router.post("/grammar-check", response_model=GrammarCheckResponse, status_code=status.HTTP_201_CREATED)
async def submit_grammar_check(
    payload: GrammarCheckTextRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Check grammar, spelling, and writing style."""
    sub = _save_submission(
        db, current_user.user_id, "grammar_check", "text",
        language=payload.language, input_text=payload.text,
    )
    result_data = _stub_grammar_check(payload.text, model_type=payload.model_type)

    result = GrammarCheckResult(submission_id=sub.submission_id, **result_data)
    db.add(result)
    _complete_submission(db, sub)

    return GrammarCheckResponse(submission_id=sub.submission_id, status=sub.status, **result_data)


@router.post("/paraphrase", response_model=ParaphraseResponse, status_code=status.HTTP_201_CREATED)
async def submit_paraphrase(
    # Text input
    text: str | None = Form(None),
    mode: str = Form("standard"),
    language: str = Form("English"),
    # PDF input
    file: UploadFile | None = File(None),
    model_type: str = Form("fast"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):

    """Rewrite text while preserving meaning. Supports text and PDF."""
    if file:
        file_name, file_path, size_kb = await _save_uploaded_file(file)
        sub = _save_submission(
            db, current_user.user_id, "paraphrase", "pdf",
            language=language, file_name=file_name, file_path=file_path, file_size_kb=size_kb,
        )
        input_text = f"[PDF: {file_name}]"
    elif text:
        sub = _save_submission(
            db, current_user.user_id, "paraphrase", "text", language=language, input_text=text,
        )
        input_text = text
    else:
        raise HTTPException(status_code=422, detail="Provide 'text' or upload a PDF file")

    if file:
        result_data = _stub_paraphrase(file_path=file_path, mode=mode, language=language, model_type=model_type)
    else:
        result_data = _stub_paraphrase(text=input_text, mode=mode, language=language, model_type=model_type)

    result = ParaphraseResult(submission_id=sub.submission_id, mode=mode, **result_data)
    db.add(result)
    _complete_submission(db, sub)

    return ParaphraseResponse(
        submission_id=sub.submission_id, status=sub.status, mode=mode, **result_data
    )


@router.post("/plagiarism", response_model=PlagiarismResponse, status_code=status.HTTP_201_CREATED)
async def submit_plagiarism(
    file: UploadFile = File(...),
    model_type: str = Form("fast"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):

    """Detect plagiarism from an uploaded PDF (checks against Google Scholar and ArXiv)."""
    file_name, file_path, size_kb = await _save_uploaded_file(file)
    sub = _save_submission(
        db, current_user.user_id, "plagiarism", "pdf",
        file_name=file_name, file_path=file_path, file_size_kb=size_kb,
    )

    estimated_words = size_kb * 80  # rough words-per-kb estimate
    result_data = _stub_plagiarism(estimated_words, file_path, model_type=model_type)

    result = PlagiarismResult(submission_id=sub.submission_id, **result_data)
    db.add(result)
    _complete_submission(db, sub)

    return PlagiarismResponse(submission_id=sub.submission_id, status=sub.status, **result_data)


@router.post("/summarize", response_model=SummarizeResponse, status_code=status.HTTP_201_CREATED)
async def submit_summarize(
    # Text input
    text: str | None = Form(None),
    summary_type: str = Form("abstractive"),
    language: str = Form("English"),
    # PDF input
    file: UploadFile | None = File(None),
    model_type: str = Form("fast"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):

    """Generate concise summaries from text or an uploaded PDF."""
    if file:
        file_name, file_path, size_kb = await _save_uploaded_file(file)
        sub = _save_submission(
            db, current_user.user_id, "summarize", "pdf",
            language=language, file_name=file_name, file_path=file_path, file_size_kb=size_kb,
        )
        input_text = "x " * (size_kb * 80)  # rough placeholder
    elif text:
        sub = _save_submission(
            db, current_user.user_id, "summarize", "text", language=language, input_text=text,
        )
        input_text = text
    else:
        raise HTTPException(status_code=422, detail="Provide 'text' or upload a PDF file")

    if file:
        result_data = _stub_summarize(file_path=file_path, summary_type=summary_type, language=language, model_type=model_type)
    else:
        result_data = _stub_summarize(text=input_text, summary_type=summary_type, language=language, model_type=model_type)

    result = SummarizationResult(submission_id=sub.submission_id, **result_data)
    db.add(result)
    _complete_submission(db, sub)

    return SummarizeResponse(submission_id=sub.submission_id, status=sub.status, **result_data)


@router.post("/paper-review", response_model=PaperReviewResponse, status_code=status.HTTP_201_CREATED)
async def submit_paper_review(
    file: UploadFile = File(...),
    model_type: str = Form("fast"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):

    """Comprehensive academic review of a research paper PDF."""
    file_name, file_path, size_kb = await _save_uploaded_file(file)
    sub = _save_submission(
        db, current_user.user_id, "paper_review", "pdf",
        file_name=file_name, file_path=file_path, file_size_kb=size_kb,
    )

    result_data = _stub_paper_review(file_path, model_type=model_type)

    result = PaperReviewResult(submission_id=sub.submission_id, **result_data)
    db.add(result)
    _complete_submission(db, sub)

    return PaperReviewResponse(submission_id=sub.submission_id, status=sub.status, **result_data)
