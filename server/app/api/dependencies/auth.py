from uuid import UUID

from fastapi import Header, HTTPException, status

from app.db.client import get_supabase_anon_client


def _extract_bearer_token(authorization: str | None) -> str:
    if not authorization:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization 헤더가 필요합니다.",
        )

    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization 형식은 'Bearer <토큰>' 이어야 합니다.",
        )

    return token.strip()


async def get_current_user_id(
    authorization: str | None = Header(None),
) -> str:
    """Authorization Bearer 토큰을 Supabase로 검증해 user_id(UUID)를 반환한다."""
    token = _extract_bearer_token(authorization)

    try:
        supabase = get_supabase_anon_client()
        response = supabase.auth.get_user(token)
        user = getattr(response, "user", None)
        user_id = getattr(user, "id", None)

        if not user_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="유효하지 않은 토큰입니다.",
            )

        # UUID 형식이 아닌 경우 인증 실패로 처리
        validated_user_id = UUID(str(user_id))
        return str(validated_user_id)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="유효하지 않은 토큰입니다.",
        ) from exc
