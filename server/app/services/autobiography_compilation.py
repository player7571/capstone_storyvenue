from functools import lru_cache
from textwrap import dedent
from uuid import UUID

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings


AUTOBIOGRAPHY_SYSTEM_PROMPT = dedent(
    """
    당신은 노년의 삶을 정리하는 자서전 편집자입니다.
    주어진 10개의 이야기 초안을 질문 1번부터 10번까지 순서대로 읽고,
    하나의 자서전으로 자연스럽게 이어지도록 각 챕터의 제목과 본문을 다듬어 주세요.

    반드시 지킬 조건:
    - 새로운 사실을 만들지 않습니다.
    - 이미 나온 사건, 사람, 감정의 핵심은 유지합니다.
    - 1인칭 회고체를 유지합니다.
    - 챕터 간 연결은 부드럽게 하되, 과도한 설명이나 반복은 줄입니다.
    - 각 챕터는 원래 초안의 분량감을 크게 해치지 않도록 유지합니다.
    - 질문 번호 순서를 바꾸지 않습니다.
    - chapters 필드에만 결과를 작성합니다.
    """
).strip()


class AutobiographyChapter(BaseModel):
    id: UUID
    source_question_no: int = Field(ge=1, le=10)
    title: str = Field(min_length=1)
    content: str = Field(min_length=1)


class AutobiographyCompilation(BaseModel):
    chapters: list[AutobiographyChapter] = Field(min_length=10, max_length=10)


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _build_autobiography_prompt(book_title: str, chapters: list[dict]) -> str:
    normalized_title = book_title.strip() or "나의 자서전"
    chapter_blocks: list[str] = []
    for chapter in chapters:
        source_question_no = int(chapter["source_question_no"])
        title = str(chapter.get("title") or "").strip() or f"이야기 {source_question_no}"
        content = str(chapter.get("content") or "").strip()
        chapter_blocks.append(
            dedent(
                f"""
                [이야기 {source_question_no}]
                id: {chapter["id"]}
                제목: {title}
                본문:
                {content}
                """
            ).strip()
        )

    chapter_text = "\n\n".join(chapter_blocks)
    return dedent(
        f"""
        책 제목: {normalized_title}

        아래는 자서전에 들어갈 이야기 1번부터 10번까지의 초안입니다.
        전체 흐름이 자연스럽게 이어지도록 제목과 본문을 다듬어 주세요.

        {chapter_text}

        작성 규칙:
        - chapters는 반드시 10개여야 합니다.
        - source_question_no는 1부터 10까지 각각 한 번씩만 나와야 합니다.
        - 각 chapter의 id는 입력에 들어온 원래 id를 그대로 유지합니다.
        - 이전 챕터의 정서가 다음 챕터로 자연스럽게 이어지게 써 주세요.
        - 사실관계나 인물, 사건을 새로 만들지 마세요.
        """
    ).strip()


def generate_autobiography_chapters(book_title: str, chapters: list[dict]) -> list[dict]:
    ordered_chapters = sorted(chapters, key=lambda chapter: int(chapter["source_question_no"]))
    prompt = _build_autobiography_prompt(book_title=book_title, chapters=ordered_chapters)
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=AUTOBIOGRAPHY_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.4,
        text_format=AutobiographyCompilation,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("자서전 챕터 응답을 해석하지 못했습니다.")

    polished_chapters = [chapter.model_dump(mode="json") for chapter in parsed.chapters]
    question_numbers = [int(chapter["source_question_no"]) for chapter in polished_chapters]
    if question_numbers != list(range(1, 11)):
        raise RuntimeError("자서전 챕터 순서를 올바르게 생성하지 못했습니다.")

    for chapter in polished_chapters:
        chapter["title"] = str(chapter["title"]).strip()
        chapter["content"] = str(chapter["content"]).strip()
        if not chapter["title"] or not chapter["content"]:
            raise RuntimeError("자서전 챕터 생성 결과가 비어 있습니다.")

    return polished_chapters
