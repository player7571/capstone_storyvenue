import logging

from app.services.interview import (
    InterviewQuestion,
    SKIP_KEYWORDS,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
    VoiceInterviewPromptState,
    VoiceInterviewState,
    VoiceInterviewTurnOutcome,
    append_question_answer,
    build_voice_interview_prompt_state,
    build_follow_up_fallback,
    build_interviewer_acknowledgement_fallback,
    build_interviewer_guidance,
    decide_interview_turn,
    get_interview_question,
    get_question_answers,
    get_total_question_count,
    is_story_generatable_answer,
    request_follow_up_question,
    request_interviewer_acknowledgement,
    request_voice_interview_assessment,
)

logger = logging.getLogger(__name__)


def _truncate_for_log(value: str | None, limit: int = 240) -> str:
    text = str(value or "").strip()
    if len(text) <= limit:
        return text
    return f"{text[:limit]}...(+{len(text) - limit} chars)"


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

    assessment = request_voice_interview_assessment(question, state, cleaned_text)
    decision = decide_interview_turn(question, state, assessment, cleaned_text)
    logger.info(
        "[interview_assessment] question_no=%s follow_up_count=%s user_text=%s summary=%s filled_slots=%s missing_slots=%s relevance=%s detail=%s reflection=%s transcript_unclear=%s off_topic=%s question_echo=%s decision=%s reason_code=%s total_score=%s required_hits=%s selected_missing_slot=%s",
        question.question_no,
        state.follow_up_count,
        _truncate_for_log(cleaned_text),
        _truncate_for_log(assessment.answer_summary),
        ",".join(assessment.filled_slots) if assessment.filled_slots else "-",
        ",".join(assessment.missing_slots) if assessment.missing_slots else "-",
        assessment.relevance_score,
        assessment.detail_score,
        assessment.reflection_score,
        assessment.transcript_unclear,
        assessment.off_topic,
        assessment.question_echo,
        decision.decision,
        decision.reason_code,
        decision.total_score,
        decision.required_slot_hits,
        decision.selected_missing_slot,
    )

    if decision.decision == "follow_up":
        decision.follow_up_question = request_follow_up_question(
            question,
            assessment,
            decision,
            cleaned_text,
        )
        if not decision.follow_up_question:
            decision.follow_up_question = build_follow_up_fallback(
                question,
                assessment,
                decision,
                cleaned_text,
            )
        logger.info(
            "[interview_follow_up] question_no=%s reason_code=%s selected_missing_slot=%s follow_up_question=%s",
            question.question_no,
            decision.reason_code,
            decision.selected_missing_slot,
            _truncate_for_log(decision.follow_up_question),
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


def _join_interviewer_sentences(
    acknowledgement: str,
    guidance: str,
) -> str:
    ack = acknowledgement.strip()
    guide = guidance.strip()
    if ack and guide:
        return f"{ack} {guide}"
    return ack or guide


def _build_interviewer_message(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    prompt_state: VoiceInterviewPromptState,
    core_message: str,
    *,
    use_acknowledgement: bool = True,
) -> str:
    guidance = build_interviewer_guidance(decision, prompt_state, core_message)
    if not use_acknowledgement:
        return guidance

    acknowledgement = ""
    try:
        acknowledgement = (
            request_interviewer_acknowledgement(
                question,
                assessment,
                decision,
            )
            or ""
        )
    except Exception:
        acknowledgement = ""

    if not acknowledgement:
        acknowledgement = build_interviewer_acknowledgement_fallback(decision, assessment)

    return _join_interviewer_sentences(acknowledgement, guidance)


def _build_follow_up_state(
    state: VoiceInterviewState,
    user_text: str,
    decision: VoiceInterviewDecision,
    story_generatable: bool,
) -> VoiceInterviewState:
    answers = get_question_answers(state, state.current_question_no)
    next_answers = append_question_answer(state, state.current_question_no, user_text)
    updated_current_answers = next_answers.get(str(state.current_question_no), answers)
    next_state = VoiceInterviewState(
        current_question_no=state.current_question_no,
        follow_up_count=state.follow_up_count + 1,
        last_follow_up_question=decision.follow_up_question,
        collected_answers=updated_current_answers,
        question_answers=next_answers,
        question_statuses=dict(state.question_statuses),
        question_story_qualities=dict(state.question_story_qualities),
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
    if next_question_no > total_questions:
        next_state = VoiceInterviewState(
            current_question_no=total_questions,
            follow_up_count=0,
            last_follow_up_question=None,
            collected_answers=next_question_answers.get(str(total_questions), []),
            question_answers=next_question_answers,
            question_statuses=dict(state.question_statuses),
            question_story_qualities=dict(state.question_story_qualities),
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
        question_story_qualities=dict(state.question_story_qualities),
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
    story_generatable = is_story_generatable_answer(question, assessment, user_text.strip())

    if decision.decision == "repeat":
        if decision.reason_code == "question_echo":
            core_message = (
                f"질문이 다시 들린 것 같아요. 답변만 천천히 말씀해주세요. "
                f"{question.hint}"
            ).strip()
        elif decision.reason_code == "empty_answer":
            core_message = "답변이 들리지 않았어요. 기억나는 내용부터 천천히 말씀해주세요."
        else:
            core_message = "말씀을 정확히 알아듣지 못했어요. 같은 내용을 조금만 천천히 다시 말씀해주세요."
        prompt_state = build_voice_interview_prompt_state(state)
        assistant_text = _build_interviewer_message(
            question,
            assessment,
            decision,
            prompt_state,
            core_message,
            use_acknowledgement=False,
        )
        return VoiceInterviewTurnOutcome(
            decision="repeat",
            assistant_text=assistant_text,
            next_state=_build_state_with_metadata(state, decision),
            prompt_state=prompt_state,
            reason_code=decision.reason_code,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    if decision.decision == "follow_up":
        safe_follow_up_question = (
            decision.follow_up_question
            or build_follow_up_fallback(question, assessment, decision, user_text.strip())
        ).strip()
        decision.follow_up_question = safe_follow_up_question
        next_state = _build_follow_up_state(state, user_text, decision, story_generatable)
        prompt_state = build_voice_interview_prompt_state(next_state)
        assistant_text = _build_interviewer_message(
            question,
            assessment,
            decision,
            prompt_state,
            safe_follow_up_question,
        )
        return VoiceInterviewTurnOutcome(
            decision="follow_up",
            assistant_text=assistant_text,
            next_state=next_state,
            prompt_state=prompt_state,
            reason_code=decision.reason_code,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    if decision.decision == "pass":
        next_state = _build_passed_current_question_state(state, user_text, decision)
        prompt_state = build_voice_interview_prompt_state(next_state)
        assistant_text = _build_interviewer_message(
            question,
            assessment,
            decision,
            prompt_state,
            "더 떠오르는 게 없으면 다음 질문으로 넘어가거나 지금 이야기를 만들어도 괜찮아요.",
        )
        return VoiceInterviewTurnOutcome(
            decision="pass",
            assistant_text=assistant_text,
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
        user_text if should_store_answer else None,
        decision,
        story_generatable if should_store_answer else False,
    )
    prompt_state = build_voice_interview_prompt_state(next_state)

    if next_state.is_interview_complete:
        core_message = "잘 들었습니다. 질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
    elif decision.decision == "move_on":
        core_message = "괜찮아요. 기억나는 만큼으로도 충분해요. 다음 이야기로 넘어가볼게요."
    else:
        core_message = "잘 들었습니다. 다음 이야기로 넘어가볼게요."

    assistant_text = _build_interviewer_message(
        question,
        assessment,
        decision,
        prompt_state,
        core_message,
        use_acknowledgement=False,
    )

    return VoiceInterviewTurnOutcome(
        decision=decision.decision,
        assistant_text=assistant_text,
        next_state=next_state,
        prompt_state=prompt_state,
        reason_code=decision.reason_code,
        answer_summary=summary,
        filled_slots=assessment.filled_slots,
        missing_slots=assessment.missing_slots,
    )
