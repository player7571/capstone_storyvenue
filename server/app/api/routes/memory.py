from fastapi import APIRouter, Depends

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.memory import MemoryResponse
from app.db.supabase import get_supabase

router = APIRouter(prefix="/memory", tags=["memory"])


@router.get("", response_model=list[MemoryResponse])
async def list_memories(user_id: str = Depends(get_current_user_id)):
    result = (
        get_supabase()
        .table("user_memories")
        .select("*")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .execute()
    )
    return [MemoryResponse(**row) for row in result.data or []]
