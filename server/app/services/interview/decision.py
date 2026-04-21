from app.services.interview.types import (
    InterviewQuestion,
    SlotName,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
    VoiceInterviewState,
)

SKIP_KEYWORDS = (
    "기억이 안",
    "기억나지",
    "기억이 잘 안",
    "잘 모르",
    "모르겠",
    "생각이 안",
    "다음으로",
    "넘어가",
)


def looks_like_meaningful_answer(text: str) -> bool:
    stripped = text.strip()
    if len(stripped) >= 6:
        return True
    if " " in stripped and len(stripped) >= 4:
        return True
    return False


def count_required_slot_hits(question: InterviewQuestion, filled_slots: list[SlotName]) -> int:
    if not question.required_slots:
        return 0
    return sum(1 for slot in question.required_slots if slot in filled_slots)


def total_assessment_score(assessment: VoiceInterviewAssessment) -> int:
    return assessment.relevance_score + assessment.detail_score + assessment.reflection_score


def passes_question_rules(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> bool:
    required_hits = count_required_slot_hits(question, assessment.filled_slots)
    has_required_slots = (
        not question.required_slots or required_hits >= question.required_slot_min_hits
    )
    has_enough_slots = len(assessment.filled_slots) >= question.min_filled_slots
    has_enough_relevance = assessment.relevance_score >= 1
    has_enough_score = total_assessment_score(assessment) >= question.pass_score
    return has_required_slots and has_enough_slots and has_enough_relevance and has_enough_score


def matched_alt_pass_route(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> str | None:
    if assessment.relevance_score < 1:
        return None

    total_score = total_assessment_score(assessment)
    for route in question.alt_pass_routes:
        if all(slot in assessment.filled_slots for slot in route) and total_score >= max(
            question.pass_score - 1,
            2,
        ):
            return "+".join(route)
    return None


def passes_emotion_exception(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> bool:
    return (
        question.allow_emotion_exception
        and assessment.relevance_score >= 1
        and "emotion" in assessment.filled_slots
        and total_assessment_score(assessment) >= max(question.pass_score - 1, 2)
    )


def is_near_pass(question: InterviewQuestion, assessment: VoiceInterviewAssessment) -> bool:
    required_hits = count_required_slot_hits(question, assessment.filled_slots)
    required_ok = not question.required_slots or required_hits >= question.required_slot_min_hits
    score_gap = question.pass_score - total_assessment_score(assessment)
    slot_gap = question.min_filled_slots - len(assessment.filled_slots)
    return (
        assessment.relevance_score >= 1
        and required_ok
        and score_gap <= 1
        and slot_gap <= 1
    )


def get_allowed_follow_ups(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> int:
    if is_near_pass(question, assessment):
        return question.base_follow_ups + question.near_pass_extra_follow_ups
    return question.base_follow_ups


def pick_missing_slot(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> SlotName:
    for slot in question.required_slots:
        if slot not in assessment.filled_slots:
            return slot
    for slot in assessment.missing_slots:
        if slot not in assessment.filled_slots:
            return slot
    for slot in question.target_slots:
        if slot not in assessment.filled_slots:
            return slot
    return question.target_slots[0]


def decide_interview_turn(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    cleaned_text: str,
) -> VoiceInterviewDecision:
    total_score = total_assessment_score(assessment)
    required_hits = count_required_slot_hits(question, assessment.filled_slots)
    meaningful_answer = looks_like_meaningful_answer(cleaned_text)

    if assessment.transcript_unclear and not meaningful_answer:
        return VoiceInterviewDecision(
            decision="repeat",
            reason_code="transcript_unclear",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    if passes_question_rules(question, assessment):
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="score_and_slots",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    pass_route = matched_alt_pass_route(question, assessment)
    if pass_route is not None:
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="alt_pass_route",
            pass_route=pass_route,
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    if passes_emotion_exception(question, assessment):
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="emotion_exception",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    allowed_follow_ups = get_allowed_follow_ups(question, assessment)
    if state.follow_up_count >= allowed_follow_ups:
        return VoiceInterviewDecision(
            decision="move_on",
            reason_code="follow_up_limit",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    selected_missing_slot = pick_missing_slot(question, assessment)
    if assessment.off_topic and meaningful_answer:
        reason_code = "off_topic_refocus"
    elif required_hits < question.required_slot_min_hits and question.required_slots:
        reason_code = "missing_required_slot"
    else:
        reason_code = "needs_more_detail"

    return VoiceInterviewDecision(
        decision="follow_up",
        reason_code=reason_code,
        selected_missing_slot=selected_missing_slot,
        total_score=total_score,
        required_slot_hits=required_hits,
    )
