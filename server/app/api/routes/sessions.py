from pathlib import Path
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, File, HTTPException, Response, UploadFile, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.sessions import (
    InterviewStateResponse,
    PhotoSessionEndRequest,
    PhotoSessionEndResponse,
    PhotoSessionReplyRequest,
    PhotoSessionReplyResponse,
    PhotoSessionStartResponse,
    SessionCreateRequest,
    SessionResponse,
)
from app.db.supabase import get_supabase
from app.services import extract_memories
from app.services.adaptive_interview import (
    INTERVIEW_STATE_ROLE,
    build_initial_voice_interview_state,
    build_voice_interview_prompt_state,
    derive_voice_interview_state_from_session_messages,
    is_voice_interview_state_message,
    move_voice_interview_question,
    serialize_voice_interview_state,
)
from app.services.photo_interview import (
    generate_photo_follow_up_message,
    generate_photo_opening_message,
)

router = APIRouter(prefix="/sessions", tags=["sessions"])

PHOTO_BUCKET_NAME = "interview-photos"
PHOTO_SESSION_TITLE = "사진 문답"
PHOTO_SESSION_THEME = "photo"
PHOTO_SESSION_TYPE = "photo"
PHOTO_COMPLETED_STATUS = "completed"
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


def _require_photo_session(session: dict) -> None:
    session_type = str(session.get("session_type") or "voice").strip().lower()
    if session_type != PHOTO_SESSION_TYPE:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="사진 문답이 아닙니다.",
        )


def _require_active_session(session: dict) -> None:
    session_status = str(session.get("status") or "active").strip().lower()
    if session_status != "active":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이미 종료된 사진 문답입니다.",
        )


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


def _insert_voice_interview_state(session_id: UUID) -> InterviewStateResponse:
    initial_state = build_initial_voice_interview_state()
    _insert_session_message(
        session_id,
        INTERVIEW_STATE_ROLE,
        serialize_voice_interview_state(initial_state),
    )
    return InterviewStateResponse(**build_voice_interview_prompt_state(initial_state).model_dump())


def _load_conversation_history(session_id: UUID) -> list[dict[str, str]]:
    result = (
        get_supabase()
        .table("session_messages")
        .select("role, content")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .limit(50)
        .execute()
    )

    history: list[dict[str, str]] = []
    for row in result.data or []:
        role = str(row.get("role", "")).strip()
        content = str(row.get("content", "")).strip()
        if role not in {"user", "assistant"} or not content or is_voice_interview_state_message(content):
            continue
        history.append({"role": role, "content": content})

    return history


def _load_voice_interview_state_response(session_id: UUID) -> InterviewStateResponse:
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


def _build_session_response(session: dict) -> SessionResponse:
    session_type = str(session.get("session_type") or "voice").strip().lower()
    interview_state = None
    if session_type != PHOTO_SESSION_TYPE:
        interview_state = _load_voice_interview_state_response(UUID(str(session["id"])))
    return SessionResponse(**session, interview_state=interview_state)


def _move_voice_session_question(
    session_id: UUID,
    user_id: str,
    direction: str,
) -> InterviewStateResponse:
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


def _store_memories(
    user_id: str,
    session_id: UUID,
    extracted_memories: dict[str, list[str]],
) -> int:
    existing = (
        get_supabase()
        .table("user_memories")
        .select("memory_type, content")
        .eq("user_id", user_id)
        .eq("session_id", str(session_id))
        .execute()
    )
    existing_pairs = {
        (str(row.get("memory_type", "")).strip(), str(row.get("content", "")).strip())
        for row in (existing.data or [])
    }

    rows: list[dict[str, str]] = []
    for memory_type, items in extracted_memories.items():
        for item in items:
            value = str(item).strip()
            pair = (memory_type, value)
            if not value or pair in existing_pairs:
                continue
            existing_pairs.add(pair)
            rows.append(
                {
                    "user_id": user_id,
                    "session_id": str(session_id),
                    "memory_type": memory_type,
                    "content": value,
                }
            )

    if not rows:
        return 0

    inserted = get_supabase().table("user_memories").insert(rows).execute()
    return len(inserted.data or rows)


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
    sb = get_supabase()

    try:
        result = (
            sb.table("interview_sessions")
            .insert(
                {
                    "user_id": user_id,
                    "title": body.title,
                    "theme": body.theme,
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
        return SessionResponse(**session, interview_state=interview_state)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="세션 생성 중 오류가 발생했습니다.",
        ) from exc


@router.post(
    "/photo",
    response_model=PhotoSessionStartResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_photo_session(
    image_file: UploadFile = File(...),
    user_id: str = Depends(get_current_user_id),
):
    image_bytes = await image_file.read()
    content_type = _validate_photo_upload(image_file, image_bytes)
    sb = get_supabase()

    try:
        created = (
            sb.table("interview_sessions")
            .insert(
                {
                    "user_id": user_id,
                    "title": PHOTO_SESSION_TITLE,
                    "theme": PHOTO_SESSION_THEME,
                    "session_type": PHOTO_SESSION_TYPE,
                }
            )
            .execute()
        )
        if not created.data:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="사진 문답 시작에 실패했습니다.",
            )

        session = created.data[0]
        session_id = UUID(str(session["id"]))
        photo_url = _upload_photo_to_storage(
            user_id=user_id,
            session_id=session_id,
            image_bytes=image_bytes,
            content_type=content_type,
            filename=image_file.filename,
        )
        updated = (
            sb.table("interview_sessions")
            .update({"photo_url": photo_url})
            .eq("id", str(session_id))
            .eq("user_id", user_id)
            .execute()
        )
        if updated.data:
            session = updated.data[0]

        ai_message = generate_photo_opening_message(image_bytes, content_type)
        _insert_session_message(session_id, "assistant", ai_message)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"사진 문답 시작 중 오류가 발생했습니다: {exc}",
        ) from exc

    return PhotoSessionStartResponse(
        session_id=session_id,
        photo_url=photo_url,
        ai_message=ai_message,
        created_at=session["created_at"],
    )


@router.get("", response_model=list[SessionResponse])
async def list_sessions(
    user_id: str = Depends(get_current_user_id),
):
    sb = get_supabase()

    try:
        result = (
            sb.table("interview_sessions")
            .select("*")
            .eq("user_id", user_id)
            .order("created_at", desc=True)
            .execute()
        )
        visible_rows = [row for row in (result.data or []) if not _is_deleted_session(row)]
        return [SessionResponse(**row) for row in visible_rows]
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
    session = _get_session_or_404(session_id, user_id)
    session_type = str(session.get("session_type") or "voice").strip().lower()
    if session_type == PHOTO_SESSION_TYPE:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="사진 문답에서는 이전 질문 기능을 사용할 수 없습니다.",
        )

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
    session = _get_session_or_404(session_id, user_id)
    session_type = str(session.get("session_type") or "voice").strip().lower()
    if session_type == PHOTO_SESSION_TYPE:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="사진 문답에서는 다음 질문 기능을 사용할 수 없습니다.",
        )

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


@router.post("/{session_id}/reply", response_model=PhotoSessionReplyResponse)
async def reply_photo_session(
    session_id: UUID,
    body: PhotoSessionReplyRequest,
    user_id: str = Depends(get_current_user_id),
):
    session = _get_session_or_404(session_id, user_id)
    _require_photo_session(session)
    _require_active_session(session)

    try:
        user_message = _insert_session_message(session_id, "user", body.content.strip())
        history = _load_conversation_history(session_id)
        ai_message = generate_photo_follow_up_message(history)
        assistant_message = _insert_session_message(session_id, "assistant", ai_message)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"사진 문답 답변 처리 중 오류가 발생했습니다: {exc}",
        ) from exc

    return PhotoSessionReplyResponse(
        user_message_id=user_message["id"],
        ai_message=ai_message,
        ai_message_id=assistant_message["id"],
    )


@router.post("/{session_id}/end", response_model=PhotoSessionEndResponse)
async def end_photo_session(
    session_id: UUID,
    body: PhotoSessionEndRequest | None = None,
    user_id: str = Depends(get_current_user_id),
):
    session = _get_session_or_404(session_id, user_id)
    _require_photo_session(session)
    extract_requested = body.extract_memories if body is not None else False

    try:
        memories_created = 0
        if extract_requested:
            history = _load_conversation_history(session_id)
            if history:
                extracted_memories = extract_memories(history)
                memories_created = _store_memories(
                    user_id=user_id,
                    session_id=session_id,
                    extracted_memories=extracted_memories,
                )

        updated = (
            get_supabase()
            .table("interview_sessions")
            .update({"status": PHOTO_COMPLETED_STATUS})
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
            detail=f"사진 문답 종료 중 오류가 발생했습니다: {exc}",
        ) from exc

    return PhotoSessionEndResponse(
        session_id=session_id,
        status=str(updated.data[0].get("status") or PHOTO_COMPLETED_STATUS),
        memories_created=memories_created,
    )
