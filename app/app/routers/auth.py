from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.config import settings
from app.core.dependencies import get_current_user, bearer_scheme
from app.core.security import (
    create_access_token,
    hash_password,
    hash_token,
    verify_password,
)
from app.database import get_db
from app.models.session import Session as DBSession
from app.models.user import User
from app.schemas.auth import (
    LoginRequest,
    LoginResponse,
    LogoutResponse,
    RegisterRequest,
    RegisterResponse,
    UserInToken,
)

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/register", response_model=RegisterResponse, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterRequest, request: Request, db: Session = Depends(get_db)):
    """Register a new user account and return a JWT token."""
    # Check duplicate email
    if db.query(User).filter(User.email == payload.email).first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Email already registered",
        )

    user = User(
        full_name=payload.full_name,
        email=payload.email,
        password_hash=hash_password(payload.password),
        role=payload.role,
        institution=payload.institution,
    )
    db.add(user)
    db.flush()  # get user_id before commit

    # Create session / token
    token, expires_at = create_access_token(
        data={"sub": str(user.user_id), "role": user.role},
        expires_delta=timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES),
    )
    session = DBSession(
        user_id=user.user_id,
        token_hash=hash_token(token),
        ip_address=request.client.host if request.client else None,
        user_agent=request.headers.get("user-agent"),
        expires_at=expires_at,
    )
    db.add(session)
    db.commit()

    return RegisterResponse(user_id=user.user_id, email=user.email, role=user.role, token=token)


@router.post("/login", response_model=LoginResponse)
def login(payload: LoginRequest, request: Request, db: Session = Depends(get_db)):
    """Login and receive a JWT access token."""
    user = db.query(User).filter(User.email == payload.email).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
        )
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is deactivated",
        )

    token, expires_at = create_access_token(
        data={"sub": str(user.user_id), "role": user.role},
    )
    session = DBSession(
        user_id=user.user_id,
        token_hash=hash_token(token),
        ip_address=request.client.host if request.client else None,
        user_agent=request.headers.get("user-agent"),
        expires_at=expires_at,
    )
    db.add(session)
    db.commit()

    return LoginResponse(
        token=token,
        expires_at=expires_at,
        user=UserInToken.model_validate(user),
    )


@router.post("/logout", response_model=LogoutResponse)
def logout(
    credentials=Depends(bearer_scheme),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Revoke current session token."""
    token_hash = hash_token(credentials.credentials)
    session = db.query(DBSession).filter(DBSession.token_hash == token_hash).first()
    if session:
        session.is_revoked = True
        db.commit()
    return LogoutResponse(message="Logged out successfully")
