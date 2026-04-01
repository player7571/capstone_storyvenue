from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field

ChapterType = Literal["childhood", "youth", "career", "love", "reflection"]


class ChapterGenerateRequest(BaseModel):
    session_id: UUID
    chapter_type: ChapterType


class ChapterUpdateRequest(BaseModel):
    title: str = Field(min_length=1)
    content: str = Field(min_length=1)


class ChapterResponse(BaseModel):
    id: UUID
    user_id: UUID
    session_id: UUID
    title: str
    content: str
    chapter_type: ChapterType
    version_no: int
    created_at: datetime
