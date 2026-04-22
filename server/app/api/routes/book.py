from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.book import (
    AutobiographyCreateRequest,
    AutobiographyPublishResponse,
    BookCompileRequest,
    BookDetailResponse,
    BookSummaryResponse,
)
from app.db.supabase import get_supabase
from app.services import generate_autobiography_chapters, generate_book_subtitle
from app.services.safety import check_content_safety

router = APIRouter(prefix="/book", tags=["book"])


def _normalize_book_row(row: dict) -> dict:
    normalized = dict(row)
    normalized["chapters"] = normalized.get("chapters") or []
    return normalized


def _get_book_or_404(book_id: UUID, user_id: str) -> dict:
    result = (
        get_supabase()
        .table("book_versions")
        .select("*")
        .eq("id", str(book_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="책을 찾을 수 없습니다.",
        )
    return _normalize_book_row(result.data)


def _load_owned_chapters(chapter_id_values: list[str], user_id: str) -> dict[str, dict]:
    result = (
        get_supabase()
        .table("chapter_drafts")
        .select("id, session_id, source_question_no, title, content")
        .eq("user_id", user_id)
        .in_("id", chapter_id_values)
        .execute()
    )
    return {
        str(row["id"]): {
            "id": str(row["id"]),
            "session_id": str(row.get("session_id")),
            "source_question_no": row.get("source_question_no"),
            "title": str(row.get("title", "")).strip(),
            "content": str(row.get("content", "")).strip(),
        }
        for row in (result.data or [])
    }


def _validate_autobiography_chapters(
    chapter_id_values: list[str],
    session_id: UUID,
    chapters_by_id: dict[str, dict],
) -> list[dict]:
    missing_ids = [chapter_id for chapter_id in chapter_id_values if chapter_id not in chapters_by_id]
    if missing_ids:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="하나 이상의 이야기를 찾을 수 없습니다.",
        )

    ordered_chapters = [chapters_by_id[chapter_id] for chapter_id in chapter_id_values]
    invalid_session = [
        chapter for chapter in ordered_chapters if str(chapter.get("session_id")) != str(session_id)
    ]
    if invalid_session:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="같은 문답에서 만든 이야기만 자서전에 담을 수 있어요.",
        )

    missing_story_numbers = [
        chapter for chapter in ordered_chapters if chapter.get("source_question_no") is None
    ]
    if missing_story_numbers:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="질문 번호가 없는 이야기는 자서전에 담을 수 없어요.",
        )

    story_numbers = [int(chapter["source_question_no"]) for chapter in ordered_chapters]
    if set(story_numbers) != set(range(1, 11)) or len(story_numbers) != 10:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="이야기 1번부터 10번까지가 모두 있어야 자서전을 만들 수 있어요.",
        )

    return sorted(ordered_chapters, key=lambda chapter: int(chapter["source_question_no"]))


def _build_book_preview(subtitle: str | None, chapters: list[dict]) -> str:
    subtitle_line = (subtitle or "").strip()
    chapters_block = "\n\n".join(
        (
            f"이야기 {int(chapter['source_question_no'])} : {str(chapter['title']).strip()}\n"
            f"{str(chapter['content']).strip()}"
        )
        for chapter in chapters
    ).strip()
    if subtitle_line and chapters_block:
        return f"{subtitle_line}\n\n{chapters_block}"
    if subtitle_line:
        return subtitle_line
    return chapters_block


def _create_book_version(user_id: str, title: str, subtitle: str | None, chapters: list[dict]) -> dict:
    created = (
        get_supabase()
        .table("book_versions")
        .insert(
            {
                "user_id": user_id,
                "title": title,
                "subtitle": subtitle,
                "chapters": chapters,
            }
        )
        .execute()
    )
    return _normalize_book_row(created.data[0])


def _generate_and_store_autobiography(body: AutobiographyCreateRequest, user_id: str) -> dict:
    chapter_id_values = [str(chapter_id) for chapter_id in body.chapter_ids]

    try:
        chapters_by_id = _load_owned_chapters(chapter_id_values, user_id)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"이야기 조회 중 오류가 발생했습니다: {exc}",
        ) from exc

    ordered_source_chapters = _validate_autobiography_chapters(
        chapter_id_values=chapter_id_values,
        session_id=body.session_id,
        chapters_by_id=chapters_by_id,
    )

    try:
        polished_chapters = generate_autobiography_chapters(
            book_title=body.title,
            chapters=ordered_source_chapters,
        )
        subtitle = generate_book_subtitle(
            book_title=body.title,
            chapter_titles=[chapter["title"] for chapter in polished_chapters],
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"자서전 생성 중 오류가 발생했습니다: {exc}",
        ) from exc

    try:
        return _create_book_version(
            user_id=user_id,
            title=body.title,
            subtitle=subtitle,
            chapters=polished_chapters,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"자서전 저장 중 오류가 발생했습니다: {exc}",
        ) from exc


@router.post(
    "/compile",
    response_model=BookDetailResponse,
    status_code=status.HTTP_201_CREATED,
)
async def compile_book(
    body: BookCompileRequest,
    user_id: str = Depends(get_current_user_id),
):
    chapter_id_values = [str(chapter_id) for chapter_id in body.chapter_ids]

    try:
        chapters_by_id = _load_owned_chapters(chapter_id_values, user_id)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"챕터 조회 중 오류가 발생했습니다: {exc}",
        ) from exc

    missing_ids = [chapter_id for chapter_id in chapter_id_values if chapter_id not in chapters_by_id]
    if missing_ids:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="하나 이상의 챕터를 찾을 수 없습니다.",
        )

    ordered_chapters = [chapters_by_id[chapter_id] for chapter_id in chapter_id_values]

    try:
        subtitle = generate_book_subtitle(
            book_title=body.title,
            chapter_titles=[chapter["title"] for chapter in ordered_chapters],
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"책 소개글 생성 중 오류가 발생했습니다: {exc}",
        ) from exc

    try:
        created = (
            get_supabase()
            .table("book_versions")
            .insert(
                {
                    "user_id": user_id,
                    "title": body.title,
                    "subtitle": subtitle,
                    "chapters": ordered_chapters,
                }
            )
            .execute()
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"책 저장 중 오류가 발생했습니다: {exc}",
        ) from exc

    return BookDetailResponse(**_normalize_book_row(created.data[0]))


@router.post(
    "/autobiography",
    response_model=BookDetailResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_autobiography(
    body: AutobiographyCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    created = _generate_and_store_autobiography(body, user_id)
    return BookDetailResponse(**created)


@router.post(
    "/autobiography/publish",
    response_model=AutobiographyPublishResponse,
    status_code=status.HTTP_201_CREATED,
)
async def publish_autobiography(
    body: AutobiographyCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    created_book = _generate_and_store_autobiography(body, user_id)
    preview = _build_book_preview(
        subtitle=created_book.get("subtitle"),
        chapters=created_book.get("chapters") or [],
    )

    safety = check_content_safety(f"{created_book.get('title', '')}\n{preview}")
    if not safety["safe"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"부적절한 콘텐츠가 감지되었습니다: {safety['reason']}",
        )

    try:
        created_post = (
            get_supabase()
            .table("feed_posts")
            .insert(
                {
                    "user_id": user_id,
                    "book_id": str(created_book["id"]),
                    "title": created_book["title"],
                    "preview": preview,
                }
            )
            .execute()
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"피드 게시 중 오류가 발생했습니다: {exc}",
        ) from exc

    return AutobiographyPublishResponse(
        book_id=created_book["id"],
        post_id=created_post.data[0]["id"],
    )


@router.get("", response_model=list[BookSummaryResponse])
async def list_books(user_id: str = Depends(get_current_user_id)):
    result = (
        get_supabase()
        .table("book_versions")
        .select("id, title, subtitle, created_at")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .execute()
    )
    return [BookSummaryResponse(**row) for row in result.data or []]


@router.get("/{book_id}", response_model=BookDetailResponse)
async def get_book(
    book_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    return BookDetailResponse(**_get_book_or_404(book_id, user_id))
