from textwrap import dedent

from app.services.interview.llm import get_interview_openai_client
from app.services.interview.state import get_question_answers
from app.services.interview.types import (
    InterviewQuestion,
    SlotName,
    VoiceInterviewAssessment,
    VoiceInterviewState,
)

ASSESSMENT_SYSTEM_PROMPT = dedent(
    """
    당신은 노인 사용자의 자서전 인터뷰를 돕는 한국어 인터뷰 분석 도우미입니다.
    현재 질문과 사용자의 누적 답변을 보고, 어떤 정보가 이미 나왔는지 구조화해서 반환하세요.

    중요:
    - 최종 통과 여부(pass/follow_up/move_on/repeat)는 당신이 결정하지 않습니다.
    - 당신은 정보 추출과 점수화만 담당합니다.
    - filled_slots와 missing_slots에는 반드시 person, place, time, event, emotion, scene, value 중에서만 고르세요.
    - relevance_score는 0~2:
      0 = 질문과 거의 무관함
      1 = 부분적으로 관련 있음
      2 = 질문에 분명히 맞는 답변
    - detail_score는 0~2:
      0 = 정보가 거의 없음
      1 = 정보가 1개 정도 있음
      2 = 정보가 2개 이상 비교적 또렷함
    - reflection_score는 0~1:
      0 = 감정/의미/가치가 거의 없음
      1 = 감정이나 의미가 드러남
    - transcript_unclear는 소음이 많거나 뜻을 거의 파악하기 어려울 때만 true로 하세요.
    - off_topic은 말 자체는 들리지만 현재 질문과 방향이 꽤 어긋날 때만 true로 하세요.
    - answer_summary는 사용자의 답변을 1문장 이내로 짧게 요약합니다.
    """
).strip()

LOCAL_SLOT_KEYWORDS: dict[SlotName, tuple[str, ...]] = {
    "person": (
        "어머니",
        "아버지",
        "엄마",
        "아빠",
        "형",
        "누나",
        "언니",
        "오빠",
        "동생",
        "할머니",
        "할아버지",
        "친구",
        "선생님",
        "가족",
        "아내",
        "남편",
        "아이",
        "자식",
    ),
    "place": (
        "서울",
        "부산",
        "대구",
        "인천",
        "광주",
        "시골",
        "마을",
        "동네",
        "집",
        "학교",
        "공장",
        "시장",
        "회사",
        "교회",
        "역",
    ),
    "time": (
        "어릴 때",
        "젊었을 때",
        "그때",
        "옛날",
        "초등학교",
        "중학교",
        "고등학교",
        "스무 살",
        "스무살",
        "서른",
        "마흔",
        "명절",
        "겨울",
        "여름",
        "봄",
        "가을",
    ),
    "event": (
        "결혼",
        "졸업",
        "입학",
        "취직",
        "취업",
        "일했",
        "장사",
        "이사",
        "전쟁",
        "사고",
        "병원",
        "출근",
        "첫 직장",
        "군대",
    ),
    "emotion": (
        "외로",
        "기뻤",
        "슬펐",
        "무서",
        "걱정",
        "감사",
        "행복",
        "먹먹",
        "따뜻",
        "힘들",
        "좋았",
        "떨렸",
        "긴장",
        "뿌듯",
    ),
    "scene": (
        "장면",
        "모습",
        "기억",
        "눈앞",
        "마당",
        "냇가",
        "식탁",
        "밥상",
        "기차역",
        "시장",
        "길",
    ),
    "value": (
        "책임감",
        "소중",
        "감사",
        "버텨",
        "참아",
        "사람 마음",
        "가족이 먼저",
        "정직",
        "성실",
        "포기",
    ),
}


def _sanitize_slots(
    raw_slots: list[str],
    fallback_slots: list[SlotName] | None = None,
) -> list[SlotName]:
    allowed = {"person", "place", "time", "event", "emotion", "scene", "value"}
    cleaned: list[SlotName] = []

    for value in raw_slots:
        slot = str(value).strip().lower()
        if slot not in allowed:
            continue
        typed_slot = slot  # type: ignore[assignment]
        if typed_slot not in cleaned:
            cleaned.append(typed_slot)

    if cleaned:
        return cleaned

    return list(fallback_slots or [])


def _sanitize_score(raw_value: int, minimum: int, maximum: int) -> int:
    try:
        numeric = int(raw_value)
    except (TypeError, ValueError):
        return minimum
    return min(max(numeric, minimum), maximum)


def _build_assessment_input(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    user_text: str,
) -> str:
    current_answers = get_question_answers(state, state.current_question_no)
    previous_answers = "\n".join(f"- {text}" for text in current_answers if text.strip())
    previous_answers_text = previous_answers or "- 없음"
    last_follow_up = state.last_follow_up_question or "없음"

    return dedent(
        f"""
        현재 메인 질문:
        {question.main_question}

        질문 힌트:
        {question.hint}

        이 질문에서 보고 싶은 정보:
        {", ".join(question.target_slots)}

        이 질문에서 특히 중요하게 보고 싶은 정보:
        {", ".join(question.required_slots) if question.required_slots else "없음"}

        이전까지 모인 답변:
        {previous_answers_text}

        직전에 물었던 보조 질문:
        {last_follow_up}

        이번 사용자 답변:
        {user_text}

        지금까지 사용한 보조 질문 횟수:
        {state.follow_up_count}
        """
    ).strip()


def _extract_local_slots(text: str) -> list[SlotName]:
    lowered = text.lower()
    found: list[SlotName] = []
    for slot, keywords in LOCAL_SLOT_KEYWORDS.items():
        if any(keyword.lower() in lowered for keyword in keywords):
            found.append(slot)
    return found


def _normalize_assessment(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    user_text: str,
) -> VoiceInterviewAssessment:
    assessment.filled_slots = _sanitize_slots(assessment.filled_slots)
    assessment.missing_slots = _sanitize_slots(assessment.missing_slots, question.target_slots)
    assessment.relevance_score = _sanitize_score(assessment.relevance_score, 0, 2)
    assessment.detail_score = _sanitize_score(assessment.detail_score, 0, 2)
    assessment.reflection_score = _sanitize_score(assessment.reflection_score, 0, 1)

    combined_text = " ".join(
        [*get_question_answers(state, state.current_question_no), user_text]
    ).strip()
    for slot in _extract_local_slots(combined_text):
        if slot not in assessment.filled_slots:
            assessment.filled_slots.append(slot)

    if (
        "emotion" in assessment.filled_slots
        and assessment.reflection_score < 1
        and question.allow_emotion_exception
    ):
        assessment.reflection_score = 1

    if len(assessment.filled_slots) >= 2 and assessment.detail_score < 1:
        assessment.detail_score = 1

    if not assessment.missing_slots:
        assessment.missing_slots = [
            slot for slot in question.target_slots if slot not in assessment.filled_slots
        ]
    else:
        assessment.missing_slots = [
            slot for slot in assessment.missing_slots if slot not in assessment.filled_slots
        ]

    if not assessment.missing_slots:
        assessment.missing_slots = [
            slot for slot in question.target_slots if slot not in assessment.filled_slots
        ] or list(question.target_slots)

    return assessment


def request_voice_interview_assessment(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    user_text: str,
) -> VoiceInterviewAssessment:
    response = get_interview_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=ASSESSMENT_SYSTEM_PROMPT,
        input=_build_assessment_input(question, state, user_text),
        temperature=0.2,
        text_format=VoiceInterviewAssessment,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("인터뷰 답변 평가 응답을 해석하지 못했습니다.")

    return _normalize_assessment(question, state, parsed, user_text.strip())
