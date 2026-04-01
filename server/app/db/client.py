from functools import lru_cache

from supabase import Client, create_client

from app.core.config import get_settings


def _build_client(url: str | None, key: str | None) -> Client:
    if not url or not key:
        raise RuntimeError("Supabase 설정이 누락되었습니다. server/.env 값을 확인하세요.")
    return create_client(url, key)


@lru_cache
def get_supabase_anon_client() -> Client:
    settings = get_settings()
    return _build_client(settings.supabase_url, settings.supabase_anon_key)


@lru_cache
def get_supabase_service_client() -> Client:
    settings = get_settings()
    return _build_client(settings.supabase_url, settings.supabase_service_role_key)

