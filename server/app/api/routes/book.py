import urllib.parse
from io import BytesIO
from uuid import UUID, uuid5

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import StreamingResponse

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.book import (
    AutobiographyCreateRequest,
    AutobiographyCreateResponse,
    BookShareResponse,
    BookShareStatusResponse,
    BookCompileRequest,
    BookDetailResponse,
    BookSummaryResponse,
    BookUpdateRequest,
)
from app.db.supabase import get_supabase
from app.services import generate_autobiography_book, generate_book_subtitle
from app.services.book_pdf import (
    ALLOWED_IMAGE_MIME,
    prepare_cover_image_data_url,
    render_book_pdf,
)
from app.services.safety import check_content_safety

MAX_COVER_IMAGE_BYTES = 10 * 1024 * 1024

router = APIRouter(prefix="/book", tags=["book"])

BOOK_BODY_OVERRIDE_TITLE = "__edited_body__"
BOOK_BODY_OVERRIDE_SOURCE_QUESTION_NO = 0
BOOK_BODY_OVERRIDE_NAMESPACE = UUID("1b3a4b4e-9a33-4bcb-87fe-4ac10f1f9cbe")


def _is_body_override_chapter(chapter: dict) -> bool:
    return (
        str(chapter.get("title") or "").strip() == BOOK_BODY_OVERRIDE_TITLE
        and int(chapter.get("source_question_no") or 0) == BOOK_BODY_OVERRIDE_SOURCE_QUESTION_NO
    )


def _visible_book_chapters(chapters: list[dict]) -> list[dict]:
    return [dict(chapter) for chapter in chapters if not _is_body_override_chapter(chapter)]


def _build_book_body(chapters: list[dict]) -> str:
    for chapter in chapters:
        if _is_body_override_chapter(chapter):
            content = str(chapter.get("content") or "").strip()
            if content:
                return content

    return "\n\n".join(
        str(chapter.get("content") or "").strip()
        for chapter in sorted(
            _visible_book_chapters(chapters),
            key=lambda chapter: int(chapter.get("source_question_no") or 2**31 - 1),
        )
        if str(chapter.get("content") or "").strip()
    ).strip()


def _upsert_book_body_override(chapters: list[dict], body: str) -> list[dict]:
    visible_chapters = _visible_book_chapters(chapters)
    override_chapter = {
        "id": str(uuid5(BOOK_BODY_OVERRIDE_NAMESPACE, "book-body-override")),
        "title": BOOK_BODY_OVERRIDE_TITLE,
        "content": body.strip(),
        "source_question_no": BOOK_BODY_OVERRIDE_SOURCE_QUESTION_NO,
    }
    return [override_chapter, *visible_chapters]


def _normalize_book_row(row: dict) -> dict:
    normalized = dict(row)
    raw_chapters = normalized.get("chapters") or []
    normalized["body"] = _build_book_body(raw_chapters)
    normalized["chapters"] = _visible_book_chapters(raw_chapters)
    return normalized


def _build_book_detail_response(
    row: dict,
    *,
    shared: bool = False,
    shared_post_id: str | None = None,
) -> BookDetailResponse:
    normalized = _normalize_book_row(row)
    normalized["shared"] = shared
    normalized["shared_post_id"] = shared_post_id
    return BookDetailResponse(**normalized)


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


def _get_shared_post_for_book(book_id: UUID, user_id: str) -> dict | None:
    result = (
        get_supabase()
        .table("feed_posts")
        .select("id, created_at")
        .eq("book_id", str(book_id))
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .limit(1)
        .execute()
    )
    rows = result.data or []
    return rows[0] if rows else None


def _book_is_shared(book_id: UUID) -> bool:
    result = (
        get_supabase()
        .table("feed_posts")
        .select("id")
        .eq("book_id", str(book_id))
        .limit(1)
        .execute()
    )
    return bool(result.data)


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


def _build_feed_preview(subtitle: str | None, chapters: list[dict], limit: int = 220) -> str:
    subtitle_line = (subtitle or "").strip()
    body = _build_book_body(chapters)

    seed = "\n\n".join(
        part
        for part in (
            subtitle_line,
            body,
        )
        if part
    ).strip()

    normalized = " ".join(seed.split())
    if len(normalized) <= limit:
        return normalized
    return normalized[:limit].rstrip() + " ..."


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
        generated_book = generate_autobiography_book(
            book_title=body.title,
            chapters=ordered_source_chapters,
        )
        polished_chapters = generated_book["chapters"]
        subtitle = str(generated_book.get("subtitle") or "").strip() or generate_book_subtitle(
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

    return _build_book_detail_response(created.data[0])


@router.post(
    "/autobiography",
    response_model=AutobiographyCreateResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_autobiography(
    body: AutobiographyCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    created = _generate_and_store_autobiography(body, user_id)
    return AutobiographyCreateResponse(book_id=created["id"])


@router.get("/{book_id}/share-status", response_model=BookShareStatusResponse)
async def get_book_share_status(
    book_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    _get_book_or_404(book_id, user_id)
    shared_post = _get_shared_post_for_book(book_id, user_id)
    if not shared_post:
        return BookShareStatusResponse(shared=False, post_id=None)
    return BookShareStatusResponse(
        shared=True,
        post_id=shared_post["id"],
    )


@router.get("/{book_id}/shared", response_model=BookDetailResponse)
async def get_shared_book(
    book_id: UUID,
    _: str = Depends(get_current_user_id),
):
    if not _book_is_shared(book_id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="공유된 자서전을 찾을 수 없습니다.",
        )

    result = (
        get_supabase()
        .table("book_versions")
        .select("*")
        .eq("id", str(book_id))
        .maybe_single()
        .execute()
    )
    if not result.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="공유된 자서전을 찾을 수 없습니다.",
        )
    return _build_book_detail_response(result.data, shared=True)


@router.post(
    "/{book_id}/share",
    response_model=BookShareResponse,
    status_code=status.HTTP_201_CREATED,
)
async def share_book(
    book_id: UUID,
    user_id: str = Depends(get_current_user_id),
):
    book = _get_book_or_404(book_id, user_id)
    shared_post = _get_shared_post_for_book(book_id, user_id)
    if shared_post:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="이미 공유된 자서전입니다.",
        )

    preview = _build_feed_preview(
        subtitle=book.get("subtitle"),
        chapters=book.get("chapters") or [],
    )
    safety = check_content_safety(f"{book.get('title', '')}\n{preview}")
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
                    "book_id": str(book["id"]),
                    "title": book["title"],
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

    return BookShareResponse(
        book_id=book["id"],
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
    book = _get_book_or_404(book_id, user_id)
    shared_post = _get_shared_post_for_book(book_id, user_id)
    return _build_book_detail_response(
        book,
        shared=shared_post is not None,
        shared_post_id=shared_post["id"] if shared_post else None,
    )


@router.put("/{book_id}", response_model=BookDetailResponse)
async def update_book(
    book_id: UUID,
    body: BookUpdateRequest,
    user_id: str = Depends(get_current_user_id),
):
    book = _get_book_or_404(book_id, user_id)
    if _get_shared_post_for_book(book_id, user_id):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="이미 공유된 자서전은 수정할 수 없어요.",
        )

    normalized_title = body.title.strip()
    normalized_subtitle = (body.subtitle or "").strip() or None
    normalized_body = body.body.strip()
    if not normalized_title or not normalized_body:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="제목과 자서전 본문을 모두 입력해주세요.",
        )

    safety = check_content_safety(
        "\n".join(part for part in (normalized_title, normalized_subtitle or "", normalized_body) if part)
    )
    if not safety["safe"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"부적절한 콘텐츠가 감지되었습니다: {safety['reason']}",
        )

    updated_chapters = _upsert_book_body_override(book.get("chapters") or [], normalized_body)

    try:
        updated = (
            get_supabase()
            .table("book_versions")
            .update(
                {
                    "title": normalized_title,
                    "subtitle": normalized_subtitle,
                    "chapters": updated_chapters,
                }
            )
            .eq("id", str(book_id))
            .eq("user_id", user_id)
            .execute()
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"자서전 수정 중 오류가 발생했습니다: {exc}",
        ) from exc

    if not updated.data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="자서전을 찾을 수 없습니다.",
        )

    return _build_book_detail_response(updated.data[0])


def _get_raw_book_or_404(book_id: UUID, user_id: str) -> dict:
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
    return result.data


def _fetch_author_name(user_id: str) -> str:
    try:
        result = (
            get_supabase()
            .table("profiles")
            .select("name")
            .eq("id", user_id)
            .maybe_single()
            .execute()
        )
    except Exception:
        return "익명"
    name = (result.data or {}).get("name") if result and result.data else None
    return (name or "").strip() or "익명"


def _ensure_subtitle(raw_book: dict, user_id: str) -> str | None:
    existing = (raw_book.get("subtitle") or "").strip()
    if existing:
        return existing

    visible_chapters = _visible_book_chapters(raw_book.get("chapters") or [])
    chapter_titles = [
        str(chapter.get("title") or "").strip()
        for chapter in visible_chapters
        if str(chapter.get("title") or "").strip()
    ]
    if not chapter_titles:
        return None

    try:
        generated = generate_book_subtitle(
            book_title=str(raw_book.get("title") or "").strip() or "자서전",
            chapter_titles=chapter_titles,
        )
    except Exception:
        return None

    generated = (generated or "").strip() or None
    if not generated:
        return None

    if not _book_is_shared(UUID(str(raw_book["id"]))):
        try:
            get_supabase().table("book_versions").update({"subtitle": generated}).eq(
                "id", str(raw_book["id"])
            ).eq("user_id", user_id).execute()
        except Exception:
            pass

    return generated


def _content_disposition_filename(title: str) -> str:
    safe_title = (title or "자서전").strip() or "자서전"
    quoted = urllib.parse.quote(f"{safe_title}.pdf")
    return f"attachment; filename=\"book.pdf\"; filename*=UTF-8''{quoted}"


@router.post("/{book_id}/pdf")
async def export_book_pdf(
    book_id: UUID,
    include_cover: bool = Form(False),
    cover_image: UploadFile | None = File(None),
    user_id: str = Depends(get_current_user_id),
):
    raw_book = _get_raw_book_or_404(book_id, user_id)

    cover_image_data_url: str | None = None
    if include_cover and cover_image is not None:
        if (cover_image.content_type or "").lower() not in ALLOWED_IMAGE_MIME:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="JPEG, PNG, WebP 이미지만 표지로 사용할 수 있어요.",
            )
        image_bytes = await cover_image.read()
        if not image_bytes:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="표지 이미지가 비어 있어요.",
            )
        if len(image_bytes) > MAX_COVER_IMAGE_BYTES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="표지 이미지 크기는 10MB 이하여야 해요.",
            )
        try:
            cover_image_data_url = prepare_cover_image_data_url(
                image_bytes=image_bytes,
                mime_type=cover_image.content_type,
            )
        except ValueError as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=str(exc),
            ) from exc

    subtitle = _ensure_subtitle(raw_book, user_id)
    author_name = _fetch_author_name(user_id)

    try:
        pdf_bytes = render_book_pdf(
            title=str(raw_book.get("title") or "").strip() or "자서전",
            subtitle=subtitle,
            chapters=list(raw_book.get("chapters") or []),
            author_name=author_name,
            created_at=raw_book.get("created_at"),
            include_cover=include_cover,
            cover_image_data_url=cover_image_data_url,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"PDF 생성 중 오류가 발생했습니다: {exc}",
        ) from exc

    return StreamingResponse(
        BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={
            "Content-Disposition": _content_disposition_filename(
                str(raw_book.get("title") or "자서전")
            ),
            "Content-Length": str(len(pdf_bytes)),
        },
    )
