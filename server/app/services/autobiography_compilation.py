from functools import lru_cache
from textwrap import dedent
from uuid import UUID

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings


EDITORIAL_MAP_SYSTEM_PROMPT = dedent(
    """
    당신은 노년의 삶을 한 권의 자서전으로 정리하는 편집자입니다.
    주어진 10개의 이야기 초안을 버리거나 요약해 대체하지 말고,
    각 초안의 핵심 사실과 장면을 살리면서 책처럼 읽히게 만들기 위한 편집 지도를 작성하세요.

    반드시 지킬 조건:
    - 새로운 사실을 만들지 않습니다.
    - must_keep_facts에는 초안에 실제로 들어 있는 사실, 장면, 사람, 감정만 적습니다.
    - chapter_role은 책 전체에서 그 챕터가 맡는 기능을 짧게 설명합니다.
    - bridge_from_previous에는 직전 챕터에서 무엇을 이어받아 시작할지 적습니다.
    - bridge_to_next에는 다음 챕터로 어떤 정서나 의미를 넘길지 적습니다.
    - meaning_link에는 이 초안이 삶 전체 흐름 안에서 어떤 의미를 갖는지 적습니다.
    - transition_goal은 앞뒤 챕터와 어떻게 자연스럽게 이어질지 구체적으로 적습니다.
    - expression_upgrade_goal은 표현을 어떤 방향으로 다듬을지 적되, 사실을 보태지 않습니다.
    - repetition_to_reduce에는 반복되기 쉬운 표현이나 정서를 짧게 적습니다.
    - core_values에는 이 삶을 관통하는 가치나 태도를 2개에서 5개까지 적습니다.
    - turning_points에는 삶의 흐름을 바꾸거나 깊게 남은 분기점을 적습니다.
    - chapter_guides는 반드시 10개여야 하며 source_question_no 1부터 10까지 순서를 유지합니다.
    """
).strip()


AUTOBIOGRAPHY_REWRITE_SYSTEM_PROMPT = dedent(
    """
    당신은 노년의 삶을 정리하는 자서전 편집자입니다.
    주어진 질문별 초안 10개와 편집 지도를 바탕으로, 책처럼 읽히는 자서전 챕터 10개를 다시 작성하세요.

    반드시 지킬 조건:
    - 새로운 사실, 사건, 인물, 장소, 시간 흐름을 만들지 않습니다.
    - 각 챕터의 must_keep_facts는 반드시 유지합니다.
    - 1인칭 회고체를 유지합니다.
    - 각 챕터는 독립된 새 글처럼 다시 시작하지 말고, 앞뒤 챕터와 정서와 맥락이 자연스럽게 이어져야 합니다.
    - 각 챕터의 첫 문장은 bridge_from_previous를 반영하되, 앞 문단을 해설하거나 요약하지 않고 자연스럽게 이어집니다.
    - 각 챕터의 마지막 정서는 bridge_to_next를 반영하되, 다음 이야기를 예고하거나 안내하지 않습니다.
    - meaning_link가 문장 안에 은은하게 드러나도록 하되, 교훈글처럼 과장하지 않습니다.
    - 같은 표현, 같은 설명, 같은 감정을 반복하지 않도록 다듬습니다.
    - 초안을 거의 그대로 다시 적지 말고, 그렇다고 초안의 의미를 지우지도 마세요.
    - "다음으로는", "이어서", "이제는", "이 이야기를 이어가겠습니다" 같은 안내형 연결 문장을 쓰지 않습니다.
    - 독자에게 말을 거는 문장이나 책 소개 문구를 넣지 않습니다.
    - 질문 번호 순서는 1부터 10까지 유지합니다.
    - chapters 필드에만 결과를 작성합니다.
    """
).strip()


SINGLE_PASS_SYSTEM_PROMPT = dedent(
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


BOOK_FRAMING_SYSTEM_PROMPT = dedent(
    """
    당신은 한 사람의 삶을 자서전 한 권처럼 마무리하는 편집자입니다.
    이미 다듬어진 자서전 챕터 10개와 삶의 서사 지도를 보고,
    책을 닫는 짧은 에필로그와 책 전체를 소개하는 부제를 작성하세요.

    반드시 지킬 조건:
    - 새로운 사실, 사건, 인물, 장소를 만들지 않습니다.
    - closing_note는 마지막 장면 뒤에 놓여도 어색하지 않은 짧은 1인칭 회고 문단입니다.
    - subtitle은 한국어 한 문장으로 작성합니다.
    - 과장된 광고 문구나 지나치게 시적인 문장은 피합니다.
    - "이 책은", "독자", "이 이야기를 통해", "전하고 싶습니다" 같은 책 소개 문구를 쓰지 않습니다.
    - closing_note와 subtitle 필드에만 결과를 작성합니다.
    """
).strip()


MANUSCRIPT_SMOOTHING_SYSTEM_PROMPT = dedent(
    """
    당신은 이미 다듬어진 자서전 원고를 마지막으로 매만지는 편집자입니다.
    주어진 챕터 순서와 핵심 사실을 유지한 채, 문단과 문단 사이의 연결만 더 자연스럽게 다듬으세요.

    반드시 지킬 조건:
    - 새로운 사실, 사건, 인물, 장소를 만들지 않습니다.
    - 챕터 순서를 바꾸지 않습니다.
    - must_keep_facts가 드러나는 내용은 삭제하지 않습니다.
    - 각 챕터의 첫 문장이 독립된 새 글처럼 다시 시작하지 않게 다듬습니다.
    - 반복되는 도입 표현, 감정 표현, 설명을 줄입니다.
    - 연결만 다듬고, 핵심 장면과 의미는 유지합니다.
    - "다음으로는", "이어서", "이제는", "이 책은", "독자" 같은 메타 문장을 쓰지 않습니다.
    - chapters 필드에만 결과를 작성합니다.
    """
).strip()


class ChapterEditorialGuide(BaseModel):
    id: UUID
    source_question_no: int = Field(ge=1, le=10)
    chapter_role: str = Field(min_length=1)
    must_keep_facts: list[str] = Field(min_length=1, max_length=6)
    key_scene: str | None = None
    bridge_from_previous: str = Field(min_length=1)
    bridge_to_next: str = Field(min_length=1)
    meaning_link: str = Field(min_length=1)
    transition_goal: str = Field(min_length=1)
    expression_upgrade_goal: str = Field(min_length=1)
    repetition_to_reduce: list[str] = Field(default_factory=list, max_length=4)


class AutobiographyEditorialMap(BaseModel):
    life_arc_summary: str = Field(min_length=1)
    voice_guide: str = Field(min_length=1)
    core_values: list[str] = Field(default_factory=list, max_length=5)
    turning_points: list[str] = Field(default_factory=list, max_length=6)
    continuity_notes: list[str] = Field(default_factory=list, max_length=6)
    chapter_guides: list[ChapterEditorialGuide] = Field(min_length=10, max_length=10)


class AutobiographyChapter(BaseModel):
    id: UUID
    source_question_no: int = Field(ge=1, le=10)
    title: str = Field(min_length=1)
    content: str = Field(min_length=1)


class AutobiographyCompilation(BaseModel):
    chapters: list[AutobiographyChapter] = Field(min_length=10, max_length=10)


class BookFraming(BaseModel):
    subtitle: str = Field(min_length=1)
    closing_note: str = Field(min_length=1)


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _normalize_title(source_question_no: int, title: str) -> str:
    normalized = title.strip()
    return normalized or f"이야기 {source_question_no}"


def _build_chapter_blocks(chapters: list[dict]) -> str:
    chapter_blocks: list[str] = []
    for chapter in chapters:
        source_question_no = int(chapter["source_question_no"])
        title = _normalize_title(source_question_no, str(chapter.get("title") or ""))
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
    return "\n\n".join(chapter_blocks)


def _build_editorial_map_prompt(book_title: str, chapters: list[dict]) -> str:
    normalized_title = book_title.strip() or "나의 자서전"
    chapter_text = _build_chapter_blocks(chapters)
    return dedent(
        f"""
        책 제목: {normalized_title}

        아래는 자서전에 들어갈 이야기 1번부터 10번까지의 초안입니다.
        초안을 버리거나 요약본으로 대체하지 말고, 각 초안의 핵심 사실과 장면을 살릴 수 있는 편집 지도를 만들어 주세요.

        {chapter_text}

        작성 규칙:
        - life_arc_summary에는 이 10개 초안이 보여주는 삶의 흐름을 짧게 요약합니다.
        - voice_guide에는 이 책이 어떤 어조와 분위기로 읽혀야 하는지 적습니다.
        - core_values에는 이 삶을 관통하는 가치나 태도를 2개 이상 적습니다.
        - turning_points에는 삶의 분기점이나 중요한 전환을 적습니다.
        - continuity_notes에는 책 전체의 연결에서 유의할 점을 적습니다.
        - chapter_guides는 10개여야 합니다.
        - must_keep_facts는 반드시 원래 초안에 실제로 있는 요소만 적습니다.
        - bridge_from_previous, bridge_to_next, meaning_link를 모두 작성합니다.
        - chapter_guides는 source_question_no 1부터 10까지 순서를 유지합니다.
        """
    ).strip()


def _build_autobiography_rewrite_prompt(
    book_title: str,
    chapters: list[dict],
    editorial_map: AutobiographyEditorialMap,
) -> str:
    normalized_title = book_title.strip() or "나의 자서전"
    chapter_text = _build_chapter_blocks(chapters)

    continuity_notes = "\n".join(f"- {note}" for note in editorial_map.continuity_notes) or "- 없음"
    core_values = "\n".join(f"- {value}" for value in editorial_map.core_values) or "- 없음"
    turning_points = "\n".join(f"- {point}" for point in editorial_map.turning_points) or "- 없음"

    guide_blocks: list[str] = []
    for guide in editorial_map.chapter_guides:
        must_keep_lines = "\n".join(f"- {fact}" for fact in guide.must_keep_facts)
        repetition_lines = (
            "\n".join(f"- {item}" for item in guide.repetition_to_reduce)
            if guide.repetition_to_reduce
            else "- 없음"
        )
        guide_blocks.append(
            dedent(
                f"""
                [가이드 {guide.source_question_no}]
                id: {guide.id}
                source_question_no: {guide.source_question_no}
                chapter_role: {guide.chapter_role}
                must_keep_facts:
                {must_keep_lines}
                key_scene: {guide.key_scene or "없음"}
                bridge_from_previous: {guide.bridge_from_previous}
                bridge_to_next: {guide.bridge_to_next}
                meaning_link: {guide.meaning_link}
                transition_goal: {guide.transition_goal}
                expression_upgrade_goal: {guide.expression_upgrade_goal}
                repetition_to_reduce:
                {repetition_lines}
                """
            ).strip()
        )

    guide_text = "\n\n".join(guide_blocks)

    return dedent(
        f"""
        책 제목: {normalized_title}

        [편집 지도 - 책 전체]
        인생 흐름 요약:
        {editorial_map.life_arc_summary}

        문체 가이드:
        {editorial_map.voice_guide}

        핵심 가치:
        {core_values}

        삶의 분기점:
        {turning_points}

        연결 메모:
        {continuity_notes}

        [편집 지도 - 챕터별]
        {guide_text}

        [원래 초안]
        {chapter_text}

        작성 규칙:
        - chapters는 반드시 10개여야 합니다.
        - source_question_no는 1부터 10까지 한 번씩만 나와야 합니다.
        - 각 chapter의 id는 입력과 동일해야 합니다.
        - must_keep_facts는 모두 반영해야 합니다.
        - 각 챕터는 책처럼 자연스럽게 이어지도록 다시 쓰되, 새 사실은 추가하지 않습니다.
        - bridge_from_previous와 bridge_to_next를 의식해 문단 첫머리와 끝맺음을 조정합니다.
        - meaning_link가 독자의 눈에 자연스럽게 느껴지도록 회고의 시선을 더합니다.
        - 같은 출발 문장이나 같은 감정 설명을 반복하지 않도록 표현을 조정합니다.
        - 장면에서 장면으로 이어질 뿐, 다음 이야기를 설명하거나 소개하는 문장을 넣지 않습니다.
        - 초안의 핵심 장면과 사실은 약화시키지 않습니다.
        """
    ).strip()


def _build_single_pass_prompt(book_title: str, chapters: list[dict]) -> str:
    normalized_title = book_title.strip() or "나의 자서전"
    chapter_text = _build_chapter_blocks(chapters)
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


def _build_book_framing_prompt(
    book_title: str,
    chapters: list[dict],
    editorial_map: AutobiographyEditorialMap,
) -> str:
    normalized_title = book_title.strip() or "나의 자서전"
    chapter_text = _build_chapter_blocks(chapters)
    continuity_notes = "\n".join(f"- {note}" for note in editorial_map.continuity_notes) or "- 없음"

    return dedent(
        f"""
        책 제목: {normalized_title}

        [삶의 흐름 요약]
        {editorial_map.life_arc_summary}

        [문체 가이드]
        {editorial_map.voice_guide}

        [연결 메모]
        {continuity_notes}

        [다듬어진 자서전 챕터]
        {chapter_text}

        작성 규칙:
        - subtitle은 책 전체를 소개하는 한국어 한 문장입니다.
        - closing_note는 마지막 문단 뒤에 자연스럽게 놓일 짧은 회고 문단입니다.
        - 이미 다듬어진 챕터에 없는 사실을 새로 보태지 않습니다.
        - 담백한 회고체를 유지합니다.
        - 독자에게 직접 설명하거나 책을 소개하는 말투를 피합니다.
        """
    ).strip()


def _build_manuscript_smoothing_prompt(
    book_title: str,
    chapters: list[dict],
    editorial_map: AutobiographyEditorialMap,
) -> str:
    normalized_title = book_title.strip() or "나의 자서전"
    chapter_text = _build_chapter_blocks(chapters)
    continuity_notes = "\n".join(f"- {note}" for note in editorial_map.continuity_notes) or "- 없음"

    guide_blocks: list[str] = []
    for guide in editorial_map.chapter_guides:
        must_keep_lines = "\n".join(f"- {fact}" for fact in guide.must_keep_facts)
        repetition_lines = (
            "\n".join(f"- {item}" for item in guide.repetition_to_reduce)
            if guide.repetition_to_reduce
            else "- 없음"
        )
        guide_blocks.append(
            dedent(
                f"""
                [가이드 {guide.source_question_no}]
                id: {guide.id}
                source_question_no: {guide.source_question_no}
                must_keep_facts:
                {must_keep_lines}
                bridge_from_previous: {guide.bridge_from_previous}
                bridge_to_next: {guide.bridge_to_next}
                meaning_link: {guide.meaning_link}
                repetition_to_reduce:
                {repetition_lines}
                """
            ).strip()
        )

    guide_text = "\n\n".join(guide_blocks)

    return dedent(
        f"""
        책 제목: {normalized_title}

        [삶의 흐름]
        {editorial_map.life_arc_summary}

        [연결 메모]
        {continuity_notes}

        [챕터별 연결 가이드]
        {guide_text}

        [현재 자서전 챕터]
        {chapter_text}

        작성 규칙:
        - 문단 순서와 챕터 개수는 유지합니다.
        - 같은 뜻의 시작 문장이 반복되면 더 자연스럽게 조정합니다.
        - 문단과 문단 사이가 갑자기 끊기지 않도록 연결만 보완합니다.
        - 새로운 사실을 더하거나 장면을 지우지 않습니다.
        - 연결을 위해 설명조 문장을 덧붙이기보다, 문단의 첫머리와 끝맺음을 자연스럽게 정리합니다.
        """
    ).strip()


def _validate_polished_chapters(polished_chapters: list[dict]) -> list[dict]:
    question_numbers = [int(chapter["source_question_no"]) for chapter in polished_chapters]
    if question_numbers != list(range(1, 11)):
        raise RuntimeError("자서전 챕터 순서를 올바르게 생성하지 못했습니다.")

    for chapter in polished_chapters:
        chapter["title"] = str(chapter["title"]).strip()
        chapter["content"] = str(chapter["content"]).strip()
        if not chapter["title"] or not chapter["content"]:
            raise RuntimeError("자서전 챕터 생성 결과가 비어 있습니다.")

    return polished_chapters


def _generate_editorial_map(book_title: str, chapters: list[dict]) -> AutobiographyEditorialMap:
    prompt = _build_editorial_map_prompt(book_title=book_title, chapters=chapters)
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=EDITORIAL_MAP_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.3,
        text_format=AutobiographyEditorialMap,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("자서전 편집 지도를 해석하지 못했습니다.")

    guide_numbers = [guide.source_question_no for guide in parsed.chapter_guides]
    if guide_numbers != list(range(1, 11)):
        raise RuntimeError("자서전 편집 지도의 질문 순서가 올바르지 않습니다.")

    return parsed


def _generate_rewritten_chapters(
    book_title: str,
    chapters: list[dict],
    editorial_map: AutobiographyEditorialMap,
) -> list[dict]:
    prompt = _build_autobiography_rewrite_prompt(
        book_title=book_title,
        chapters=chapters,
        editorial_map=editorial_map,
    )
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=AUTOBIOGRAPHY_REWRITE_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.45,
        text_format=AutobiographyCompilation,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("자서전 챕터 응답을 해석하지 못했습니다.")

    polished_chapters = [chapter.model_dump(mode="json") for chapter in parsed.chapters]
    return _validate_polished_chapters(polished_chapters)


def _generate_single_pass_chapters(book_title: str, chapters: list[dict]) -> list[dict]:
    prompt = _build_single_pass_prompt(book_title=book_title, chapters=chapters)
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=SINGLE_PASS_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.4,
        text_format=AutobiographyCompilation,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("자서전 챕터 응답을 해석하지 못했습니다.")

    polished_chapters = [chapter.model_dump(mode="json") for chapter in parsed.chapters]
    return _validate_polished_chapters(polished_chapters)


def _generate_book_framing(
    book_title: str,
    chapters: list[dict],
    editorial_map: AutobiographyEditorialMap,
) -> BookFraming:
    prompt = _build_book_framing_prompt(
        book_title=book_title,
        chapters=chapters,
        editorial_map=editorial_map,
    )
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=BOOK_FRAMING_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.45,
        text_format=BookFraming,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("자서전 마무리 응답을 해석하지 못했습니다.")

    parsed.subtitle = parsed.subtitle.strip()
    parsed.closing_note = parsed.closing_note.strip()
    if not parsed.subtitle or not parsed.closing_note:
        raise RuntimeError("자서전 마무리 생성 결과가 비어 있습니다.")

    return parsed


def _apply_book_framing(chapters: list[dict], framing: BookFraming) -> list[dict]:
    framed_chapters = [dict(chapter) for chapter in chapters]
    if not framed_chapters:
        return framed_chapters

    last_content = str(framed_chapters[-1]["content"]).strip()
    framed_chapters[-1]["content"] = f"{last_content}\n\n{framing.closing_note}".strip()
    return framed_chapters


def _smooth_autobiography_chapters(
    book_title: str,
    chapters: list[dict],
    editorial_map: AutobiographyEditorialMap,
) -> list[dict]:
    prompt = _build_manuscript_smoothing_prompt(
        book_title=book_title,
        chapters=chapters,
        editorial_map=editorial_map,
    )
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=MANUSCRIPT_SMOOTHING_SYSTEM_PROMPT,
        input=prompt,
        temperature=0.35,
        text_format=AutobiographyCompilation,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("자서전 연결 편집 응답을 해석하지 못했습니다.")

    polished_chapters = [chapter.model_dump(mode="json") for chapter in parsed.chapters]
    return _validate_polished_chapters(polished_chapters)


def generate_autobiography_book(book_title: str, chapters: list[dict]) -> dict:
    ordered_chapters = sorted(chapters, key=lambda chapter: int(chapter["source_question_no"]))
    try:
        editorial_map = _generate_editorial_map(
            book_title=book_title,
            chapters=ordered_chapters,
        )
        rewritten_chapters = _generate_rewritten_chapters(
            book_title=book_title,
            chapters=ordered_chapters,
            editorial_map=editorial_map,
        )
        framing = _generate_book_framing(
            book_title=book_title,
            chapters=rewritten_chapters,
            editorial_map=editorial_map,
        )
        framed_chapters = _apply_book_framing(rewritten_chapters, framing)
        smoothed_chapters = _smooth_autobiography_chapters(
            book_title=book_title,
            chapters=framed_chapters,
            editorial_map=editorial_map,
        )
        return {
            "subtitle": framing.subtitle,
            "chapters": smoothed_chapters,
        }
    except Exception:
        return {
            "subtitle": "",
            "chapters": _generate_single_pass_chapters(
                book_title=book_title,
                chapters=ordered_chapters,
            ),
        }


def generate_autobiography_chapters(book_title: str, chapters: list[dict]) -> list[dict]:
    return generate_autobiography_book(
        book_title=book_title,
        chapters=chapters,
    )["chapters"]
