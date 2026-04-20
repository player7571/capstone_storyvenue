from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


# ── 요청 ──────────────────────────────────────────
class CommentCreateRequest(BaseModel):
    content: str = Field(min_length=1, max_length=1000)


# ── 응답 ──────────────────────────────────────────
class CommentResponse(BaseModel):
    id: UUID
    post_id: UUID
    user_id: UUID
    author_name: str | None = None
    author_avatar_url: str | None = None
    content: str
    created_at: datetime
