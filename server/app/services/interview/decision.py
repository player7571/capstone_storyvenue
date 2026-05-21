from app.services.interview.types import (
    FollowUpGoal,
    InterviewQuestion,
    SlotName,
    TurnDecision,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
    VoiceInterviewState,
)
from app.services.interview.question_prompts import get_question_goal_priority

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


def matches_story_generatable_route(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> bool:
    return any(all(slot in assessment.filled_slots for slot in route) for route in question.story_generatable_routes)


def passes_story_ready_threshold(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    cleaned_text: str,
) -> bool:
    if assessment.transcript_unclear or assessment.question_echo or assessment.off_topic:
        return False
    if assessment.relevance_score < 1:
        return False
    if len(cleaned_text.strip()) < question.story_generatable_min_length:
        return False
    if assessment.detail_score < question.story_min_detail_score:
        return False
    if assessment.reflection_score < question.story_min_reflection_score:
        return False
    if len(set(assessment.filled_slots)) < question.story_min_distinct_slots:
        return False

    if question.follow_up_flow == "event_sequence":
        if not assessment.setup_present:
            return False
        if not assessment.development_present:
            return False
        if not (assessment.result_present or assessment.emotion_present):
            return False
        if question.story_min_reflection_score > 0 and not (
            assessment.emotion_present or assessment.meaning_present
        ):
            return False
        return True

    return matches_story_generatable_route(question, assessment)


def is_story_generatable_answer(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    cleaned_text: str,
) -> bool:
    return passes_story_ready_threshold(question, assessment, cleaned_text)


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


def _goal_for_slot(slot: SlotName | None) -> FollowUpGoal | None:
    if slot == "person":
        return "deepen_person"
    if slot in {"place", "time", "scene"}:
        return "deepen_scene"
    if slot == "value":
        return "deepen_reason"
    if slot == "event":
        return "deepen_event"
    if slot == "emotion":
        return "deepen_emotion"
    return None


def _goal_needs_follow_up(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    goal: FollowUpGoal,
) -> bool:
    missing_slots = set(assessment.missing_slots)
    target_slots = set(question.target_slots)

    if goal == "deepen_event":
        return not assessment.setup_present or "event" in missing_slots
    if goal == "deepen_scene":
        return not assessment.development_present or bool({"place", "time", "scene"} & missing_slots)
    if goal == "deepen_result":
        return not assessment.result_present
    if goal == "deepen_emotion":
        return not assessment.emotion_present or "emotion" in missing_slots
    if goal == "deepen_reason":
        return not assessment.meaning_present or bool({"value"} & missing_slots)
    if goal == "deepen_person":
        return not assessment.person_present or ("person" in target_slots and "person" in missing_slots)
    return False


def _derive_question_policy_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    selected_missing_slot: SlotName | None,
) -> FollowUpGoal | None:
    priority = get_question_goal_priority(question)
    if not priority:
        return None

    for goal in priority:
        if _goal_needs_follow_up(question, assessment, goal):
            return goal

    slot_goal = _goal_for_slot(selected_missing_slot)
    if slot_goal in priority:
        return slot_goal

    if "deepen_reason" in priority:
        return "deepen_reason"
    return priority[-1]


def _derive_event_sequence_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> FollowUpGoal:
    if not assessment.setup_present:
        return "deepen_event"
    if not assessment.development_present:
        return "deepen_scene"
    if not assessment.result_present:
        return "deepen_result"
    if not assessment.emotion_present:
        return "deepen_emotion"
    if not assessment.meaning_present and (
        "value" in question.target_slots or question.chapter_type == "reflection"
    ):
        return "deepen_reason"
    if not assessment.person_present and "person" in question.target_slots:
        return "deepen_person"
    return "deepen_event"


def _derive_person_focus_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> FollowUpGoal:
    if not assessment.person_present:
        return "deepen_person"
    if not assessment.setup_present:
        return "deepen_event"
    if not assessment.emotion_present:
        return "deepen_emotion"
    if not assessment.meaning_present and "value" in question.target_slots:
        return "deepen_reason"
    if not assessment.development_present and "scene" in question.target_slots:
        return "deepen_scene"
    return "deepen_event"


def _derive_background_memory_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> FollowUpGoal:
    if "place" not in assessment.filled_slots:
        return "deepen_scene"
    if not assessment.person_present:
        return "deepen_person"
    if not assessment.development_present:
        return "deepen_scene"
    if not assessment.emotion_present:
        return "deepen_emotion"
    return "deepen_scene"


def _derive_peer_life_memory_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> FollowUpGoal:
    if not assessment.setup_present or not assessment.development_present:
        return "deepen_event"
    if not assessment.person_present:
        return "deepen_person"
    if not assessment.emotion_present:
        return "deepen_emotion"
    if "value" in question.target_slots and not assessment.meaning_present:
        return "deepen_reason"
    return "deepen_event"


def _derive_person_memory_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> FollowUpGoal:
    if not assessment.person_present:
        return "deepen_person"
    if not assessment.setup_present:
        return "deepen_event"
    if not assessment.development_present:
        return "deepen_scene"
    if not assessment.emotion_present:
        return "deepen_emotion"
    if "value" in question.target_slots and not assessment.meaning_present:
        return "deepen_reason"
    return "deepen_person"


def _derive_value_focus_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> FollowUpGoal:
    if not assessment.meaning_present:
        return "deepen_reason"
    if not assessment.setup_present:
        return "deepen_event"
    if not assessment.emotion_present:
        return "deepen_emotion"
    if not assessment.person_present and "person" in question.target_slots:
        return "deepen_person"
    return "deepen_reason"


def _looks_like_generic_legacy_target(user_text: str) -> bool:
    lowered = user_text.strip().lower()
    if not lowered:
        return True
    generic_tokens = ("가족", "사람들", "모두", "우리", "서로", "누군가", "다들")
    specific_tokens = ("아이", "아들", "딸", "손자", "손녀", "남편", "아내", "친구", "동생", "형", "누나", "언니", "오빠", "어머니", "아버지")
    if any(token in lowered for token in specific_tokens):
        return False
    return any(token in lowered for token in generic_tokens)


def _derive_legacy_message_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    user_text: str,
) -> FollowUpGoal:
    if "value" not in assessment.filled_slots and not assessment.meaning_present:
        return "deepen_reason"
    if not assessment.person_present or _looks_like_generic_legacy_target(user_text):
        return "deepen_person"
    if not assessment.meaning_present:
        return "deepen_reason"
    if not assessment.setup_present:
        return "deepen_event"
    if not assessment.emotion_present:
        return "deepen_emotion"
    return "deepen_reason"


def derive_follow_up_goal(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: TurnDecision,
    selected_missing_slot: SlotName | None,
    user_text: str,
) -> FollowUpGoal:
    if decision == "pass":
        if (
            question.follow_up_flow == "event_sequence"
            and question.chapter_type == "reflection"
            and assessment.setup_present
            and assessment.development_present
            and assessment.result_present
            and assessment.emotion_present
            and not assessment.meaning_present
        ):
            return "deepen_reason"
        return "close"
    if decision == "repeat":
        return "retry"
    if decision == "move_on":
        return "close"
    if assessment.off_topic:
        return "refocus"

    policy_goal = _derive_question_policy_goal(question, assessment, selected_missing_slot)
    if policy_goal is not None:
        return policy_goal

    if question.follow_up_flow == "event_sequence":
        return _derive_event_sequence_goal(question, assessment)
    if question.follow_up_flow == "background_memory":
        return _derive_background_memory_goal(question, assessment)
    if question.follow_up_flow == "peer_life_memory":
        return _derive_peer_life_memory_goal(question, assessment)
    if question.follow_up_flow == "person_memory":
        return _derive_person_memory_goal(question, assessment)
    if question.follow_up_flow == "legacy_message":
        return _derive_legacy_message_goal(question, assessment, user_text)
    if question.follow_up_flow == "person_focus":
        return _derive_person_focus_goal(question, assessment)
    if question.follow_up_flow == "value_focus":
        return _derive_value_focus_goal(question, assessment)

    slot_goal = _goal_for_slot(selected_missing_slot)
    if slot_goal is not None:
        return slot_goal
    return "deepen_event"


def decide_interview_turn(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    cleaned_text: str,
) -> VoiceInterviewDecision:
    total_score = total_assessment_score(assessment)
    required_hits = count_required_slot_hits(question, assessment.filled_slots)
    meaningful_answer = looks_like_meaningful_answer(cleaned_text)
    story_ready_candidate = passes_story_ready_threshold(question, assessment, cleaned_text)

    if assessment.question_echo:
        return VoiceInterviewDecision(
            decision="repeat",
            reason_code="question_echo",
            follow_up_goal="retry",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    if assessment.transcript_unclear and not meaningful_answer:
        return VoiceInterviewDecision(
            decision="repeat",
            reason_code="transcript_unclear",
            follow_up_goal="retry",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    if passes_question_rules(question, assessment) and story_ready_candidate:
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="score_and_slots",
            follow_up_goal="close",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    pass_route = matched_alt_pass_route(question, assessment)
    if pass_route is not None and story_ready_candidate:
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="alt_pass_route",
            pass_route=pass_route,
            follow_up_goal="close",
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
        follow_up_goal=derive_follow_up_goal(
            question,
            assessment,
            "follow_up",
            selected_missing_slot,
            cleaned_text,
        ),
        total_score=total_score,
        required_slot_hits=required_hits,
    )
