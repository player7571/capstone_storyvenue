from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class BookCompileRequest(BaseModel):
    chapter_ids: list[UUID] = Field(min_length=1)
    title: str = Field(min_length=1)


class AutobiographyCreateRequest(BaseModel):
    session_id: UUID
    chapter_ids: list[UUID] = Field(min_length=10, max_length=10)
    title: str = Field(min_length=1)


class AutobiographyCreateResponse(BaseModel):
    book_id: UUID


class BookShareResponse(BaseModel):
    book_id: UUID
    post_id: UUID


class BookShareStatusResponse(BaseModel):
    shared: bool
    post_id: UUID | None = None


class BookChapterPayload(BaseModel):
    id: UUID
    title: str
    content: str
    source_question_no: int | None = None


class BookSummaryResponse(BaseModel):
    id: UUID
    title: str
    subtitle: str | None = None
    created_at: datetime


class BookDetailResponse(BookSummaryResponse):
    user_id: UUID
    chapters: list[BookChapterPayload] = Field(default_factory=list)
    shared: bool = False
    shared_post_id: UUID | None = None
