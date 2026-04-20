from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


# ── 요청 ──────────────────────────────────────────
class MessageCreateRequest(BaseModel):
    content: str = Field(min_length=1, max_length=2000)


# ── 응답 ──────────────────────────────────────────
class MessageResponse(BaseModel):
    id: UUID
    sender_id: UUID
    receiver_id: UUID
    content: str
    is_read: bool
    created_at: datetime


class ChatPartnerResponse(BaseModel):
    user_id: str
    name: str | None = None
    avatar_url: str | None = None
    last_message: str | None = None
    last_message_at: datetime | None = None
    unread_count: int = 0
