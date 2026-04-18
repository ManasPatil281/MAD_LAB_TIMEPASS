from datetime import datetime, timezone

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.security import decode_access_token, hash_token
from app.database import get_db
from app.models.session import Session as DBSession
from app.models.user import User

bearer_scheme = HTTPBearer()


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    """
    Validate JWT, check session is not revoked, and return the User ORM object.
    Raises 401 on any failure.
    """
    token = credentials.credentials
    payload = decode_access_token(token)

    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if payload is None:
        raise credentials_exception

    user_id: str = payload.get("sub")
    if user_id is None:
        raise credentials_exception

    # Check session is active and not revoked
    token_hash = hash_token(token)
    session = (
        db.query(DBSession)
        .filter(
            DBSession.token_hash == token_hash,
            DBSession.is_revoked == False,  # noqa: E712
            DBSession.expires_at > datetime.now(timezone.utc),
        )
        .first()
    )
    if session is None:
        raise credentials_exception

    user = db.query(User).filter(User.user_id == user_id).first()
    if user is None or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found or deactivated",
        )
    return user


def require_admin(current_user: User = Depends(get_current_user)) -> User:
    """Restrict endpoint to admin users only."""
    if current_user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin access required",
        )
    return current_user
