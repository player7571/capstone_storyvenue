from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class MemoryResponse(BaseModel):
    id: UUID
    user_id: UUID
    session_id: UUID
    memory_type: str
    content: str
    created_at: datetime
