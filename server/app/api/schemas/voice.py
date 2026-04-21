from pydantic import BaseModel

from app.api.schemas.sessions import InterviewStateResponse


class VoiceTurnResponse(BaseModel):
    user_text: str
    assistant_text: str
    audio_url: str
    decision: str | None = None
    reason_code: str | None = None
    interview_state: InterviewStateResponse | None = None
