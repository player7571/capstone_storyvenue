from functools import lru_cache
from textwrap import dedent
from typing import Literal

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings

ChapterType = Literal["childhood", "youth", "career", "love", "reflection"]

SYSTEM_PROMPT = dedent(
    """
    당신은 한국의 베스트셀러 작가입니다.
    인터뷰 내용을 바탕으로 따뜻하고 감동적인 자서전 챕터를 작성합니다.
    반드시 한국어로 작성하고, 1인칭 시점을 유지하세요.
    문학적 표현을 사용하되 과장되거나 허구적인 내용을 함부로 지어내지 마세요.
    본문은 최소 600자 이상이어야 하며, 제목과 본문을 함께 작성하세요.
    """
).strip()

CHAPTER_TONE_GUIDE: dict[ChapterType, str] = {
    "childhood": "어린 시절의 풍경, 가족, 처음의 감정을 섬세하고 포근하게 담아냅니다.",
    "youth": "성장통, 꿈, 방황과 설렘이 함께 느껴지는 청춘의 결을 살립니다.",
    "career": "일과 책임, 성취와 좌절, 삶의 무게가 드러나도록 차분하고 진중하게 씁니다.",
    "love": "사람과 사람 사이의 애정, 관계의 온기, 그리움과 감사가 느껴지도록 씁니다.",
    "reflection": "지나온 삶을 돌아보며 의미와 깨달음이 잔잔하게 남도록 성찰적으로 씁니다.",
}


class ChapterContent(BaseModel):
    title: str = Field(min_length=1)
    content: str = Field(min_length=1)


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _format_conversation_history(conversation_history: list[dict]) -> str:
    lines: list[str] = []
    speaker_map = {
        "user": "사용자",
        "assistant": "인터뷰어",
    }

    for message in conversation_history:
        role = str(message.get("role", "")).strip().lower()
        content = str(message.get("content", "")).strip()
        if not content:
            continue
        speaker = speaker_map.get(role, role or "알 수 없음")
        lines.append(f"{speaker}: {content}")

    if not lines:
        raise ValueError("유효한 대화 내용이 없습니다.")

    return "\n".join(lines)


def _build_generation_prompt(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
    minimum_length: int,
) -> str:
    tone_guide = CHAPTER_TONE_GUIDE[chapter_type]
    transcript = _format_conversation_history(conversation_history)
    name = user_name.strip() or "사용자"

    return dedent(
        f"""
        아래 인터뷰 대화를 바탕으로 {name}의 자서전 챕터를 작성하세요.

        챕터 유형: {chapter_type}
        챕터 톤 가이드: {tone_guide}

        작성 조건:
        - 제목은 한국어 한 줄로 작성합니다.
        - 본문은 반드시 한국어로 작성합니다.
        - 본문은 1인칭 시점으로 씁니다.
        - 본문은 최소 {minimum_length}자 이상으로 작성합니다.
        - 인터뷰에 나온 사실과 감정을 중심으로 자연스럽게 서사화합니다.
        - 대화에 없는 정보는 과도하게 만들어내지 않습니다.

        인터뷰 대화:
        {transcript}
        """
    ).strip()


def _request_chapter_content(prompt: str) -> ChapterContent:
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=SYSTEM_PROMPT,
        input=prompt,
        temperature=0.8,
        text_format=ChapterContent,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("챕터 생성 응답을 해석하지 못했습니다.")

    return parsed


def generate_chapter_content(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
) -> dict[str, str]:
    if chapter_type not in CHAPTER_TONE_GUIDE:
        allowed = ", ".join(CHAPTER_TONE_GUIDE)
        raise ValueError(f"chapter_type은 다음 중 하나여야 합니다: {allowed}")

    minimum_length = 600

    for _ in range(2):
        prompt = _build_generation_prompt(
            conversation_history=conversation_history,
            chapter_type=chapter_type,
            user_name=user_name,
            minimum_length=minimum_length,
        )
        chapter = _request_chapter_content(prompt)
        title = chapter.title.strip()
        content = chapter.content.strip()

        if title and len(content) >= minimum_length:
            return {
                "title": title,
                "content": content,
            }

        minimum_length = 700

    raise RuntimeError("생성된 챕터가 길이 조건을 충족하지 못했습니다.")
