from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.chat import (
    ChatPartnerResponse,
    MessageCreateRequest,
    MessageResponse,
)
from app.db.supabase import get_supabase
from app.services.safety import check_content_safety

router = APIRouter(prefix="/chat", tags=["chat"])


# ── GET /chat ─────────────────────────────────────
@router.get("", response_model=list[ChatPartnerResponse])
async def list_chat_partners(
    user_id: str = Depends(get_current_user_id),
):
    """나와 대화한 상대 목록을 반환한다. 마지막 메시지 시간 기준 내림차순."""
    sb = get_supabase()

    # 내가 보냈거나 받은 메시지에서 상대방 ID 추출
    sent = (
        sb.table("chat_messages")
        .select("receiver_id, content, created_at")
        .eq("sender_id", user_id)
        .order("created_at", desc=True)
        .execute()
    )
    received = (
        sb.table("chat_messages")
        .select("sender_id, content, created_at, is_read")
        .eq("receiver_id", user_id)
        .order("created_at", desc=True)
        .execute()
    )

    # 상대방별 마지막 메시지·안 읽은 수 집계
    partners: dict[str, dict] = {}

    for row in sent.data:
        pid = row["receiver_id"]
        if pid not in partners or row["created_at"] > partners[pid]["last_message_at"]:
            partners[pid] = {
                "last_message": row["content"],
                "last_message_at": row["created_at"],
                "unread_count": partners.get(pid, {}).get("unread_count", 0),
            }

    for row in received.data:
        pid = row["sender_id"]
        unread = partners.get(pid, {}).get("unread_count", 0)
        if not row["is_read"]:
            unread += 1
        if pid not in partners or row["created_at"] > partners[pid]["last_message_at"]:
            partners[pid] = {
                "last_message": row["content"],
                "last_message_at": row["created_at"],
                "unread_count": unread,
            }
        else:
            partners[pid]["unread_count"] = unread

    # 프로필 이름 조회
    partner_ids = list(partners.keys())
    names: dict[str, str | None] = {}
    if partner_ids:
        profiles = (
            sb.table("profiles")
            .select("id, name")
            .in_("id", partner_ids)
            .execute()
        )
        for p in profiles.data:
            names[p["id"]] = p.get("name")

    # 마지막 메시지 시간 기준 정렬
    result = []
    for pid, info in partners.items():
        result.append(
            ChatPartnerResponse(
                user_id=pid,
                name=names.get(pid),
                last_message=info["last_message"],
                last_message_at=info["last_message_at"],
                unread_count=info["unread_count"],
            )
        )
    result.sort(key=lambda x: x.last_message_at or "", reverse=True)
    return result


# ── GET /chat/{other_user_id}/messages ────────────
@router.get("/{other_user_id}/messages", response_model=list[MessageResponse])
async def list_messages(
    other_user_id: UUID,
    limit: int = Query(50, ge=1, le=200),
    user_id: str = Depends(get_current_user_id),
):
    """특정 상대와의 채팅 메시지를 시간순으로 반환하고, 안 읽은 메시지를 읽음 처리한다."""
    sb = get_supabase()
    other_id = str(other_user_id)

    # 두 사람 사이의 메시지 조회 (시간순)
    result = (
        sb.table("chat_messages")
        .select("*")
        .or_(
            f"and(sender_id.eq.{user_id},receiver_id.eq.{other_id}),"
            f"and(sender_id.eq.{other_id},receiver_id.eq.{user_id})"
        )
        .order("created_at", desc=False)
        .limit(limit)
        .execute()
    )

    # 상대가 보낸 안 읽은 메시지를 읽음 처리
    (
        sb.table("chat_messages")
        .update({"is_read": True})
        .eq("sender_id", other_id)
        .eq("receiver_id", user_id)
        .eq("is_read", False)
        .execute()
    )

    return [MessageResponse(**row) for row in result.data]


# ── POST /chat/{other_user_id}/messages ───────────
@router.post(
    "/{other_user_id}/messages",
    response_model=MessageResponse,
    status_code=status.HTTP_201_CREATED,
)
async def send_message(
    other_user_id: UUID,
    body: MessageCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    """상대에게 채팅 메시지를 전송한다."""
    sb = get_supabase()
    other_id = str(other_user_id)

    if user_id == other_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="자기 자신에게 메시지를 보낼 수 없습니다.",
        )

    # 안전 검사
    safety = check_content_safety(body.content)
    if not safety["safe"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"부적절한 콘텐츠가 감지되었습니다: {safety['reason']}",
        )

    row = (
        sb.table("chat_messages")
        .insert(
            {
                "sender_id": user_id,
                "receiver_id": other_id,
                "content": body.content,
            }
        )
        .execute()
    )

    return MessageResponse(**row.data[0])
