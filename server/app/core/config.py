from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    app_env: str = Field(default="local", validation_alias="APP_ENV")
    supabase_url: str = Field(validation_alias="SUPABASE_URL")
    supabase_anon_key: str = Field(validation_alias="SUPABASE_ANON_KEY")
    supabase_service_role_key: str = Field(validation_alias="SUPABASE_SERVICE_ROLE_KEY")
    kakao_rest_api_key: str | None = Field(default=None, validation_alias="KAKAO_REST_API_KEY")
    kakao_client_secret: str | None = Field(default=None, validation_alias="KAKAO_CLIENT_SECRET")
    kakao_redirect_uri: str | None = Field(default=None, validation_alias="KAKAO_REDIRECT_URI")
    kakao_allow_dev_login: bool = Field(default=False, validation_alias="KAKAO_ALLOW_DEV_LOGIN")
    auth_shadow_password_pepper: str | None = Field(default=None, validation_alias="AUTH_SHADOW_PASSWORD_PEPPER")
    auth_refresh_token_pepper: str | None = Field(default=None, validation_alias="AUTH_REFRESH_TOKEN_PEPPER")
    allow_dev_user_header: bool = Field(default=True, validation_alias="ALLOW_DEV_USER_HEADER")
    openai_api_key: str | None = Field(default=None, validation_alias="OPENAI_API_KEY")
    openai_stt_model: str = Field(default="gpt-4o-transcribe", validation_alias="OPENAI_STT_MODEL")
    openai_stt_language: str = Field(default="ko", validation_alias="OPENAI_STT_LANGUAGE")
    openai_stt_prompt: str | None = Field(default=None, validation_alias="OPENAI_STT_PROMPT")
    openai_safety_model: str = Field(default="gpt-4.1-mini", validation_alias="OPENAI_SAFETY_MODEL")
    openai_safety_prompt: str | None = Field(default=None, validation_alias="OPENAI_SAFETY_PROMPT")

    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
