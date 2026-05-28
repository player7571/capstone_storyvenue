from __future__ import annotations

import base64
import io
import logging
import re
from datetime import date, datetime
from pathlib import Path
from urllib.request import Request, urlopen

logger = logging.getLogger(__name__)

from jinja2 import Environment, FileSystemLoader, select_autoescape
from PIL import Image
from weasyprint import HTML

ASSETS_DIR = Path(__file__).resolve().parent.parent / "assets" / "pdf"
TEMPLATE_NAME = "book_template.html"
CSS_NAME = "book.css"

OVERRIDE_CHAPTER_TITLE_SENTINEL = "__edited_body__"
OVERRIDE_CHAPTER_SOURCE_NO = 0

MAX_COVER_IMAGE_PIXELS = 1600
MAX_CHAPTER_PHOTO_PIXELS = 1200
MAX_CHAPTER_PHOTO_BYTES = 10 * 1024 * 1024
CHAPTER_PHOTO_FETCH_TIMEOUT_SECONDS = 8
ALLOWED_IMAGE_MIME = {"image/jpeg", "image/png", "image/webp"}

_PARAGRAPH_SPLIT = re.compile(r"\n\s*\n+")

_jinja_env = Environment(
    loader=FileSystemLoader(str(ASSETS_DIR)),
    autoescape=select_autoescape(["html"]),
    trim_blocks=True,
    lstrip_blocks=True,
)


def _split_paragraphs(text: str) -> list[str]:
    if not text:
        return []
    parts = _PARAGRAPH_SPLIT.split(text.strip())
    return [part.strip() for part in parts if part.strip()]


def _prepare_chapter_photo_data_url(photo_url: str) -> str | None:
    cleaned = (photo_url or "").strip()
    if not cleaned:
        return None
    try:
        request = Request(cleaned, headers={"User-Agent": "storyvenue-pdf/1.0"})
        with urlopen(request, timeout=CHAPTER_PHOTO_FETCH_TIMEOUT_SECONDS) as response:
            image_bytes = response.read(MAX_CHAPTER_PHOTO_BYTES + 1)
    except Exception as exc:
        logger.warning("chapter photo fetch failed url=%s err=%s", cleaned[:80], exc)
        return None
    if not image_bytes or len(image_bytes) > MAX_CHAPTER_PHOTO_BYTES:
        logger.warning("chapter photo empty or too large url=%s size=%d", cleaned[:80], len(image_bytes or b""))
        return None

    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            img.load()
            if img.mode == "RGBA":
                background = Image.new("RGB", img.size, (255, 255, 255))
                background.paste(img, mask=img.split()[-1])
                img = background
            elif img.mode != "RGB":
                img = img.convert("RGB")

            img.thumbnail(
                (MAX_CHAPTER_PHOTO_PIXELS, MAX_CHAPTER_PHOTO_PIXELS),
                Image.LANCZOS,
            )

            buffer = io.BytesIO()
            img.save(buffer, format="JPEG", quality=82, optimize=True)
            encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    except Exception as exc:
        logger.warning("chapter photo process failed url=%s err=%s", cleaned[:80], exc)
        return None

    return f"data:image/jpeg;base64,{encoded}"


def _normalize_chapters_for_pdf(raw_chapters: list[dict]) -> list[dict]:
    override = next(
        (
            chapter for chapter in raw_chapters
            if str(chapter.get("title") or "").strip() == OVERRIDE_CHAPTER_TITLE_SENTINEL
            and int(chapter.get("source_question_no") or 0) == OVERRIDE_CHAPTER_SOURCE_NO
        ),
        None,
    )

    if override is not None:
        body = str(override.get("content") or "").strip()
        if body:
            return [{"title": "본문", "paragraphs": _split_paragraphs(body)}]

    visible = [
        chapter for chapter in raw_chapters
        if not (
            str(chapter.get("title") or "").strip() == OVERRIDE_CHAPTER_TITLE_SENTINEL
            and int(chapter.get("source_question_no") or 0) == OVERRIDE_CHAPTER_SOURCE_NO
        )
    ]
    visible.sort(key=lambda c: int(c.get("source_question_no") or 2**31 - 1))

    return [
        {
            "title": str(chapter.get("title") or "").strip() or "이야기",
            "paragraphs": _split_paragraphs(str(chapter.get("content") or "")),
            "photo_data_url": _prepare_chapter_photo_data_url(
                str(chapter.get("photo_url") or "")
            ),
        }
        for chapter in visible
    ]


def _format_korean_date(value: str | datetime | date | None) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        cleaned = value.replace("Z", "+00:00")
        try:
            parsed = datetime.fromisoformat(cleaned)
        except ValueError:
            return value
    elif isinstance(value, datetime):
        parsed = value
    elif isinstance(value, date):
        return f"{value.year}년 {value.month}월 {value.day}일"
    else:
        return str(value)
    return f"{parsed.year}년 {parsed.month}월 {parsed.day}일"


def prepare_cover_image_data_url(image_bytes: bytes, mime_type: str | None) -> str:
    if (mime_type or "").lower() not in ALLOWED_IMAGE_MIME:
        raise ValueError("지원하지 않는 이미지 형식입니다. JPEG, PNG, WebP만 가능합니다.")

    with Image.open(io.BytesIO(image_bytes)) as img:
        img.load()
        if img.mode not in ("RGB", "RGBA"):
            img = img.convert("RGB")
        elif img.mode == "RGBA":
            background = Image.new("RGB", img.size, (255, 255, 255))
            background.paste(img, mask=img.split()[-1])
            img = background

        img.thumbnail((MAX_COVER_IMAGE_PIXELS, MAX_COVER_IMAGE_PIXELS), Image.LANCZOS)

        buffer = io.BytesIO()
        img.save(buffer, format="JPEG", quality=85, optimize=True)
        encoded = base64.b64encode(buffer.getvalue()).decode("ascii")

    return f"data:image/jpeg;base64,{encoded}"


def render_book_pdf(
    *,
    title: str,
    subtitle: str | None,
    chapters: list[dict],
    author_name: str,
    created_at: str | datetime | date | None,
    include_cover: bool,
    cover_image_data_url: str | None,
) -> bytes:
    normalized_chapters = _normalize_chapters_for_pdf(chapters)
    css_text = (ASSETS_DIR / CSS_NAME).read_text(encoding="utf-8")

    template = _jinja_env.get_template(TEMPLATE_NAME)
    html_text = template.render(
        title=title.strip() or "자서전",
        subtitle=(subtitle or "").strip() or None,
        author_name=(author_name or "").strip() or "익명",
        created_at_display=_format_korean_date(created_at),
        chapters=normalized_chapters,
        include_cover=include_cover,
        cover_image_data_url=cover_image_data_url if include_cover else None,
        css=css_text,
    )

    pdf_bytes = HTML(string=html_text, base_url=str(ASSETS_DIR)).write_pdf()
    return pdf_bytes
