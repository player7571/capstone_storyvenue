from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.sessions import SessionCreateRequest, SessionResponse
from app.db.supabase import get_supabase

router = APIRouter(prefix="/sessions", tags=["sessions"])


@router.post("", response_model=SessionResponse, status_code=status.HTTP_201_CREATED)
async def create_session(
    body: SessionCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()

    try:
        result = (
            sb.table("interview_sessions")
            .insert(
                {
                    "user_id": user_id,
                    "title": body.title,
                    "theme": body.theme,
                }
            )
            .execute()
        )
        if not result.data:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="세션 생성에 실패했습니다.",
            )
        return SessionResponse(**result.data[0])
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 생성 중 오류가 발생했습니다.",
        ) from exc


@router.get("", response_model=list[SessionResponse])
async def list_sessions(
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()

    try:
        result = (
            sb.table("interview_sessions")
            .select("*")
            .eq("user_id", user_id)
            .order("created_at", desc=True)
            .execute()
        )
        return [SessionResponse(**row) for row in (result.data or [])]
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 목록 조회 중 오류가 발생했습니다.",
        ) from exc


@router.get("/{session_id}", response_model=SessionResponse)
async def get_session_detail(
    session_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()

    try:
        result = (
            sb.table("interview_sessions")
            .select("*")
            .eq("id", str(session_id))
            .eq("user_id", user_id)
            .maybe_single()
            .execute()
        )
        if not result.data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="세션을 찾을 수 없습니다.",
            )
        return SessionResponse(**result.data)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 상세 조회 중 오류가 발생했습니다.",
        ) from exc
