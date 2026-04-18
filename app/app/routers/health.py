import time

from fastapi import APIRouter
from pydantic import BaseModel

from app.config import settings
from app.database import check_db_connection

router = APIRouter(tags=["Health"])

_START_TIME = time.time()


class HealthResponse(BaseModel):
    status: str
    database: str
    uptime_seconds: int
    version: str


@router.get("/health", response_model=HealthResponse)
def health_check():
    """Public endpoint — reports API and database connectivity status."""
    db_ok = check_db_connection()
    return HealthResponse(
        status="healthy" if db_ok else "degraded",
        database="connected" if db_ok else "unreachable",
        uptime_seconds=int(time.time() - _START_TIME),
        version=settings.APP_VERSION,
    )
