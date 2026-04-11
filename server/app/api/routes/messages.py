from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.messages import SessionMessageResponse
from app.db.supabase import get_supabase

router = APIRouter(prefix="/messages", tags=["messages"])


def _get_session_or_404(session_id: UUID, user_id: str) -> dict:
    result = (
        get_supabase()
        .table("interview_sessions")
        .select("id, user_id")
        .eq("id", str(session_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="인터뷰 세션을 찾을 수 없습니다.",
        )
    return result.data


@router.get("", response_model=list[SessionMessageResponse])
async def list_messages(
    session_id: UUID = Query(...),
    user_id: str = Depends(get_current_user_id),
):
    _get_session_or_404(session_id, user_id)

    result = (
        get_supabase()
        .table("session_messages")
        .select("id, role, content, created_at")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .execute()
    )

    return [SessionMessageResponse(**row) for row in (result.data or [])]
