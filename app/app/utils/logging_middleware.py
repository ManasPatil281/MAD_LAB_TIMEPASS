"""
Middleware that records every API request into the usage_logs table.
Captures: endpoint, method, status_code, response_time_ms, ip_address,
          feature_type (derived from the path), and the authenticated user (if any).
"""

import time
import re

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from sqlalchemy.orm import Session

from app.database import SessionLocal
from app.models.usage_log import UsageLog
from app.core.security import decode_access_token

# Map URL path fragments → feature_type labels
_FEATURE_MAP = {
    "ai-detection": "ai_detection",
    "grammar-check": "grammar_check",
    "paraphrase": "paraphrase",
    "plagiarism": "plagiarism",
    "summarize": "summarize",
    "paper-review": "paper_review",
}


def _extract_feature(path: str) -> str | None:
    for key, label in _FEATURE_MAP.items():
        if key in path:
            return label
    return None


def _extract_user_id(request: Request) -> str | None:
    auth = request.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        token = auth.split(" ", 1)[1]
        payload = decode_access_token(token)
        if payload:
            return payload.get("sub")
    return None


class UsageLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        start = time.time()
        response: Response = await call_next(request)
        elapsed_ms = int((time.time() - start) * 1000)

        # Fire-and-forget DB write — don't block the response
        try:
            db: Session = SessionLocal()
            log = UsageLog(
                user_id=_extract_user_id(request),
                endpoint=request.url.path,
                method=request.method,
                feature_type=_extract_feature(request.url.path),
                response_time_ms=elapsed_ms,
                status_code=response.status_code,
                ip_address=request.client.host if request.client else None,
            )
            db.add(log)
            db.commit()
        except Exception:
            pass  # Never let logging break the response
        finally:
            db.close()

        return response
