from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field

ChapterType = Literal["childhood", "youth", "career", "love", "reflection"]


class ChapterGenerateRequest(BaseModel):
    session_id: UUID
    question_no: int | None = Field(default=None, ge=1, le=10)
    chapter_type: ChapterType | None = None
    allow_basic: bool = False


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
    source_question_no: int | None = None
    version_no: int
    story_quality_at_generation: str | None = None
    created_at: datetime


class ChapterSummaryResponse(BaseModel):
    id: UUID
    user_id: UUID
    session_id: UUID
    title: str
    preview: str
    chapter_type: ChapterType
    source_question_no: int | None = None
    version_no: int
    story_quality_at_generation: str | None = None
    created_at: datetime
