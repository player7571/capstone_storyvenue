from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


# ── 응답 ──────────────────────────────────────────
class NotificationResponse(BaseModel):
    id: UUID
    type: str
    actor_name: str | None = None
    post_id: UUID | None = None
    message: str
    comment_preview: str | None = None
    is_read: bool
    created_at: datetime


class UnreadCountResponse(BaseModel):
    count: int
