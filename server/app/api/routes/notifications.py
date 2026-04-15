from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.notifications import NotificationResponse, UnreadCountResponse
from app.db.supabase import get_supabase

router = APIRouter(prefix="/notifications", tags=["notifications"])


# ── GET /notifications ───────────────────────────
@router.get("", response_model=list[NotificationResponse])
async def list_notifications(
    limit: int = Query(30, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user_id: str = Depends(get_current_user_id),
):
    """내 알림 목록을 최신순으로 반환한다. actor 이름과 댓글 미리보기를 포함."""
    sb = get_supabase()
    result = (
        sb.table("notifications")
        .select("*, profiles!notifications_actor_id_fkey(name)")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .range(offset, offset + limit - 1)
        .execute()
    )

    notifications = []
    for row in result.data:
        profile = row.pop("profiles", None)
        actor_name = profile.get("name") if profile else None

        # 댓글 알림이면 댓글 내용 미리보기 포함
        comment_preview = None
        if row.get("type") == "comment" and row.get("comment_id"):
            comment = (
                sb.table("feed_comments")
                .select("content")
                .eq("id", row["comment_id"])
                .maybe_single()
                .execute()
            )
            if comment.data:
                comment_preview = comment.data["content"]

        notifications.append(
            NotificationResponse(
                id=row["id"],
                type=row["type"],
                actor_name=actor_name,
                post_id=row.get("post_id"),
                message=row["message"],
                comment_preview=comment_preview,
                is_read=row["is_read"],
                created_at=row["created_at"],
            )
        )
    return notifications


# ── GET /notifications/unread-count ──────────────
@router.get("/unread-count", response_model=UnreadCountResponse)
async def unread_count(
    user_id: str = Depends(get_current_user_id),
):
    """읽지 않은 알림 수를 반환한다."""
    sb = get_supabase()
    result = (
        sb.table("notifications")
        .select("id", count="exact")
        .eq("user_id", user_id)
        .eq("is_read", False)
        .execute()
    )
    return UnreadCountResponse(count=result.count or 0)


# ── PUT /notifications/{notification_id}/read ────
@router.put("/{notification_id}/read")
async def mark_as_read(
    notification_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    """해당 알림을 읽음 처리한다."""
    sb = get_supabase()
    noti_id_str = str(notification_id)

    # 알림 존재 및 소유자 확인
    noti = (
        sb.table("notifications")
        .select("id, user_id")
        .eq("id", noti_id_str)
        .maybe_single()
        .execute()
    )
    if not noti.data:
        raise HTTPException(status_code=404, detail="알림을 찾을 수 없습니다.")
    if noti.data["user_id"] != user_id:
        raise HTTPException(status_code=403, detail="본인의 알림이 아닙니다.")

    sb.table("notifications").update({"is_read": True}).eq("id", noti_id_str).execute()
    return {"message": "읽음 처리 완료"}
