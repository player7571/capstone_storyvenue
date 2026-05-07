from functools import lru_cache
from textwrap import dedent
from typing import Literal

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings

ChapterType = Literal["childhood", "youth", "career", "love", "reflection"]

SYSTEM_PROMPT = dedent(
    """
    당신은 인터뷰 내용을 자서전용 질문별 초안으로 정리하는 한국어 편집자입니다.
    인터뷰에 나온 사실만 사용해, 이후에 다시 다듬을 수 있는 짧고 충실한 초안을 작성하세요.
    반드시 한국어로 작성하고, 처음부터 끝까지 1인칭 시점을 유지하세요.
    없는 사건, 인물, 장소, 대화, 시간 흐름, 감정을 새로 만들거나 과장하지 마세요.
    정보가 적으면 짧고 담백한 초안으로 남기고, 억지로 감동이나 교훈을 만들어내지 마세요.
    문장은 메모처럼 끊지 말고 자연스럽게 이어 쓰되, 과하게 문학적으로 꾸미지 마세요.
    제목은 답변의 핵심 기억이나 장면이 드러나게 짧고 분명하게 작성하세요.
    """
).strip()

CHAPTER_TONE_GUIDE: dict[ChapterType, str] = {
    "childhood": "어린 시절의 풍경과 당시의 감정이 드러나도록 차분하게 정리합니다.",
    "youth": "젊은 시절의 생활과 마음이 보이도록 담백하게 정리합니다.",
    "career": "일과 책임, 배움의 순간이 드러나도록 차분하게 정리합니다.",
    "love": "기억에 남는 사람과 관계의 감정이 보이도록 정리합니다.",
    "reflection": "돌아보는 마음과 삶의 의미가 드러나도록 담백하게 정리합니다.",
}

CHAPTER_FOCUS_GUIDE: dict[ChapterType, str] = {
    "childhood": dedent(
        """
        - 공간, 냄새, 소리처럼 답변에 실제로 나온 감각을 우선 살립니다.
        - 가족이나 주변 사람과 연결된 기억이 있으면 함께 정리합니다.
        - 정보가 적으면 짧게 남기고 억지로 분위기를 덧붙이지 않습니다.
        """
    ).strip(),
    "youth": dedent(
        """
        - 당시의 생활, 선택, 감정 중 답변에 나온 부분만 정리합니다.
        - 불안이나 설렘이 언급되면 그대로 살리되 과장하지 않습니다.
        - 성장 서사로 억지로 연결하지 않습니다.
        """
    ).strip(),
    "career": dedent(
        """
        - 일의 내용, 일터 분위기, 책임감처럼 실제 답변에 나온 재료를 우선 씁니다.
        - 성취나 부담, 배움이 언급되면 있는 만큼만 담습니다.
        - 삶의 교훈으로 과하게 정리하지 않습니다.
        """
    ).strip(),
    "love": dedent(
        """
        - 특정 인물과 관련된 장면이나 감정을 실제 답변 안에서만 정리합니다.
        - 거창한 표현보다 관계와 기억이 드러나는 사실을 우선합니다.
        - 감사나 그리움이 있더라도 과하게 부풀리지 않습니다.
        """
    ).strip(),
    "reflection": dedent(
        """
        - 삶을 돌아보는 시선이 있으면 그 표현을 그대로 정리합니다.
        - 과거 사건과 현재 생각이 함께 나오면 자연스럽게 이어 줍니다.
        - 깊은 깨달음이나 결론을 새로 만들지 않습니다.
        """
    ).strip(),
}

DRAFT_HARD_FLOOR = 120


class ChapterContent(BaseModel):
    title: str = Field(min_length=1)
    content: str = Field(min_length=1)


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _collect_user_contents(conversation_history: list[dict]) -> list[str]:
    contents: list[str] = []

    for message in conversation_history:
        role = str(message.get("role", "")).strip().lower()
        content = str(message.get("content", "")).strip()
        if role != "user" or not content:
            continue
        contents.append(content)

    return contents


def _format_conversation_history(conversation_history: list[dict]) -> str:
    lines = [f"사용자: {content}" for content in _collect_user_contents(conversation_history)]

    if not lines:
        raise ValueError("유효한 대화 내용이 없습니다.")

    return "\n".join(lines)


def _extract_user_answer_text(conversation_history: list[dict]) -> str:
    contents = _collect_user_contents(conversation_history)
    if not contents:
        raise ValueError("유효한 대화 내용이 없습니다.")
    return "\n".join(contents).strip()


def _resolve_target_length(answer_text: str) -> tuple[int, int]:
    answer_length = len(answer_text.strip())
    if answer_length < 80:
        return 180, 260
    if answer_length < 180:
        return 250, 380
    return 350, 520


def _build_generation_prompt(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
    target_min: int,
    target_max: int,
) -> str:
    tone_guide = CHAPTER_TONE_GUIDE[chapter_type]
    focus_guide = CHAPTER_FOCUS_GUIDE[chapter_type]
    transcript = _format_conversation_history(conversation_history)
    name = user_name.strip() or "사용자"

    return dedent(
        f"""
        아래 인터뷰 대화를 바탕으로 {name}의 자서전 질문별 초안을 작성하세요.

        챕터 유형: {chapter_type}
        챕터 톤 가이드: {tone_guide}
        챕터 집중 포인트:
        {focus_guide}

        작성 조건:
        - 제목은 한국어 한 줄로 작성합니다.
        - 제목은 지나치게 시적이거나 추상적으로 쓰지 말고, 핵심 기억이나 장면이 드러나게 씁니다.
        - 본문은 반드시 한국어로 작성합니다.
        - 본문은 1인칭 시점으로 씁니다.
        - 현재 질문 답변에 나온 사실, 장면, 감정만 바탕으로 씁니다.
        - 다른 질문에서 나온 내용이나 맥락은 사용하지 않습니다.
        - 답변에 없는 사건, 인물, 장소, 대화, 시간 흐름, 감정을 새로 만들지 않습니다.
        - 본문은 보통 {target_min}자에서 {target_max}자 안팎의 짧은 초안을 목표로 하되, 답변이 짧으면 그보다 짧아도 됩니다.
        - 정보가 적으면 1~2문단의 짧고 담백한 초안으로 남기고, 억지로 분량을 늘리지 않습니다.
        - 메모처럼 끊지 말고, 읽히는 산문으로 자연스럽게 정리합니다.
        - 과장된 교훈, 진부한 감동, 확신 없는 해석은 쓰지 않습니다.
        - 인터뷰어의 질문을 반복 요약하지 말고, 화자의 기억과 말이 중심이 되게 씁니다.

        인터뷰 대화:
        {transcript}
        """
    ).strip()


def _request_chapter_content(prompt: str) -> ChapterContent:
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=SYSTEM_PROMPT,
        input=prompt,
        temperature=0.4,
        text_format=ChapterContent,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("챕터 생성 응답을 해석하지 못했습니다.")

    return parsed


def _build_short_retry_prompt(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
    current_title: str,
    current_content: str,
    target_min: int,
    target_max: int,
) -> str:
    tone_guide = CHAPTER_TONE_GUIDE[chapter_type]
    focus_guide = CHAPTER_FOCUS_GUIDE[chapter_type]
    transcript = _format_conversation_history(conversation_history)
    name = user_name.strip() or "사용자"

    return dedent(
        f"""
        아래는 {name}의 자서전 질문별 초안입니다.
        새 사실을 추가하지 말고, 현재 초안의 내용만 바탕으로 조금 더 읽기 좋게 정리해주세요.

        챕터 유형: {chapter_type}
        챕터 톤 가이드: {tone_guide}
        챕터 집중 포인트:
        {focus_guide}

        정리 규칙:
        - 제목은 현재 초안의 핵심 장면이나 기억이 드러나는 짧은 제목으로 유지하거나 다듬습니다.
        - 본문은 한국어, 1인칭 시점으로 작성합니다.
        - 현재 초안과 인터뷰 대화에 없는 사건, 인물, 장소, 대화, 시간 흐름, 감정을 추가하지 않습니다.
        - 길이를 억지로 늘리지 말고, 현재 내용만 조금 더 자연스럽게 이어 주세요.
        - 본문은 보통 {max(DRAFT_HARD_FLOOR, target_min)}자에서 {target_max}자 안팎이면 충분합니다.
        - 메모처럼 끊지 말고 담백한 초안 문장으로 정리합니다.
        - 과장된 교훈, 감동, 해석은 넣지 않습니다.

        현재 초안 제목:
        {current_title}

        현재 초안 본문:
        {current_content}

        인터뷰 대화:
        {transcript}
        """
    ).strip()


def generate_chapter_content(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
) -> dict[str, str]:
    if chapter_type not in CHAPTER_TONE_GUIDE:
        allowed = ", ".join(CHAPTER_TONE_GUIDE)
        raise ValueError(f"chapter_type은 다음 중 하나여야 합니다: {allowed}")

    answer_text = _extract_user_answer_text(conversation_history)
    target_min, target_max = _resolve_target_length(answer_text)

    prompt = _build_generation_prompt(
        conversation_history=conversation_history,
        chapter_type=chapter_type,
        user_name=user_name,
        target_min=target_min,
        target_max=target_max,
    )
    chapter = _request_chapter_content(prompt)
    title = chapter.title.strip()
    content = chapter.content.strip()

    if not title or not content:
        raise RuntimeError("생성된 초안이 비어 있습니다.")

    if len(content) < DRAFT_HARD_FLOOR:
        retry_prompt = _build_short_retry_prompt(
            conversation_history=conversation_history,
            chapter_type=chapter_type,
            user_name=user_name,
            current_title=title,
            current_content=content,
            target_min=target_min,
            target_max=target_max,
        )
        retried = _request_chapter_content(retry_prompt)
        retry_title = retried.title.strip()
        retry_content = retried.content.strip()
        if retry_title and len(retry_content) > len(content):
            title = retry_title
            content = retry_content

    return {
        "title": title,
        "content": content,
    }
