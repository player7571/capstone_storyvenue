from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.users import (
    UserDeleteResponse,
    UserProfileResponse,
    UserProfileUpdateRequest,
)
from app.db.client import get_supabase_service_client

router = APIRouter(prefix="/users", tags=["users"])

AVATAR_BUCKET_NAME = "avatars"
MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024
SIGNED_URL_EXPIRES_IN = 60 * 60 * 24 * 365
SUPPORTED_AVATAR_TYPES = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
}


def _get_profile_or_404(user_id: str) -> dict:
    result = (
        get_supabase_service_client()
        .table("profiles")
        .select("id, name, email, avatar_url, created_at")
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


@router.post("/me/avatar", response_model=UserProfileResponse)
async def upload_avatar(
    image_file: UploadFile = File(...),
    user_id: str = Depends(get_current_user_id),
):
    image_bytes = await image_file.read()
    content_type = str(image_file.content_type or "").strip().lower()
    if content_type not in SUPPORTED_AVATAR_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.",
        )
    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미지 파일이 비어 있습니다.",
        )
    if len(image_bytes) > MAX_AVATAR_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미지 크기는 5MB 이하여야 합니다.",
        )

    suffix = Path(image_file.filename or "").suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png"}:
        suffix = SUPPORTED_AVATAR_TYPES[content_type]
    storage_path = f"{user_id}/{uuid4().hex}{suffix}"

    sb = get_supabase_service_client()
    try:
        bucket = sb.storage.from_(AVATAR_BUCKET_NAME)
        bucket.upload(
            storage_path,
            image_bytes,
            {
                "content-type": content_type,
                "x-upsert": "false",
            },
        )
        signed = bucket.create_signed_url(storage_path, SIGNED_URL_EXPIRES_IN)
        avatar_url = signed.get("signedURL") or signed.get("signedUrl")
        if not avatar_url:
            raise RuntimeError("업로드한 이미지의 URL을 생성하지 못했습니다.")

        sb.table("profiles").update({"avatar_url": str(avatar_url)}).eq(
            "id", user_id
        ).execute()
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"프로필 사진 업로드 중 오류가 발생했습니다: {exc}",
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
