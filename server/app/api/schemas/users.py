from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class UserProfileResponse(BaseModel):
    id: UUID
    name: str | None = None
    email: str | None = None
    notification_enabled: bool = True
    created_at: datetime


class UserProfileUpdateRequest(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=50)
    notification_enabled: bool | None = None


class UserDeleteResponse(BaseModel):
    message: str

