from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class SessionCreateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    theme: str = Field(min_length=1, max_length=200)


class SessionResponse(BaseModel):
    id: UUID
    user_id: UUID
    title: str | None = None
    theme: str | None = None
    status: str | None = None
    created_at: datetime

