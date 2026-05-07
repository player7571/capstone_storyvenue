from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.chapters import (
    ChapterGenerateRequest,
    ChapterResponse,
    ChapterSummaryResponse,
    ChapterUpdateRequest,
)
from app.db.supabase import get_supabase
from app.services.interview import (
    derive_voice_interview_state_from_session_messages,
    get_interview_question,
    get_question_answers,
    get_question_story_quality,
    load_question_state_from_store,
    load_voice_interview_state_from_store,
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


def _build_chapter_preview(content: str, limit: int = 120) -> str:
    normalized = " ".join(str(content or "").strip().split())
    if len(normalized) <= limit:
        return normalized
    return normalized[:limit].rstrip() + " ..."


def _load_legacy_conversation_history(session_id: UUID) -> list[dict[str, str]]:
    try:
        state = load_voice_interview_state_from_store(session_id)
    except Exception:
        state = None

    if state is not None:
        history: list[dict[str, str]] = []
        for question_no in range(1, 11):
            answers = get_question_answers(state, question_no)
            if not answers:
                continue
            history.append({"role": "user", "content": "\n".join(answers)})
        if history:
            return history

    result = (
        get_supabase()
        .table("session_messages")
        .select("role, content")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .execute()
    )

    rows = result.data or []
    legacy_state = derive_voice_interview_state_from_session_messages(rows)
    history_from_state: list[dict[str, str]] = []
    for question_no in range(1, 11):
        answers = get_question_answers(legacy_state, question_no)
        if not answers:
            continue
        history_from_state.append({"role": "user", "content": "\n".join(answers)})
    if history_from_state:
        return history_from_state

    history: list[dict[str, str]] = []
    for row in rows:
        content = str(row.get("content", "")).strip()
        role = str(row.get("role", "")).strip().lower()
        if role != "user" or not content:
            continue
        history.append({"role": "user", "content": content})

    return history


def _load_voice_question_story_context(
    session_id: UUID,
    question_no: int,
) -> tuple[list[dict[str, str]], str, str]:
    try:
        state = load_voice_interview_state_from_store(session_id)
    except Exception:
        state = None

    if state is None:
        result = (
            get_supabase()
            .table("session_messages")
            .select("role, content")
            .eq("session_id", str(session_id))
            .order("created_at", desc=False)
            .execute()
        )
        state = derive_voice_interview_state_from_session_messages(result.data or [])

    current_answers = get_question_answers(state, question_no)
    if not current_answers:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이 질문에는 아직 사용자 답변이 없습니다.",
        )

    current_answer_text = "\n".join(current_answers).strip()
    history: list[dict[str, str]] = [
        {
            "role": "user",
            "content": current_answer_text,
        },
    ]

    story_quality = get_question_story_quality(state, question_no)
    return history, current_answer_text, story_quality


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
    if body.question_no is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="질문 번호가 필요합니다.",
        )
    question = get_interview_question(body.question_no)
    chapter_type = question.chapter_type
    conversation_history, answer_snapshot, computed_story_quality = _load_voice_question_story_context(
        body.session_id,
        body.question_no,
    )
    try:
        question_state = load_question_state_from_store(body.session_id, body.question_no) or {}
    except Exception:
        question_state = {}
    story_quality = (
        str(question_state.get("story_quality") or "").strip().lower()
        or computed_story_quality
    )
    if story_quality == "none":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이 질문에 답변이 있어야 이야기를 만들 수 있어요.",
        )
    if story_quality == "basic" and not body.allow_basic:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="답변이 조금 짧아요. 지금 생성할지 한 번 더 이야기할지 선택해주세요.",
        )
    source_question_no = body.question_no

    try:
        generated = generate_chapter_content(
            conversation_history=conversation_history,
            chapter_type=chapter_type,
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
        payload = {
            "user_id": user_id,
            "session_id": str(body.session_id),
            "title": generated["title"],
            "content": generated["content"],
            "chapter_type": chapter_type,
            "version_no": 1,
        }
        if source_question_no is not None:
            payload["source_question_no"] = source_question_no
            payload["answer_snapshot"] = answer_snapshot
            payload["story_quality_at_generation"] = story_quality

        try:
            created = get_supabase().table("chapter_drafts").insert(payload).execute()
        except Exception as exc:
            if source_question_no is None or not any(
                key in str(exc)
                for key in ("source_question_no", "answer_snapshot", "story_quality_at_generation")
            ):
                raise
            legacy_payload = {
                "user_id": user_id,
                "session_id": str(body.session_id),
                "title": generated["title"],
                "content": generated["content"],
                "chapter_type": chapter_type,
                "version_no": 1,
            }
            created = get_supabase().table("chapter_drafts").insert(legacy_payload).execute()
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"생성된 챕터 저장 중 오류가 발생했습니다: {exc}",
        ) from exc

    if source_question_no is not None and created.data:
        try:
            from app.services.interview import mark_question_story_generated

            mark_question_story_generated(body.session_id, source_question_no, str(created.data[0]["id"]))
        except Exception:
            pass

    return ChapterResponse(**created.data[0])


@router.get("", response_model=list[ChapterSummaryResponse])
async def list_chapters(
    session_id: UUID | None = Query(default=None),
    question_no: int | None = Query(default=None, ge=1, le=10),
    latest_only: bool = Query(default=False),
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
    if question_no is not None:
        query = query.eq("source_question_no", question_no)

    result = query.execute()
    rows = result.data or []

    if latest_only:
        latest_rows_by_question: dict[int | None, dict] = {}
        for row in rows:
            key = row.get("source_question_no")
            if key not in latest_rows_by_question:
                latest_rows_by_question[key] = row
        rows = list(latest_rows_by_question.values())

    return [
        ChapterSummaryResponse(
            **{
                **row,
                "preview": _build_chapter_preview(str(row.get("content", ""))),
            }
        )
        for row in rows
    ]


@router.get("/latest", response_model=ChapterResponse)
async def get_latest_chapter(
    session_id: UUID = Query(...),
    question_no: int | None = Query(default=None, ge=1, le=10),
    user_id: str = Depends(get_current_user_id),
):
    _get_session_or_404(session_id, user_id)

    query = (
        get_supabase()
        .table("chapter_drafts")
        .select("*")
        .eq("user_id", user_id)
        .eq("session_id", str(session_id))
        .order("created_at", desc=True)
        .limit(1)
    )
    if question_no is not None:
        query = query.eq("source_question_no", question_no)

    result = query.execute()
    rows = result.data or []
    if not rows:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="생성된 초안이 없습니다.",
        )
    return ChapterResponse(**rows[0])


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


@router.delete(
    "/{chapter_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def delete_chapter(
    chapter_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    _get_chapter_or_404(chapter_id, user_id)
    deleted = (
        get_supabase()
        .table("chapter_drafts")
        .delete()
        .eq("id", str(chapter_id))
        .eq("user_id", user_id)
        .execute()
    )
    if deleted.data is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="챕터를 찾을 수 없습니다.",
        )
