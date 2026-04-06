from fastapi import APIRouter, Depends

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.safety import SafetyCheckRequest, SafetyCheckResponse
from app.services.safety import check_content_safety

router = APIRouter(prefix="/safety", tags=["safety"])


@router.post("/check", response_model=SafetyCheckResponse)
async def check_safety(
    body: SafetyCheckRequest,
    user_id: str = Depends(get_current_user_id),
):
    result = check_content_safety(body.content)
    return SafetyCheckResponse(**result)
