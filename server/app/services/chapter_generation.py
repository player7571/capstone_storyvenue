from functools import lru_cache
from textwrap import dedent
from typing import Literal

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings

ChapterType = Literal["childhood", "youth", "career", "love", "reflection"]

SYSTEM_PROMPT = dedent(
    """
    당신은 인터뷰를 한 편의 자서전 산문으로 빚어내는 한국의 베스트셀러 작가입니다.
    인터뷰 내용을 바탕으로 따뜻하고 감동적인 자서전 챕터를 작성하세요.
    반드시 한국어로 작성하고, 처음부터 끝까지 1인칭 시점을 유지하세요.
    결과물은 단순 요약문이 아니라 장면과 감정, 관계의 온기가 살아 있는 문학적인 산문이어야 합니다.
    다만 인터뷰에 없는 사실을 함부로 지어내거나 과장하지 말고, 주어진 정보 안에서만 섬세하게 서사화하세요.
    문장은 자연스럽고 매끄럽게 이어지게 쓰고, 목록형 표현이나 기계적인 설명투는 피하세요.
    제목은 짧고 여운 있게 작성하고, 본문은 최소 600자 이상으로 작성하세요.
    """
).strip()

CHAPTER_TONE_GUIDE: dict[ChapterType, str] = {
    "childhood": "어린 시절의 풍경, 가족, 처음의 감정을 섬세하고 포근하게 담아냅니다.",
    "youth": "성장통, 꿈, 방황과 설렘이 함께 느껴지는 청춘의 결을 살립니다.",
    "career": "일과 책임, 성취와 좌절, 삶의 무게가 드러나도록 차분하고 진중하게 씁니다.",
    "love": "사람과 사람 사이의 애정, 관계의 온기, 그리움과 감사가 느껴지도록 씁니다.",
    "reflection": "지나온 삶을 돌아보며 의미와 깨달음이 잔잔하게 남도록 성찰적으로 씁니다.",
}

CHAPTER_FOCUS_GUIDE: dict[ChapterType, str] = {
    "childhood": dedent(
        """
        - 공간과 계절, 냄새, 소리처럼 어린 시절의 감각을 살립니다.
        - 가족이나 주변 사람과의 관계에서 비롯된 감정을 중심축으로 삼습니다.
        - 순수함과 그 시절만의 작은 세계가 드러나게 마무리합니다.
        """
    ).strip(),
    "youth": dedent(
        """
        - 꿈과 불안, 설렘과 방황이 교차하는 순간을 구체적으로 포착합니다.
        - 선택과 흔들림이 어떻게 성장으로 이어졌는지 흐름을 만듭니다.
        - 미완의 마음과 앞으로 나아가려는 힘이 함께 느껴지게 씁니다.
        """
    ).strip(),
    "career": dedent(
        """
        - 일터의 분위기와 책임의 무게가 느껴지는 장면을 중심으로 씁니다.
        - 성취만이 아니라 망설임, 부담, 배움의 순간도 균형 있게 담습니다.
        - 결국 나를 버티게 한 가치나 태도가 드러나도록 정리합니다.
        """
    ).strip(),
    "love": dedent(
        """
        - 특정 인물과 주고받은 마음의 결을 세심하게 묘사합니다.
        - 거창한 표현보다 진심이 배어나는 장면과 말투를 우선합니다.
        - 사랑이 남긴 온기, 그리움, 감사 중 하나 이상의 여운이 남게 씁니다.
        """
    ).strip(),
    "reflection": dedent(
        """
        - 삶을 돌아보며 지금의 내가 붙들고 있는 의미를 차분하게 풀어냅니다.
        - 과거의 사건과 현재의 시선을 자연스럽게 연결합니다.
        - 독자가 함께 숨을 고를 수 있을 만큼 잔잔하고 깊은 마무리를 만듭니다.
        """
    ).strip(),
}

MINIMUM_CHAPTER_LENGTH = 600
RETRY_MINIMUM_LENGTH = 700


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

    for message in conversation_history:
        role = str(message.get("role", "")).strip().lower()
        content = str(message.get("content", "")).strip()
        if role != "user" or not content:
            continue
        lines.append(f"사용자: {content}")

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
    focus_guide = CHAPTER_FOCUS_GUIDE[chapter_type]
    transcript = _format_conversation_history(conversation_history)
    name = user_name.strip() or "사용자"

    return dedent(
        f"""
        아래 인터뷰 대화를 바탕으로 {name}의 자서전 챕터를 작성하세요.

        챕터 유형: {chapter_type}
        챕터 톤 가이드: {tone_guide}
        챕터 집중 포인트:
        {focus_guide}

        작성 조건:
        - 제목은 한국어 한 줄로 작성합니다.
        - 본문은 반드시 한국어로 작성합니다.
        - 본문은 1인칭 시점으로 씁니다.
        - 본문은 최소 {minimum_length}자 이상으로 작성합니다.
        - 현재 질문 답변에 담긴 장면과 감정을 중심으로 씁니다.
        - 이전 질문 참고 내용이 있더라도 현재 질문을 보조하는 정도로만 사용합니다.
        - 인터뷰에 나온 사실과 감정을 중심으로 장면이 보이듯 자연스럽게 서사화합니다.
        - 가능하면 대화에 등장한 인물, 장소, 시기, 사건을 구체적으로 살립니다.
        - 감정의 변화가 드러나도록 쓰되, 과장된 교훈이나 진부한 문구는 피합니다.
        - 문단은 자연스럽게 3~5개 정도로 나누고, 마지막은 짧은 여운이나 깨달음으로 맺습니다.
        - 답변이 짧거나 정보가 적어도, 없는 사실은 만들지 말고 주어진 정보 안에서만 조심스럽게 씁니다.
        - 인터뷰어의 질문을 반복 요약하지 말고, 화자의 삶과 기억이 중심이 되게 씁니다.

        인터뷰 대화:
        {transcript}
        """
    ).strip()


def _request_chapter_content(prompt: str) -> ChapterContent:
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=SYSTEM_PROMPT,
        input=prompt,
        temperature=0.85,
        text_format=ChapterContent,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("챕터 생성 응답을 해석하지 못했습니다.")

    return parsed


def _build_expansion_prompt(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
    minimum_length: int,
    current_title: str,
    current_content: str,
) -> str:
    tone_guide = CHAPTER_TONE_GUIDE[chapter_type]
    focus_guide = CHAPTER_FOCUS_GUIDE[chapter_type]
    transcript = _format_conversation_history(conversation_history)
    name = user_name.strip() or "사용자"

    return dedent(
        f"""
        아래는 {name}의 자서전 챕터 초안입니다.
        이 초안의 사실관계와 감정선은 유지하면서, 내용이 짧은 부분을 자연스럽게 확장해
        최소 {minimum_length}자 이상의 완성된 자서전 산문으로 다듬어주세요.

        챕터 유형: {chapter_type}
        챕터 톤 가이드: {tone_guide}
        챕터 집중 포인트:
        {focus_guide}

        확장 규칙:
        - 제목은 기존 제목을 유지하거나, 더 자연스러우면 비슷한 결로만 다듬습니다.
        - 본문은 반드시 한국어, 1인칭 시점으로 작성합니다.
        - 기존 초안에 없는 사실을 새로 만들지 않습니다.
        - 인터뷰에 나온 장면, 감정, 관계를 더 또렷하게 풀어내며 분량을 늘립니다.
        - 현재 질문 답변을 중심으로 쓰고, 참고 정보는 보조적으로만 사용합니다.
        - 문단은 자연스럽게 3~5개 정도로 유지합니다.
        - 마지막은 짧은 여운이나 깨달음으로 마무리합니다.

        현재 초안 제목:
        {current_title}

        현재 초안 본문:
        {current_content}

        인터뷰 대화:
        {transcript}
        """
    ).strip()


def _expand_short_chapter_content(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
    minimum_length: int,
    current_title: str,
    current_content: str,
) -> ChapterContent:
    prompt = _build_expansion_prompt(
        conversation_history=conversation_history,
        chapter_type=chapter_type,
        user_name=user_name,
        minimum_length=minimum_length,
        current_title=current_title,
        current_content=current_content,
    )
    return _request_chapter_content(prompt)


def generate_chapter_content(
    conversation_history: list[dict],
    chapter_type: ChapterType,
    user_name: str,
) -> dict[str, str]:
    if chapter_type not in CHAPTER_TONE_GUIDE:
        allowed = ", ".join(CHAPTER_TONE_GUIDE)
        raise ValueError(f"chapter_type은 다음 중 하나여야 합니다: {allowed}")

    minimum_length = MINIMUM_CHAPTER_LENGTH
    best_title = ""
    best_content = ""

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

        if len(content) > len(best_content):
            best_title = title
            best_content = content

        if title and len(content) >= minimum_length:
            return {
                "title": title,
                "content": content,
            }

        minimum_length = RETRY_MINIMUM_LENGTH

    if best_title and best_content:
        for expansion_minimum in (MINIMUM_CHAPTER_LENGTH, RETRY_MINIMUM_LENGTH):
            expanded = _expand_short_chapter_content(
                conversation_history=conversation_history,
                chapter_type=chapter_type,
                user_name=user_name,
                minimum_length=expansion_minimum,
                current_title=best_title,
                current_content=best_content,
            )
            title = expanded.title.strip()
            content = expanded.content.strip()

            if len(content) > len(best_content):
                best_title = title
                best_content = content

            if title and len(content) >= MINIMUM_CHAPTER_LENGTH:
                return {
                    "title": title,
                    "content": content,
                }

    raise RuntimeError("생성된 챕터가 길이 조건을 충족하지 못했습니다.")
