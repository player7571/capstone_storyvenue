from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.chapters import (
    ChapterGenerateRequest,
    ChapterResponse,
    ChapterUpdateRequest,
)
from app.db.supabase import get_supabase
from app.services.adaptive_interview import (
    build_question_answer_conversation_history,
    derive_voice_interview_state_from_session_messages,
    is_voice_interview_state_message,
)
from app.services import generate_chapter_content

router = APIRouter(prefix="/chapters", tags=["chapters"])


def _get_session_or_404(session_id: UUID, user_id: str) -> dict:
    result = (
        get_supabase()
        .table("interview_sessions")
        .select("id, user_id, status, session_type")
        .eq("id", str(session_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    session = result.data
    if not session or str(session.get("status") or "").strip().lower() == "deleted":
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="문답을 찾을 수 없습니다.",
        )
    return session


def _get_chapter_or_404(chapter_id: UUID, user_id: str) -> dict:
    result = (
        get_supabase()
        .table("chapter_drafts")
        .select("*")
        .eq("id", str(chapter_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="챕터를 찾을 수 없습니다.",
        )
    return result.data


def _load_conversation_history(session_id: UUID) -> list[dict[str, str]]:
    result = (
        get_supabase()
        .table("session_messages")
        .select("role, content")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .execute()
    )

    rows = result.data or []
    state = derive_voice_interview_state_from_session_messages(rows)
    question_answer_history = build_question_answer_conversation_history(state)
    if question_answer_history:
        return question_answer_history

    history: list[dict[str, str]] = []
    for row in rows:
        content = str(row.get("content", "")).strip()
        role = str(row.get("role", "")).strip().lower()
        if role != "user" or not content or is_voice_interview_state_message(content):
            continue
        history.append({"role": "user", "content": content})

    return history


def _get_user_name(user_id: str) -> str:
    try:
        result = (
            get_supabase()
            .table("profiles")
            .select("name")
            .eq("id", user_id)
            .maybe_single()
            .execute()
        )
        user_name = (result.data or {}).get("name")
        if user_name:
            return str(user_name).strip()
    except Exception:
        pass

    return "사용자"


@router.post(
    "/generate",
    response_model=ChapterResponse,
    status_code=status.HTTP_201_CREATED,
)
async def generate_chapter(
    body: ChapterGenerateRequest,
    user_id: str = Depends(get_current_user_id),
):
    _get_session_or_404(body.session_id, user_id)
    conversation_history = _load_conversation_history(body.session_id)
    if not conversation_history:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="세션에 사용자 답변 기록이 없습니다.",
        )

    try:
        generated = generate_chapter_content(
            conversation_history=conversation_history,
            chapter_type=body.chapter_type,
            user_name=_get_user_name(user_id),
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"챕터 생성 중 오류가 발생했습니다: {exc}",
        ) from exc

    try:
        created = (
            get_supabase()
            .table("chapter_drafts")
            .insert(
                {
                    "user_id": user_id,
                    "session_id": str(body.session_id),
                    "title": generated["title"],
                    "content": generated["content"],
                    "chapter_type": body.chapter_type,
                    "version_no": 1,
                }
            )
            .execute()
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"생성된 챕터 저장 중 오류가 발생했습니다: {exc}",
        ) from exc

    return ChapterResponse(**created.data[0])


@router.get("", response_model=list[ChapterResponse])
async def list_chapters(
    session_id: UUID | None = Query(default=None),
    user_id: str = Depends(get_current_user_id),
):
    query = (
        get_supabase()
        .table("chapter_drafts")
        .select("*")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
    )
    if session_id is not None:
        query = query.eq("session_id", str(session_id))

    result = query.execute()
    return [ChapterResponse(**row) for row in result.data or []]


@router.get("/{chapter_id}", response_model=ChapterResponse)
async def get_chapter(
    chapter_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    return ChapterResponse(**_get_chapter_or_404(chapter_id, user_id))


@router.put("/{chapter_id}", response_model=ChapterResponse)
async def update_chapter(
    chapter_id: UUID,
    body: ChapterUpdateRequest,
    user_id: str = Depends(get_current_user_id),
):
    chapter = _get_chapter_or_404(chapter_id, user_id)
    updated = (
        get_supabase()
        .table("chapter_drafts")
        .update(
            {
                "title": body.title,
                "content": body.content,
                "version_no": int(chapter.get("version_no", 1)) + 1,
            }
        )
        .eq("id", str(chapter_id))
        .eq("user_id", user_id)
        .execute()
    )
    if not updated.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="챕터를 찾을 수 없습니다.",
        )

    return ChapterResponse(**updated.data[0])
