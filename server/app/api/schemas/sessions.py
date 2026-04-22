from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class SessionCreateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    theme: str = Field(min_length=1, max_length=200)


class InterviewStateResponse(BaseModel):
    current_question_no: int
    total_questions: int
    main_question: str
    question_hint: str | None = None
    follow_up_count: int = 0
    question_status: str
    progress_percent: int
    is_interview_complete: bool = False
    current_question_has_answer: bool = False
    current_question_answer_count: int = 0
    current_question_story_ready: bool = False
    current_question_story_quality: str = "none"
    current_question_completed: bool = False
    current_question_can_move_next: bool = False
    story_target_question_no: int | None = None
    story_target_has_answer: bool = False
    story_target_answer_count: int = 0
    story_target_story_ready: bool = False
    story_target_story_quality: str = "none"
    story_target_is_current_question: bool = True


class SessionResponse(BaseModel):
    id: UUID
    user_id: UUID
    title: str | None = None
    theme: str | None = None
    status: str | None = None
    photo_url: str | None = None
    session_type: str | None = None
    created_at: datetime
    interview_state: InterviewStateResponse | None = None


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
