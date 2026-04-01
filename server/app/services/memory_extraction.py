from functools import lru_cache
from textwrap import dedent

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings

SYSTEM_PROMPT = dedent(
    """
    당신은 대화에서 중요한 기억 정보를 정리하는 도우미입니다.
    아래 대화를 읽고 핵심 정보만 추출하세요.
    추출 항목은 인물 이름, 장소, 날짜/연도, 중요한 사건, 감정입니다.
    반드시 한국어로 작성하고, 대화에 없는 정보를 지어내지 마세요.
    """
).strip()


class MemoryExtractionResult(BaseModel):
    people: list[str] = Field(default_factory=list)
    places: list[str] = Field(default_factory=list)
    dates: list[str] = Field(default_factory=list)
    events: list[str] = Field(default_factory=list)
    emotions: list[str] = Field(default_factory=list)


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _format_conversation_history(conversation_history: list[dict]) -> str:
    speaker_map = {
        "user": "사용자",
        "assistant": "인터뷰어",
    }
    lines: list[str] = []

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


def _normalize_items(items: list[str]) -> list[str]:
    seen: set[str] = set()
    normalized: list[str] = []

    for item in items:
        value = str(item).strip()
        if not value or value in seen:
            continue
        seen.add(value)
        normalized.append(value)

    return normalized


def extract_memories(conversation_history: list[dict]) -> dict[str, list[str]]:
    transcript = _format_conversation_history(conversation_history)
    prompt = dedent(
        f"""
        아래 대화에서 중요한 정보를 추출하세요.

        추출 항목:
        - people: 인물 이름
        - places: 장소
        - dates: 날짜, 연도, 시기
        - events: 중요한 사건
        - emotions: 감정

        대화:
        {transcript}
        """
    ).strip()

    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=SYSTEM_PROMPT,
        input=prompt,
        temperature=0.2,
        text_format=MemoryExtractionResult,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("기억 추출 응답을 해석하지 못했습니다.")

    return {
        "people": _normalize_items(parsed.people),
        "places": _normalize_items(parsed.places),
        "dates": _normalize_items(parsed.dates),
        "events": _normalize_items(parsed.events),
        "emotions": _normalize_items(parsed.emotions),
    }
