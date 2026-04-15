from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.users import (
    UserDeleteResponse,
    UserProfileResponse,
    UserProfileUpdateRequest,
)
from app.db.client import get_supabase_service_client

router = APIRouter(prefix="/users", tags=["users"])


def _get_profile_or_404(user_id: str) -> dict:
    result = (
        get_supabase_service_client()
        .table("profiles")
        .select("id, name, email, created_at")
        .eq("id", user_id)
        .maybe_single()
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="프로필 정보를 찾을 수 없습니다.",
        )
    return result.data


@router.get("/me", response_model=UserProfileResponse)
async def get_me(
    user_id: str = Depends(get_current_user_id),
):
    return UserProfileResponse(**_get_profile_or_404(user_id))


@router.put("/me", response_model=UserProfileResponse)
async def update_me(
    body: UserProfileUpdateRequest,
    user_id: str = Depends(get_current_user_id),
):
    update_data = body.model_dump(exclude_unset=True)

    if update_data:
        try:
            (
                get_supabase_service_client()
                .table("profiles")
                .update(update_data)
                .eq("id", user_id)
                .execute()
            )
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="프로필 수정 중 오류가 발생했습니다.",
            ) from exc

    return UserProfileResponse(**_get_profile_or_404(user_id))


@router.delete("/me", response_model=UserDeleteResponse)
async def delete_me(
    user_id: str = Depends(get_current_user_id),
):
    try:
        get_supabase_service_client().auth.admin.delete_user(user_id)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="회원탈퇴 처리 중 오류가 발생했습니다.",
        ) from exc

    return UserDeleteResponse(message="회원탈퇴가 완료되었습니다")

