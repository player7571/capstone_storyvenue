from pydantic import BaseModel

from app.api.schemas.sessions import InterviewStateResponse


class VoiceTurnResponse(BaseModel):
    user_text: str
    assistant_text: str
    audio_url: str | None = None
    audio_status: str = "disabled"
    audio_id: str | None = None
    decision: str | None = None
    reason_code: str | None = None
    interview_state: InterviewStateResponse | None = None


class VoiceAudioStatusResponse(BaseModel):
    audio_id: str
    audio_status: str
    audio_url: str | None = None
