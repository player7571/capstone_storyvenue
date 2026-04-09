from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.book import (
    BookCompileRequest,
    BookDetailResponse,
    BookSummaryResponse,
)
from app.db.supabase import get_supabase
from app.services import generate_book_subtitle

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
        chapter_result = (
            get_supabase()
            .table("chapter_drafts")
            .select("id, title, content")
            .eq("user_id", user_id)
            .in_("id", chapter_id_values)
            .execute()
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"챕터 조회 중 오류가 발생했습니다: {exc}",
        ) from exc

    chapters_by_id = {
        str(row["id"]): {
            "id": str(row["id"]),
            "title": str(row.get("title", "")).strip(),
            "content": str(row.get("content", "")).strip(),
        }
        for row in (chapter_result.data or [])
    }
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
