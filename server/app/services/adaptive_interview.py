import logging
import time

from app.services.interview import (
    InterviewQuestion,
    InterviewerTurnResponse,
    SKIP_KEYWORDS,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
    VoiceInterviewPromptState,
    VoiceInterviewState,
    VoiceInterviewTurnOutcome,
    append_question_answer,
    build_interviewer_turn_fallback,
    build_voice_interview_prompt_state,
    build_follow_up_fallback,
    decide_interview_turn,
    get_interview_question,
    get_question_answers,
    get_total_question_count,
    is_story_generatable_answer,
    request_voice_interview_assessment,
)
from app.services.interview.follow_up import request_interviewer_turn

_STORY_QUALITY_RANK = {
    "none": 0,
    "basic": 1,
    "almost_ready": 2,
    "ready": 3,
}

_RELAXED_EVENT_SEQUENCE_QUESTIONS = {2, 4, 6, 8}
_STRICT_EVENT_SEQUENCE_QUESTIONS = {7, 9}

logger = logging.getLogger(__name__)


def _truncate_for_log(value: str | None, limit: int = 240) -> str:
    text = str(value or "").strip()
    if len(text) <= limit:
        return text
    return f"{text[:limit]}...(+{len(text) - limit} chars)"


def _elapsed_ms(started_at: float) -> int:
    return int((time.perf_counter() - started_at) * 1000)


def _build_cumulative_answer_text(
    state: VoiceInterviewState,
    question_no: int,
    user_text: str,
) -> str:
    answers = [answer.strip() for answer in get_question_answers(state, question_no) if answer.strip()]
    current = user_text.strip()
    if current and current not in answers:
        answers.append(current)
    return "\n".join(answers).strip()


def assess_voice_interview_answer(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    user_text: str,
) -> tuple[VoiceInterviewAssessment, VoiceInterviewDecision]:
    cleaned_text = user_text.strip()
    if not cleaned_text:
        assessment = VoiceInterviewAssessment(answer_summary="답변이 비어 있음")
        decision = VoiceInterviewDecision(
            decision="repeat",
            reason_code="empty_answer",
            total_score=0,
            required_slot_hits=0,
        )
        return assessment, decision

    lowered = cleaned_text.lower()
    if any(keyword in lowered for keyword in SKIP_KEYWORDS):
        assessment = VoiceInterviewAssessment(answer_summary=cleaned_text)
        decision = VoiceInterviewDecision(
            decision="move_on",
            reason_code="user_skip_requested",
            total_score=0,
            required_slot_hits=0,
        )
        return assessment, decision

    cumulative_text = _build_cumulative_answer_text(state, question.question_no, cleaned_text)
    assessment_started_at = time.perf_counter()
    assessment = request_voice_interview_assessment(question, state, cumulative_text)
    assessment_ms = _elapsed_ms(assessment_started_at)
    decision_started_at = time.perf_counter()
    decision = decide_interview_turn(question, state, assessment, cumulative_text)
    decision_ms = _elapsed_ms(decision_started_at)
    logger.info(
        "[interview_assessment] question_no=%s follow_up_count=%s assessment_ms=%s decision_ms=%s user_text=%s cumulative_chars=%s summary=%s filled_slots=%s missing_slots=%s relevance=%s detail=%s reflection=%s emotional_tone=%s emotional_blend=%s transcript_unclear=%s off_topic=%s question_echo=%s decision=%s reason_code=%s total_score=%s required_hits=%s selected_missing_slot=%s",
        question.question_no,
        state.follow_up_count,
        assessment_ms,
        decision_ms,
        _truncate_for_log(cleaned_text),
        len(cumulative_text),
        _truncate_for_log(assessment.answer_summary),
        ",".join(assessment.filled_slots) if assessment.filled_slots else "-",
        ",".join(assessment.missing_slots) if assessment.missing_slots else "-",
        assessment.relevance_score,
        assessment.detail_score,
        assessment.reflection_score,
        assessment.emotional_tone,
        assessment.emotional_blend,
        assessment.transcript_unclear,
        assessment.off_topic,
        assessment.question_echo,
        decision.decision,
        decision.reason_code,
        decision.total_score,
        decision.required_slot_hits,
        decision.selected_missing_slot,
    )

    return assessment, decision


def _build_state_with_metadata(
    base_state: VoiceInterviewState,
    decision: VoiceInterviewDecision,
) -> VoiceInterviewState:
    return base_state.model_copy(
        update={
            "last_decision": decision.decision,
            "last_reason_code": decision.reason_code,
            "last_total_score": decision.total_score,
            "last_required_slot_hits": decision.required_slot_hits,
            "last_selected_missing_slot": decision.selected_missing_slot,
            "last_pass_route": decision.pass_route,
        }
    )


def _with_story_ready_flag(
    state: VoiceInterviewState,
    question_no: int,
    is_ready: bool,
) -> dict[str, bool]:
    next_flags = dict(state.question_story_ready_flags)
    key = str(question_no)
    if is_ready:
        next_flags[key] = True
    return next_flags


def _merge_story_quality(current: str | None, candidate: str | None) -> str:
    current_value = str(current or "none").strip().lower() or "none"
    candidate_value = str(candidate or "none").strip().lower() or "none"
    current_rank = _STORY_QUALITY_RANK.get(current_value, 0)
    candidate_rank = _STORY_QUALITY_RANK.get(candidate_value, 0)
    return candidate_value if candidate_rank > current_rank else current_value


def _derive_event_sequence_story_quality(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    story_generatable: bool,
    answer_count: int,
    previous_quality: str | None,
) -> str:
    if story_generatable:
        return "ready"

    previous_value = str(previous_quality or "none").strip().lower() or "none"
    if previous_value == "ready":
        return "ready"
    if previous_value == "almost_ready":
        return "almost_ready"

    if question.question_no in _RELAXED_EVENT_SEQUENCE_QUESTIONS:
        if assessment.setup_present and assessment.development_present:
            return "almost_ready"
        if answer_count >= 2 and (
            (assessment.development_present and assessment.result_present)
            or (assessment.development_present and assessment.emotion_present)
            or (assessment.result_present and assessment.emotion_present)
        ):
            return "almost_ready"
        return "basic"

    if question.question_no in _STRICT_EVENT_SEQUENCE_QUESTIONS:
        if assessment.setup_present and assessment.development_present and (
            assessment.result_present or assessment.emotion_present or assessment.meaning_present
        ):
            return "almost_ready"
        return "basic"

    if assessment.setup_present and assessment.development_present:
        return "almost_ready"
    return "basic"


def _derive_updated_story_quality(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    story_generatable: bool,
    answer_count: int,
) -> str:
    key = str(state.current_question_no)
    previous_quality = state.question_story_qualities.get(key, "none")

    if question.follow_up_flow == "event_sequence":
        candidate = _derive_event_sequence_story_quality(
            question,
            assessment,
            story_generatable,
            answer_count,
            previous_quality,
        )
    else:
        if decision.decision == "pass":
            candidate = "ready"
        else:
            candidate = "basic" if answer_count > 0 else "none"

    return _merge_story_quality(previous_quality, candidate)


def _build_interviewer_result(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    prompt_state: VoiceInterviewPromptState,
    user_text: str,
) -> InterviewerTurnResponse:
    started_at = time.perf_counter()
    try:
        llm_started_at = time.perf_counter()
        result = request_interviewer_turn(
            question,
            state,
            assessment,
            decision,
            prompt_state,
            user_text,
        )
        llm_ms = _elapsed_ms(llm_started_at)
    except Exception:
        result = None
        llm_ms = _elapsed_ms(started_at)

    if result is None:
        # FALLBACK: LLM이 최종 인터뷰어 응답을 한 번에 생성하지 못했을 때만 사용합니다.
        fallback_started_at = time.perf_counter()
        result = build_interviewer_turn_fallback(
            question,
            assessment,
            decision,
            prompt_state,
            user_text,
        )
        fallback_ms = _elapsed_ms(fallback_started_at)
    else:
        fallback_ms = 0

    if decision.decision == "follow_up" and not result.next_question:
        fallback_question_started_at = time.perf_counter()
        fallback_question = build_follow_up_fallback(
            question,
            assessment,
            decision,
            user_text,
        ).strip()
        result = result.model_copy(
            update={
                "assistant_text": result.assistant_text.strip() or fallback_question,
                "next_question": fallback_question,
            }
        )
        fallback_ms += _elapsed_ms(fallback_question_started_at)

    if decision.decision != "follow_up":
        result = result.model_copy(update={"next_question": None})

    logger.info(
        "[interviewer_result] question_no=%s decision=%s reason_code=%s llm_ms=%s fallback_ms=%s total_ms=%s used_fallback=%s",
        question.question_no,
        decision.decision,
        decision.reason_code,
        llm_ms,
        fallback_ms,
        _elapsed_ms(started_at),
        str(fallback_ms > 0).lower(),
    )
    return result


def _build_follow_up_state(
    state: VoiceInterviewState,
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    user_text: str,
    decision: VoiceInterviewDecision,
    story_generatable: bool,
) -> VoiceInterviewState:
    answers = get_question_answers(state, state.current_question_no)
    next_answers = append_question_answer(state, state.current_question_no, user_text)
    updated_current_answers = next_answers.get(str(state.current_question_no), answers)
    next_story_qualities = dict(state.question_story_qualities)
    next_story_qualities[str(state.current_question_no)] = _derive_updated_story_quality(
        question,
        state,
        assessment,
        decision,
        story_generatable,
        len(updated_current_answers),
    )
    next_state = VoiceInterviewState(
        current_question_no=state.current_question_no,
        follow_up_count=state.follow_up_count + 1,
        last_follow_up_question=decision.follow_up_question,
        collected_answers=updated_current_answers,
        question_answers=next_answers,
        question_statuses=dict(state.question_statuses),
        question_story_qualities=next_story_qualities,
        question_story_ready_flags=_with_story_ready_flag(
            state,
            state.current_question_no,
            story_generatable,
        ),
        question_bank_version=state.question_bank_version,
        is_interview_complete=False,
    )
    return _build_state_with_metadata(next_state, decision)


def _build_passed_current_question_state(
    state: VoiceInterviewState,
    user_text: str,
    decision: VoiceInterviewDecision,
) -> VoiceInterviewState:
    next_answers = append_question_answer(state, state.current_question_no, user_text)
    updated_current_answers = next_answers.get(str(state.current_question_no), [])
    next_story_qualities = dict(state.question_story_qualities)
    next_story_qualities[str(state.current_question_no)] = "ready"
    next_state = VoiceInterviewState(
        current_question_no=state.current_question_no,
        follow_up_count=0,
        last_follow_up_question=None,
        collected_answers=updated_current_answers,
        question_answers=next_answers,
        question_statuses=dict(state.question_statuses),
        question_story_qualities=next_story_qualities,
        question_story_ready_flags=_with_story_ready_flag(
            state,
            state.current_question_no,
            True,
        ),
        question_bank_version=state.question_bank_version,
        is_interview_complete=False,
    )
    return _build_state_with_metadata(next_state, decision)


def _build_next_question_state(
    state: VoiceInterviewState,
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment | None,
    user_text: str | None,
    decision: VoiceInterviewDecision,
    story_generatable: bool,
) -> VoiceInterviewState:
    next_question_answers = append_question_answer(
        state,
        state.current_question_no,
        user_text,
    )
    next_question_no = state.current_question_no + 1
    total_questions = get_total_question_count()
    next_story_qualities = dict(state.question_story_qualities)
    if user_text is not None and assessment is not None:
        current_answers = next_question_answers.get(str(state.current_question_no), [])
        next_story_qualities[str(state.current_question_no)] = _derive_updated_story_quality(
            question,
            state,
            assessment,
            decision,
            story_generatable,
            len(current_answers),
        )
    if next_question_no > total_questions:
        next_state = VoiceInterviewState(
            current_question_no=total_questions,
            follow_up_count=0,
            last_follow_up_question=None,
            collected_answers=next_question_answers.get(str(total_questions), []),
            question_answers=next_question_answers,
            question_statuses=dict(state.question_statuses),
            question_story_qualities=next_story_qualities,
            question_story_ready_flags=_with_story_ready_flag(
                state,
                state.current_question_no,
                story_generatable,
            ),
            question_bank_version=state.question_bank_version,
            is_interview_complete=True,
        )
        return _build_state_with_metadata(next_state, decision)

    next_state = VoiceInterviewState(
        current_question_no=next_question_no,
        follow_up_count=0,
        last_follow_up_question=None,
        collected_answers=next_question_answers.get(str(next_question_no), []),
        question_answers=next_question_answers,
        question_statuses=dict(state.question_statuses),
        question_story_qualities=next_story_qualities,
        question_story_ready_flags=_with_story_ready_flag(
            state,
            state.current_question_no,
            story_generatable,
        ),
        question_bank_version=state.question_bank_version,
        is_interview_complete=False,
    )
    return _build_state_with_metadata(next_state, decision)


def process_voice_interview_answer(
    state: VoiceInterviewState,
    user_text: str,
) -> VoiceInterviewTurnOutcome:
    if state.is_interview_complete:
        prompt_state = build_voice_interview_prompt_state(state)
        return VoiceInterviewTurnOutcome(
            decision="move_on",
            assistant_text="질문이 모두 끝났어요. 이제 이야기를 생성해보세요.",
            next_state=state,
            prompt_state=prompt_state,
            reason_code="interview_complete",
            answer_summary="이미 인터뷰 완료 상태",
        )

    question = get_interview_question(state.current_question_no)
    assessment, decision = assess_voice_interview_answer(question, state, user_text)
    summary = assessment.answer_summary.strip() or user_text.strip()
    cumulative_text = _build_cumulative_answer_text(
        state,
        state.current_question_no,
        user_text,
    )
    story_generatable = is_story_generatable_answer(question, assessment, cumulative_text)

    if decision.decision == "repeat":
        prompt_state = build_voice_interview_prompt_state(state)
        interviewer_result = _build_interviewer_result(
            question,
            state,
            assessment,
            decision,
            prompt_state,
            cumulative_text,
        )
        return VoiceInterviewTurnOutcome(
            decision="repeat",
            assistant_text=interviewer_result.assistant_text,
            next_state=_build_state_with_metadata(state, decision),
            prompt_state=prompt_state,
            reason_code=decision.reason_code,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    if decision.decision == "follow_up":
        prompt_state = build_voice_interview_prompt_state(state)
        interviewer_result = _build_interviewer_result(
            question,
            state,
            assessment,
            decision,
            prompt_state,
            cumulative_text,
        )
        decision.follow_up_question = (
            interviewer_result.next_question
            or build_follow_up_fallback(question, assessment, decision, cumulative_text)
        ).strip()
        next_state = _build_follow_up_state(
            state,
            question,
            assessment,
            user_text,
            decision,
            story_generatable,
        )
        next_prompt_state = build_voice_interview_prompt_state(next_state)
        return VoiceInterviewTurnOutcome(
            decision="follow_up",
            assistant_text=interviewer_result.assistant_text,
            next_state=next_state,
            prompt_state=next_prompt_state,
            reason_code=decision.reason_code,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    if decision.decision == "pass":
        next_state = _build_passed_current_question_state(state, user_text, decision)
        prompt_state = build_voice_interview_prompt_state(next_state)
        interviewer_result = _build_interviewer_result(
            question,
            state,
            assessment,
            decision,
            prompt_state,
            cumulative_text,
        )
        return VoiceInterviewTurnOutcome(
            decision="pass",
            assistant_text=interviewer_result.assistant_text,
            next_state=next_state,
            prompt_state=prompt_state,
            reason_code=decision.reason_code,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    should_store_answer = decision.reason_code != "user_skip_requested"
    next_state = _build_next_question_state(
        state,
        question,
        assessment if should_store_answer else None,
        user_text if should_store_answer else None,
        decision,
        story_generatable if should_store_answer else False,
    )
    prompt_state = build_voice_interview_prompt_state(next_state)
    interviewer_result = _build_interviewer_result(
        question,
        state,
        assessment,
        decision,
        prompt_state,
        cumulative_text,
    )

    return VoiceInterviewTurnOutcome(
        decision=decision.decision,
        assistant_text=interviewer_result.assistant_text,
        next_state=next_state,
        prompt_state=prompt_state,
        reason_code=decision.reason_code,
        answer_summary=summary,
        filled_slots=assessment.filled_slots,
        missing_slots=assessment.missing_slots,
    )
