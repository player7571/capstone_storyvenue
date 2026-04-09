from functools import lru_cache
from textwrap import dedent

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings

SUBTITLE_SYSTEM_PROMPT = dedent(
    """
    당신은 따뜻한 자서전 책 소개 문구를 쓰는 편집자입니다.
    주어진 챕터 제목들을 보고 책 전체를 소개하는 한국어 한 문장을 작성하세요.
    과장된 광고 문구보다는 잔잔하고 감성적인 톤을 유지하세요.
    """
).strip()


class BookSubtitle(BaseModel):
    subtitle: str = Field(min_length=1)


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _build_subtitle_prompt(book_title: str, chapter_titles: list[str]) -> str:
    normalized_book_title = book_title.strip() or "제목 없는 자서전"
    normalized_titles = [title.strip() for title in chapter_titles if title.strip()]
    if not normalized_titles:
        raise ValueError("책 소개글을 생성할 챕터 제목이 없습니다.")

    chapter_lines = "\n".join(f"- {title}" for title in normalized_titles)

    return dedent(
        f"""
        책 제목: {normalized_book_title}

        챕터 제목 목록:
        {chapter_lines}

        위 챕터 제목을 보고 따뜻한 책 소개 한 문장을 써줘.

        작성 조건:
        - 반드시 한국어 한 문장으로 작성합니다.
        - 책의 분위기와 주제를 자연스럽게 드러냅니다.
        - subtitle 필드에만 결과를 작성합니다.
        """
    ).strip()


def generate_book_subtitle(book_title: str, chapter_titles: list[str]) -> str:
    prompt = _build_subtitle_prompt(
        book_title=book_title,
        chapter_titles=chapter_titles,
    )
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=SUBTITLE_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.7,
        text_format=BookSubtitle,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("책 소개글 응답을 해석하지 못했습니다.")

    subtitle = parsed.subtitle.strip()
    if not subtitle:
        raise RuntimeError("책 소개글이 비어 있습니다.")

    return subtitle
