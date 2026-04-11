from pydantic import BaseModel


class VoiceTurnResponse(BaseModel):
    user_text: str
    assistant_text: str
    audio_url: str

