from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class SessionMessageResponse(BaseModel):
    id: UUID
    role: str
    content: str
    created_at: datetime

