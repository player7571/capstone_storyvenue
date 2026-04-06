from pydantic import BaseModel, Field


class SafetyCheckRequest(BaseModel):
    content: str = Field(min_length=1, max_length=5000)


class SafetyCheckResponse(BaseModel):
    safe: bool
    reason: str
