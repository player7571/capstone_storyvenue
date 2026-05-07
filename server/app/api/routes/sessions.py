from pathlib import Path
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, File, HTTPException, Response, UploadFile, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.sessions import (
    InterviewStateResponse,
    PhotoAttachmentResponse,
    SessionCreateRequest,
    SessionResponse,
    SessionSummaryResponse,
)
from app.db.supabase import get_supabase
from app.services.interview import (
    INTERVIEW_STATE_ROLE,
    build_initial_voice_interview_state,
    build_voice_interview_prompt_state,
    delete_voice_interview_state_from_store,
    derive_voice_interview_state_from_session_messages,
    get_interview_question,
    initialize_question_state_rows,
    load_voice_interview_state_from_store,
    move_voice_interview_question,
    save_voice_interview_state_to_store,
    serialize_voice_interview_state,
)
from app.services.photo_interview import generate_photo_opening_message
from app.services.session_artifacts import (
    PHOTO_ARTIFACT_TYPE,
    get_latest_session_artifact,
    insert_session_artifact,
)

router = APIRouter(prefix="/sessions", tags=["sessions"])

PHOTO_BUCKET_NAME = "interview-photos"
DELETED_SESSION_STATUS = "deleted"
MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024
SIGNED_URL_EXPIRES_IN = 60 * 60 * 24 * 30
SUPPORTED_IMAGE_TYPES = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
}
AUDIO_CACHE_DIR = Path(__file__).resolve().parents[3] / ".generated-audio"


def _is_deleted_session(session: dict) -> bool:
    return str(session.get("status") or "").strip().lower() == DELETED_SESSION_STATUS


def _get_session_or_404(session_id: UUID, user_id: str) -> dict:
    result = (
        get_supabase()
        .table("interview_sessions")
        .select("*")
        .eq("id", str(session_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    if not result.data or _is_deleted_session(result.data):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="세션을 찾을 수 없습니다.",
        )
    return result.data


def _insert_session_message(session_id: UUID, role: str, content: str) -> dict:
    result = (
        get_supabase()
        .table("session_messages")
        .insert(
            {
                "session_id": str(session_id),
                "role": role,
                "content": content,
            }
        )
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 메시지 저장에 실패했습니다.",
        )
    return result.data[0]


def _create_voice_session_record(
    user_id: str,
    *,
    title: str,
    theme: str,
) -> tuple[dict, InterviewStateResponse]:
    result = (
        get_supabase()
        .table("interview_sessions")
        .insert(
            {
                "user_id": user_id,
                "title": title,
                "theme": theme,
                "status": "in_progress",
                "session_type": "voice",
            }
        )
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 생성에 실패했습니다.",
        )
    session = result.data[0]
    interview_state = _insert_voice_interview_state(UUID(str(session["id"])))
    return session, interview_state


def _insert_voice_interview_state(session_id: UUID) -> InterviewStateResponse:
    initial_state = build_initial_voice_interview_state()
    try:
        initialize_question_state_rows(session_id)
        save_voice_interview_state_to_store(session_id, initial_state)
    except Exception:
        _insert_session_message(
            session_id,
            INTERVIEW_STATE_ROLE,
            serialize_voice_interview_state(initial_state),
        )
    return InterviewStateResponse(**build_voice_interview_prompt_state(initial_state).model_dump())


def _load_voice_interview_state_response(session_id: UUID) -> InterviewStateResponse:
    try:
        state = load_voice_interview_state_from_store(session_id)
        if state is not None:
            return InterviewStateResponse(**build_voice_interview_prompt_state(state).model_dump())
    except Exception:
        pass

    rows = (
        get_supabase()
        .table("session_messages")
        .select("role, content")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .execute()
    )
    state = derive_voice_interview_state_from_session_messages(rows.data or [])
    return InterviewStateResponse(**build_voice_interview_prompt_state(state).model_dump())


def _get_current_question_no_for_session(session_id: UUID) -> int | None:
    try:
        state = load_voice_interview_state_from_store(session_id)
        if state is not None:
            return state.current_question_no
    except Exception:
        pass

    result = (
        get_supabase()
        .table("interview_sessions")
        .select("current_question_no")
        .eq("id", str(session_id))
        .maybe_single()
        .execute()
    )
    current_question_no = (result.data or {}).get("current_question_no")
    if current_question_no is None:
        return None
    try:
        return int(current_question_no)
    except (TypeError, ValueError):
        return None


def _build_photo_attachment_response(
    artifact: dict,
    *,
    ai_message: str | None = None,
) -> PhotoAttachmentResponse:
    return PhotoAttachmentResponse(
        artifact_id=UUID(str(artifact["id"])),
        photo_url=str(artifact.get("storage_url") or ""),
        linked_question_no=(
            int(artifact["linked_question_no"])
            if artifact.get("linked_question_no") is not None
            else None
        ),
        ai_message=ai_message,
        created_at=artifact["created_at"],
    )


def _get_active_photo_response(
    session_id: UUID,
    *,
    current_question_no: int | None,
) -> PhotoAttachmentResponse | None:
    artifact = None
    if current_question_no is not None:
        artifact = get_latest_session_artifact(
            session_id=session_id,
            artifact_type=PHOTO_ARTIFACT_TYPE,
            linked_question_no=current_question_no,
        )
    if artifact is None:
        artifact = get_latest_session_artifact(
            session_id=session_id,
            artifact_type=PHOTO_ARTIFACT_TYPE,
        )
    if artifact is None:
        return None
    return _build_photo_attachment_response(artifact)


def _build_session_response(session: dict) -> SessionResponse:
    interview_state = _load_voice_interview_state_response(UUID(str(session["id"])))
    current_question_no = interview_state.current_question_no if interview_state is not None else None
    active_photo = _get_active_photo_response(
        UUID(str(session["id"])),
        current_question_no=current_question_no,
    )
    return SessionResponse(**session, active_photo=active_photo, interview_state=interview_state)


def _attach_photo_to_session(
    *,
    session_id: UUID,
    user_id: str,
    image_file: UploadFile,
    image_bytes: bytes,
    content_type: str,
) -> PhotoAttachmentResponse:
    linked_question_no = _get_current_question_no_for_session(session_id)
    photo_url = _upload_photo_to_storage(
        user_id=user_id,
        session_id=session_id,
        image_bytes=image_bytes,
        content_type=content_type,
        filename=image_file.filename,
    )
    current_question = get_interview_question(linked_question_no) if linked_question_no is not None else None
    ai_message = generate_photo_opening_message(
        image_bytes,
        content_type,
        current_question=current_question.main_question if current_question is not None else None,
        question_hint=current_question.hint if current_question is not None else None,
    )
    artifact = insert_session_artifact(
        session_id=session_id,
        artifact_type=PHOTO_ARTIFACT_TYPE,
        storage_url=photo_url,
        mime_type=content_type,
        linked_question_no=linked_question_no,
    )
    try:
        (
            get_supabase()
            .table("interview_sessions")
            .update({"photo_url": photo_url})
            .eq("id", str(session_id))
            .eq("user_id", user_id)
            .execute()
        )
    except Exception:
        pass
    _insert_session_message(session_id, "assistant", ai_message)
    return _build_photo_attachment_response(artifact, ai_message=ai_message)


def _move_voice_session_question(
    session_id: UUID,
    user_id: str,
    direction: str,
) -> InterviewStateResponse:
    try:
        current_state = load_voice_interview_state_from_store(session_id)
    except Exception:
        current_state = None
    if current_state is None:
        rows = (
            get_supabase()
            .table("session_messages")
            .select("role, content")
            .eq("session_id", str(session_id))
            .order("created_at", desc=False)
            .execute()
        )
        current_state = derive_voice_interview_state_from_session_messages(rows.data or [])
    next_state = move_voice_interview_question(current_state, direction=direction)
    if next_state is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="더 이상 이동할 수 없습니다.",
        )

    try:
        save_voice_interview_state_to_store(session_id, next_state)
    except Exception:
        _insert_session_message(
            session_id,
            INTERVIEW_STATE_ROLE,
            serialize_voice_interview_state(next_state),
        )
    status_value = PHOTO_COMPLETED_STATUS if next_state.is_interview_complete else "in_progress"
    (
        get_supabase()
        .table("interview_sessions")
        .update({"status": status_value})
        .eq("id", str(session_id))
        .eq("user_id", user_id)
        .execute()
    )
    return InterviewStateResponse(**build_voice_interview_prompt_state(next_state).model_dump())


def _validate_photo_upload(image_file: UploadFile, image_bytes: bytes) -> str:
    content_type = str(image_file.content_type or "").strip().lower()
    if content_type not in SUPPORTED_IMAGE_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="JPEG 또는 PNG 이미지 파일만 올릴 수 있습니다.",
        )
    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미지 파일이 비어 있습니다.",
        )
    if len(image_bytes) > MAX_PHOTO_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미지 크기는 5MB 이하여야 합니다.",
        )
    return content_type


def _build_photo_storage_path(
    user_id: str,
    session_id: UUID,
    content_type: str,
    filename: str | None,
) -> str:
    suffix = Path(filename or "").suffix.lower()
    allowed_suffixes = {".jpg", ".jpeg", ".png"}
    if suffix not in allowed_suffixes:
        suffix = SUPPORTED_IMAGE_TYPES[content_type]
    return f"{user_id}/{session_id}/{uuid4().hex}{suffix}"


def _upload_photo_to_storage(
    user_id: str,
    session_id: UUID,
    image_bytes: bytes,
    content_type: str,
    filename: str | None,
) -> str:
    storage_path = _build_photo_storage_path(user_id, session_id, content_type, filename)
    bucket = get_supabase().storage.from_(PHOTO_BUCKET_NAME)
    bucket.upload(
        storage_path,
        image_bytes,
        {
            "content-type": content_type,
            "x-upsert": "false",
        },
    )
    signed = bucket.create_signed_url(storage_path, SIGNED_URL_EXPIRES_IN)
    photo_url = signed.get("signedURL") or signed.get("signedUrl")
    if not photo_url:
        raise RuntimeError("업로드한 사진의 URL을 생성하지 못했습니다.")
    return str(photo_url)


def _delete_generated_audio_files(session_id: UUID) -> None:
    if not AUDIO_CACHE_DIR.exists():
        return

    for audio_file in AUDIO_CACHE_DIR.glob(f"{session_id}-*.mp3"):
        try:
            audio_file.unlink()
        except OSError:
            continue


@router.post("", response_model=SessionResponse, status_code=status.HTTP_201_CREATED)
async def create_session(
    body: SessionCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    try:
        session, interview_state = _create_voice_session_record(
            user_id,
            title=body.title,
            theme=body.theme,
        )
        return SessionResponse(**session, interview_state=interview_state)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 생성 중 오류가 발생했습니다.",
        ) from exc


@router.post(
    "/{session_id}/photos",
    response_model=PhotoAttachmentResponse,
    status_code=status.HTTP_201_CREATED,
)
async def attach_photo_to_session(
    session_id: UUID,
    image_file: UploadFile = File(...),
    user_id: str = Depends(get_current_user_id),
):
    _get_session_or_404(session_id, user_id)
    image_bytes = await image_file.read()
    content_type = _validate_photo_upload(image_file, image_bytes)

    try:
        return _attach_photo_to_session(
            session_id=session_id,
            user_id=user_id,
            image_file=image_file,
            image_bytes=image_bytes,
            content_type=content_type,
        )
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"사진 첨부 중 오류가 발생했습니다: {exc}",
        ) from exc


@router.get("", response_model=list[SessionSummaryResponse])
async def list_sessions(
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()

    try:
        result = (
            sb.table("interview_sessions")
            .select("id, user_id, title, theme, status, session_type, created_at")
            .eq("user_id", user_id)
            .order("created_at", desc=True)
            .execute()
        )
        visible_rows = [row for row in (result.data or []) if not _is_deleted_session(row)]
        return [SessionSummaryResponse(**row) for row in visible_rows]
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 목록 조회 중 오류가 발생했습니다.",
        ) from exc


@router.get("/{session_id}", response_model=SessionResponse)
async def get_session_detail(
    session_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    try:
        return _build_session_response(_get_session_or_404(session_id, user_id))
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 상세 조회 중 오류가 발생했습니다.",
        ) from exc


@router.post("/{session_id}/previous-question", response_model=InterviewStateResponse)
async def go_to_previous_question(
    session_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    _get_session_or_404(session_id, user_id)

    try:
        return _move_voice_session_question(session_id, user_id, direction="previous")
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="이전 질문으로 이동하는 중 오류가 발생했습니다.",
        ) from exc


@router.post("/{session_id}/next-question", response_model=InterviewStateResponse)
async def go_to_next_question(
    session_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    _get_session_or_404(session_id, user_id)

    try:
        return _move_voice_session_question(session_id, user_id, direction="next")
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="다음 질문으로 이동하는 중 오류가 발생했습니다.",
        ) from exc


@router.delete("/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_session(
    session_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    session = _get_session_or_404(session_id, user_id)
    sb = get_supabase()

    try:
        sb.table("session_messages").delete().eq("session_id", str(session_id)).execute()
        sb.table("user_memories").delete().eq("user_id", user_id).eq(
            "session_id", str(session_id)
        ).execute()
        sb.table("session_artifacts").delete().eq("session_id", str(session_id)).execute()
        try:
            delete_voice_interview_state_from_store(session_id)
        except Exception:
            pass
        _delete_generated_audio_files(session_id)

        updated = (
            sb.table("interview_sessions")
            .update(
                {
                    "status": DELETED_SESSION_STATUS,
                    "photo_url": None,
                }
            )
            .eq("id", str(session_id))
            .eq("user_id", user_id)
            .execute()
        )
        if not updated.data:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="세션을 찾을 수 없습니다.",
            )
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"문답 삭제 중 오류가 발생했습니다: {exc}",
        ) from exc

    return Response(status_code=status.HTTP_204_NO_CONTENT)
