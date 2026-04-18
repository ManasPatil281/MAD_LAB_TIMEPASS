from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Database
    DATABASE_URL: str = (
        "postgresql://postgres.hunuuzvaathdmfdofcgm:Manas@2810567"
        "@aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres"
    )

    # JWT
    SECRET_KEY: str = "scholarmate-super-secret-key-change-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 hours

    # App
    APP_NAME: str = "ScholarMate"
    APP_VERSION: str = "1.0.0"
    BASE_URL: str = "http://127.0.0.5:8005/api/v1"
    DEBUG: bool = False

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
