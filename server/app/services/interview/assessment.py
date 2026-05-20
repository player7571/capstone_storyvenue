from difflib import SequenceMatcher
import re
from textwrap import dedent

from app.services.interview.llm import get_interview_openai_client
from app.services.interview.slot_keywords import extract_local_slots
from app.services.interview.state import get_question_answers
from app.services.interview.types import (
    EmotionalTone,
    InterviewQuestion,
    QuestionFlow,
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
    - flow_type은 반드시 default, event_sequence, person_focus, value_focus, background_memory, peer_life_memory, person_memory, legacy_message 중 하나만 고르세요.
    - event_sequence는 사건의 흐름으로 말하는 답변입니다.
    - person_focus는 한 사람을 중심으로 기억을 꺼내는 답변입니다.
    - value_focus는 남기고 싶은 말이나 삶의 의미를 중심으로 말하는 답변입니다.
    - background_memory는 어린 시절 살던 곳, 집안 분위기, 동네 모습처럼 삶의 배경을 회상하는 답변입니다.
    - peer_life_memory는 학교나 또래 시절의 하루 생활, 친구, 집안일처럼 생활감을 회상하는 답변입니다.
    - person_memory는 기억에 남는 사람의 성격, 함께한 장면, 그 사람의 의미를 중심으로 말하는 답변입니다.
    - legacy_message는 지금 남기고 싶은 말, 그 말을 전하고 싶은 대상, 그 이유를 중심으로 말하는 답변입니다.
    - default는 위 셋으로 명확히 보기 어려운 일반 회고형 답변입니다.
    - setup_present는 사건이 무엇이었는지, 어떤 상황이 시작되었는지가 나왔는지입니다.
    - development_present는 사건 속 장면, 행동, 전개가 나왔는지입니다.
    - result_present는 그 뒤 어떻게 되었는지 결과가 나왔는지입니다.
    - emotion_present는 그때의 감정이 나왔는지입니다.
    - meaning_present는 왜 기억에 남는지, 어떤 의미였는지, 어떤 생각이 남았는지가 나왔는지입니다.
    - person_present는 함께 있었던 사람이나 중심 인물이 분명히 언급되었는지입니다.
    - emotional_tone은 반드시 positive, negative, fearful, warm, neutral 중 하나만 고르세요.
    - positive는 기쁨, 뿌듯함, 반가움처럼 분명히 밝은 감정일 때만 고르세요.
    - warm은 가족애, 정겨움, 다정함, 그리움처럼 따뜻한 정서가 중심일 때만 고르세요.
    - negative는 힘듦, 상실감, 외로움, 속상함, 안타까움, 상처처럼 부정적인 감정일 때 고르세요.
    - fearful은 무서움, 놀람, 공포, 숨막힘처럼 두려움이 중심일 때 고르세요.
    - neutral은 감정이 거의 드러나지 않을 때만 고르세요.
    - 힘들었던 일, 따돌림, 아픈 기억, 상처, 막막함, 고생, 사고, 잃어버림 같은 답변은 positive나 warm으로 고르지 마세요.
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
    - question_echo는 사용자가 답하지 않고 현재 질문이나 질문에 매우 가까운 문장을 그대로 되묻는 경우만 true로 하세요.
    - answer_summary는 사용자의 답변을 1문장 이내로 짧게 요약합니다.
    """
).strip()

QUESTION_LIKE_ENDINGS = (
    "?",
    "나요",
    "인가요",
    "일까요",
    "까요",
    "어요?",
    "예요?",
    "있나요",
    "있어요?",
    "었나요",
)

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


def _normalize_compare_text(text: str) -> str:
    lowered = text.lower().strip()
    lowered = re.sub(r"[^\w\s가-힣]", " ", lowered)
    lowered = re.sub(r"\s+", " ", lowered).strip()
    return lowered


def _tokenize_compare_text(text: str) -> list[str]:
    return [token for token in _normalize_compare_text(text).split(" ") if len(token) >= 2]


def _looks_like_question_form(text: str) -> bool:
    stripped = text.strip().lower()
    if not stripped:
        return False
    return any(stripped.endswith(ending) for ending in QUESTION_LIKE_ENDINGS)


def _sequence_similarity(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    return SequenceMatcher(None, left, right).ratio()


def _token_overlap_ratio(left: str, right: str) -> float:
    left_tokens = set(_tokenize_compare_text(left))
    right_tokens = set(_tokenize_compare_text(right))
    if not left_tokens or not right_tokens:
        return 0.0
    intersection = len(left_tokens & right_tokens)
    union = len(left_tokens | right_tokens)
    if union == 0:
        return 0.0
    return intersection / union


def _looks_like_question_echo(question: InterviewQuestion, user_text: str) -> bool:
    normalized_user = _normalize_compare_text(user_text)
    if len(normalized_user) < 6:
        return False

    candidates = [question.main_question]
    if question.hint:
        candidates.append(question.hint)

    best_sequence_ratio = 0.0
    best_overlap_ratio = 0.0
    for candidate in candidates:
        normalized_candidate = _normalize_compare_text(candidate)
        best_sequence_ratio = max(
            best_sequence_ratio,
            _sequence_similarity(normalized_user, normalized_candidate),
        )
        best_overlap_ratio = max(
            best_overlap_ratio,
            _token_overlap_ratio(normalized_user, normalized_candidate),
        )

    if best_sequence_ratio >= 0.82:
        return True
    if best_sequence_ratio >= 0.68 and (
        _looks_like_question_form(user_text) or best_overlap_ratio >= 0.6
    ):
        return True
    if best_overlap_ratio >= 0.8 and _looks_like_question_form(user_text):
        return True
    return False

def _sanitize_flow_type(raw_value: str | None, fallback: QuestionFlow) -> QuestionFlow:
    value = str(raw_value or "").strip().lower()
    if value in {
        "default",
        "event_sequence",
        "person_focus",
        "value_focus",
        "background_memory",
        "peer_life_memory",
        "person_memory",
        "legacy_message",
    }:
        return value  # type: ignore[return-value]
    return fallback


def _sanitize_emotional_tone(raw_value: str | None) -> EmotionalTone:
    value = str(raw_value or "").strip().lower()
    if value in {"positive", "negative", "fearful", "warm", "neutral"}:
        return value  # type: ignore[return-value]
    return "neutral"


def _build_assessment_input(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    user_text: str,
) -> str:
    current_answers = get_question_answers(state, state.current_question_no)
    previous_answers_text = " | ".join(text for text in current_answers if text.strip()) or "없음"

    return dedent(
        f"""
        현재 질문:
        {question.main_question}

        질문 힌트:
        {question.hint}

        질문에서 중요하게 보고 싶은 정보:
        target_slots={", ".join(question.target_slots) if question.target_slots else "없음"}
        required_slots={", ".join(question.required_slots) if question.required_slots else "없음"}
        expected_flow={question.follow_up_flow}

        같은 질문에서 이전까지 나온 누적 답변:
        {previous_answers_text}

        마지막 follow-up 질문:
        {state.last_follow_up_question or "없음"}

        이번 사용자 답변:
        {user_text}

        현재 follow_up_count:
        {state.follow_up_count}
        """
    ).strip()


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
    assessment.question_echo = bool(assessment.question_echo)

    if _looks_like_question_echo(question, user_text):
        assessment.question_echo = True

    if assessment.question_echo:
        assessment.filled_slots = []
        assessment.missing_slots = list(question.target_slots)
        assessment.relevance_score = 0
        assessment.detail_score = 0
        assessment.reflection_score = 0
        assessment.transcript_unclear = False
        assessment.off_topic = False
        if not assessment.answer_summary.strip():
            assessment.answer_summary = "질문을 반복한 것으로 보이는 응답"
        return assessment

    combined_text = " ".join(
        [*get_question_answers(state, state.current_question_no), user_text]
    ).strip()
    for slot in extract_local_slots(combined_text):
        if slot not in assessment.filled_slots:
            assessment.filled_slots.append(slot)

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

    fallback_flow: QuestionFlow = question.follow_up_flow if question.follow_up_flow != "default" else "default"
    assessment.flow_type = _sanitize_flow_type(assessment.flow_type, fallback_flow)
    assessment.emotional_tone = _sanitize_emotional_tone(assessment.emotional_tone)
    assessment.setup_present = bool(assessment.setup_present)
    assessment.development_present = bool(assessment.development_present)
    assessment.result_present = bool(assessment.result_present)
    assessment.emotion_present = bool(assessment.emotion_present)
    assessment.meaning_present = bool(assessment.meaning_present)
    assessment.person_present = bool(assessment.person_present)

    if "person" in assessment.filled_slots:
        assessment.person_present = True
    if "emotion" in assessment.filled_slots:
        assessment.emotion_present = True
    if "value" in assessment.filled_slots:
        assessment.meaning_present = True
    if "event" in assessment.filled_slots:
        assessment.setup_present = assessment.setup_present or True

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
