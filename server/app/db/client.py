from functools import lru_cache

from supabase import Client, create_client

from app.core.config import get_settings


def _require_env_value(value: str, env_name: str) -> str:
    if not value.strip():
        raise RuntimeError(f"{env_name} 값이 비어 있습니다. server/.env를 확인하세요.")
    return value


def _build_client(key: str, key_name: str) -> Client:
    settings = get_settings()
    supabase_url = _require_env_value(settings.supabase_url, "SUPABASE_URL")
    resolved_key = _require_env_value(key, key_name)
    return create_client(supabase_url, resolved_key)


@lru_cache
def get_supabase_anon_client() -> Client:
    settings = get_settings()
    return _build_client(settings.supabase_anon_key, "SUPABASE_ANON_KEY")


@lru_cache
def get_supabase_service_client() -> Client:
    settings = get_settings()
    return _build_client(settings.supabase_service_role_key, "SUPABASE_SERVICE_ROLE_KEY")
