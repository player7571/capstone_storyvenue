from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


# ── 요청 ──────────────────────────────────────────
class FeedCreateRequest(BaseModel):
    book_id: UUID
    title: str
    preview: str


# ── 응답 ──────────────────────────────────────────
class FeedPostResponse(BaseModel):
    id: UUID
    user_id: UUID
    book_id: UUID
    title: str
    preview: str
    like_count: int
    created_at: datetime
    author_name: str | None = None
    author_avatar_url: str | None = None


class FeedDetailResponse(FeedPostResponse):
    liked_by_me: bool = False


class LikeToggleResponse(BaseModel):
    liked: bool
    like_count: int
