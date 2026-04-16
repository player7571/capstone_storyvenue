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
    photo_url: str | None = None
    session_type: str | None = None
    created_at: datetime


class PhotoSessionStartResponse(BaseModel):
    session_id: UUID
    photo_url: str
    ai_message: str
    created_at: datetime


class PhotoSessionReplyRequest(BaseModel):
    content: str = Field(min_length=1, max_length=4000)


class PhotoSessionReplyResponse(BaseModel):
    user_message_id: UUID
    ai_message: str
    ai_message_id: UUID


class PhotoSessionEndRequest(BaseModel):
    extract_memories: bool = False


class PhotoSessionEndResponse(BaseModel):
    session_id: UUID
    status: str
    memories_created: int = 0
