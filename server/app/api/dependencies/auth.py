from fastapi import Depends, Header, HTTPException, status

from app.core.config import Settings, get_settings
from app.db.supabase import get_supabase


async def get_current_user_id(
    authorization: str | None = Header(None),
    x_dev_user_id: str | None = Header(None),
    settings: Settings = Depends(get_settings),
) -> str:
    """Supabase JWT에서 user_id를 추출한다.

    개발 편의를 위해 allow_dev_user_header=True이고
    X-Dev-User-Id 헤더가 있으면 해당 값을 그대로 반환한다.
    """
    # 개발 모드: X-Dev-User-Id 헤더 허용
    if settings.allow_dev_user_header and x_dev_user_id:
        return x_dev_user_id

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization 헤더가 필요합니다.",
        )

    token = authorization.removeprefix("Bearer ")

    try:
        sb = get_supabase()
        user = sb.auth.get_user(token)
        return user.user.id
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="유효하지 않은 토큰입니다.",
        )
