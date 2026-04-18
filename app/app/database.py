from sqlalchemy import create_engine, text
from sqlalchemy.engine import URL
from sqlalchemy.orm import sessionmaker, declarative_base

from app.config import settings

# Build the URL object so special characters (e.g. @ in password) are handled safely
_db_url = URL.create(
    drivername="postgresql+psycopg2",
    host="aws-1-ap-northeast-1.pooler.supabase.com",
    port=5432,
    database="postgres",
    username="postgres.hunuuzvaathdmfdofcgm",
    password="Manas@2810567",
    query={"sslmode": "require"},
)

# psycopg2 sync engine (used for table creation and all ORM operations)
engine = create_engine(
    _db_url,
    pool_pre_ping=True,
    pool_size=10,
    max_overflow=20,
    echo=settings.DEBUG,
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    """FastAPI dependency — yields a DB session and closes it after the request."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def create_tables():
    """Create all tables defined in the ORM models (safe — won't overwrite existing)."""
    # Import all models so SQLAlchemy registers them before calling create_all
    from app.models import (  # noqa: F401
        user,
        session,
        submission,
        ai_detection,
        grammar_check,
        paraphrase,
        plagiarism,
        summarization,
        paper_review,
        usage_log,
    )
    Base.metadata.create_all(bind=engine)


def check_db_connection() -> bool:
    """Returns True if the database is reachable."""
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception:
        return False
