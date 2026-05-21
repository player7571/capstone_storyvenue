from difflib import SequenceMatcher
import logging
import re
from textwrap import dedent

from app.services.interview.llm import (
    build_interview_prompt_cache_body,
    get_interview_openai_client,
    log_interview_prompt_cache_usage,
)
from app.services.interview.question_prompts import build_assessment_system_prompt
from app.services.interview.slot_keywords import extract_local_slots
from app.services.interview.state import get_question_answers
from app.services.interview.types import (
    EmotionalBlend,
    EmotionalTone,
    InterviewQuestion,
    QuestionFlow,
    SlotName,
    VoiceInterviewAssessment,
    VoiceInterviewState,
)

logger = logging.getLogger(__name__)

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


def _sanitize_emotional_blend(
    raw_value: str | None,
    emotional_tone: EmotionalTone,
) -> EmotionalBlend:
    value = str(raw_value or "").strip().lower()
    allowed = {
        "none",
        "warm_relief",
        "support",
        "gratitude",
        "pride_after_hardship",
        "sad_warmth",
        "regret",
    }
    if value not in allowed:
        return "none"

    # The blend is only a secondary signal. Keep it conservative so it cannot
    # flip a painful/fearful answer into an overly positive acknowledgement.
    if emotional_tone == "fearful" and value in {"warm_relief", "support", "sad_warmth"}:
        return value  # type: ignore[return-value]
    if emotional_tone == "negative" and value in {
        "warm_relief",
        "support",
        "gratitude",
        "sad_warmth",
        "regret",
    }:
        return value  # type: ignore[return-value]
    if emotional_tone == "positive" and value in {"gratitude", "pride_after_hardship"}:
        return value  # type: ignore[return-value]
    if emotional_tone == "warm" and value in {"gratitude", "support", "sad_warmth", "regret"}:
        return value  # type: ignore[return-value]
    return "none"


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
        assessment.emotional_blend = "none"
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
        ]

    fallback_flow: QuestionFlow = question.follow_up_flow if question.follow_up_flow != "default" else "default"
    assessment.flow_type = _sanitize_flow_type(assessment.flow_type, fallback_flow)
    assessment.emotional_tone = _sanitize_emotional_tone(assessment.emotional_tone)
    assessment.emotional_blend = _sanitize_emotional_blend(
        assessment.emotional_blend,
        assessment.emotional_tone,
    )
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
        instructions=build_assessment_system_prompt(question),
        input=_build_assessment_input(question, state, user_text),
        temperature=0.2,
        max_output_tokens=360,
        text_format=VoiceInterviewAssessment,
        extra_body=build_interview_prompt_cache_body("assessment", question),
    )
    log_interview_prompt_cache_usage(logger, "assessment", question, response)

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("인터뷰 답변 평가 응답을 해석하지 못했습니다.")

    return _normalize_assessment(question, state, parsed, user_text.strip())
