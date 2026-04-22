from typing import Literal

from app.services.interview.question_bank import (
    QUESTION_BANK_VERSION,
    get_interview_question,
    get_total_question_count,
)
from app.services.interview.slot_keywords import extract_local_slots
from app.services.interview.types import (
    INTERVIEW_STATE_PREFIX,
    INTERVIEW_STATE_ROLE,
    QuestionStatus,
    SlotName,
    StoryQuality,
    VoiceInterviewPromptState,
    VoiceInterviewState,
)


def build_initial_voice_interview_state() -> VoiceInterviewState:
    return VoiceInterviewState()


def _question_key(question_no: int) -> str:
    return str(question_no)


_STORY_QUALITY_RANK: dict[StoryQuality, int] = {
    "none": 0,
    "basic": 1,
    "ready": 2,
}


def _normalize_story_quality(value: str | None) -> StoryQuality:
    normalized = str(value or "").strip().lower()
    if normalized in _STORY_QUALITY_RANK:
        return normalized  # type: ignore[return-value]
    return "none"


def merge_story_qualities(*values: str | None) -> StoryQuality:
    best: StoryQuality = "none"
    best_rank = _STORY_QUALITY_RANK[best]

    for value in values:
        normalized = _normalize_story_quality(value)
        rank = _STORY_QUALITY_RANK[normalized]
        if rank > best_rank:
            best = normalized
            best_rank = rank

    return best


def _dedupe_answers(answers: list[str]) -> list[str]:
    cleaned: list[str] = []
    seen: set[str] = set()

    for raw in answers:
        value = str(raw or "").strip()
        if not value or value in seen:
            continue
        cleaned.append(value)
        seen.add(value)

    return cleaned


def _normalize_question_story_qualities(state: VoiceInterviewState) -> dict[str, StoryQuality]:
    normalized: dict[str, StoryQuality] = {}

    for key, value in state.question_story_qualities.items():
        normalized_key = str(key).strip()
        if not normalized_key:
            continue
        quality = _normalize_story_quality(value)
        if quality == "none":
            continue
        normalized[normalized_key] = quality

    return normalized


def _normalize_question_story_ready_flags(state: VoiceInterviewState) -> dict[str, bool]:
    normalized: dict[str, bool] = {}

    for key, value in state.question_story_ready_flags.items():
        normalized_key = str(key).strip()
        if not normalized_key:
            continue
        normalized[normalized_key] = bool(value)

    return normalized


def _normalize_question_statuses(state: VoiceInterviewState) -> dict[str, str]:
    normalized = {
        str(key): str(value).strip().lower()
        for key, value in state.question_statuses.items()
        if str(key).strip() and str(value).strip()
    }

    for question_no in range(1, get_total_question_count() + 1):
        key = _question_key(question_no)
        if key in normalized:
            continue
        has_answer = bool(get_question_answers(state, question_no))
        story_quality = get_question_story_quality(state, question_no)
        if question_no == state.current_question_no:
            if has_answer and story_quality == "ready":
                normalized[key] = "completed"
            else:
                normalized[key] = "in_progress"
        elif has_answer:
            normalized[key] = "completed" if story_quality == "ready" else "answered"
        elif question_no < state.current_question_no:
            normalized[key] = "skipped"
        else:
            normalized[key] = "pending"

    return normalized


def get_question_answers(
    state: VoiceInterviewState,
    question_no: int,
) -> list[str]:
    answers = _dedupe_answers(state.question_answers.get(_question_key(question_no), []))
    if answers:
        return answers

    if question_no == state.current_question_no and state.collected_answers:
        return _dedupe_answers(state.collected_answers)

    return []


def get_question_answer_count(
    state: VoiceInterviewState,
    question_no: int,
) -> int:
    return len(get_question_answers(state, question_no))


def get_question_story_quality(
    state: VoiceInterviewState,
    question_no: int,
) -> StoryQuality:
    stored_quality = _normalize_question_story_qualities(state).get(_question_key(question_no), "none")
    answers = get_question_answers(state, question_no)
    if not answers:
        return merge_story_qualities(stored_quality, "none")

    computed_quality: StoryQuality = "basic"

    if question_no == state.current_question_no and state.last_decision == "pass":
        computed_quality = "ready"

    return merge_story_qualities(stored_quality, computed_quality)


def _extract_story_slots_from_answers(answers: list[str]) -> list[SlotName]:
    combined = "\n".join(answer.strip() for answer in answers if answer.strip())
    if not combined:
        return []
    return extract_local_slots(combined)


def _matches_story_generatable_route(question_no: int, answers: list[str]) -> bool:
    question = get_interview_question(question_no)
    extracted_slots = set(_extract_story_slots_from_answers(answers))
    if not extracted_slots:
        return False
    return any(all(slot in extracted_slots for slot in route) for route in question.story_generatable_routes)


def is_question_story_generatable(
    state: VoiceInterviewState,
    question_no: int,
) -> bool:
    answers = get_question_answers(state, question_no)
    if not answers:
        return False

    story_quality = get_question_story_quality(state, question_no)
    if story_quality == "ready":
        return True

    stored_ready_flags = _normalize_question_story_ready_flags(state)
    if stored_ready_flags.get(_question_key(question_no)):
        return True

    question = get_interview_question(question_no)
    aggregated_answer = "\n".join(answer.strip() for answer in answers if answer.strip())
    if len(aggregated_answer) < question.story_generatable_min_length:
        return False

    return _matches_story_generatable_route(question_no, answers)


def is_question_story_ready(
    state: VoiceInterviewState,
    question_no: int,
) -> bool:
    return is_question_story_generatable(state, question_no)


def get_story_generation_target_question_no(
    state: VoiceInterviewState,
) -> int | None:
    total_questions = get_total_question_count()
    current_question_no = min(max(state.current_question_no, 1), total_questions)

    if (
        get_question_answer_count(state, current_question_no) > 0
        and is_question_story_generatable(state, current_question_no)
    ):
        return current_question_no

    search_start = total_questions if state.is_interview_complete else current_question_no - 1
    for question_no in range(search_start, 0, -1):
        if (
            get_question_answer_count(state, question_no) > 0
            and is_question_story_generatable(state, question_no)
        ):
            return question_no

    return None


def _with_question_answers(
    state: VoiceInterviewState,
    question_no: int,
    answers: list[str],
) -> dict[str, list[str]]:
    next_question_answers = {
        key: _dedupe_answers(value)
        for key, value in state.question_answers.items()
        if _dedupe_answers(value)
    }
    normalized_answers = _dedupe_answers(answers)
    if normalized_answers:
        next_question_answers[_question_key(question_no)] = normalized_answers
    else:
        next_question_answers.pop(_question_key(question_no), None)
    return next_question_answers


def append_question_answer(
    state: VoiceInterviewState,
    question_no: int,
    answer: str | None,
) -> dict[str, list[str]]:
    normalized = str(answer or "").strip()
    current_answers = get_question_answers(state, question_no)
    if not normalized:
        return _with_question_answers(state, question_no, current_answers)
    return _with_question_answers(state, question_no, [*current_answers, normalized])


def _hydrate_legacy_state(state: VoiceInterviewState) -> VoiceInterviewState:
    hydrated_answers = {
        key: _dedupe_answers(value)
        for key, value in state.question_answers.items()
        if _dedupe_answers(value)
    }
    hydrated_story_qualities = _normalize_question_story_qualities(state)
    hydrated_story_ready_flags = _normalize_question_story_ready_flags(state)
    return state.model_copy(
        update={
            "question_answers": hydrated_answers,
            "collected_answers": _dedupe_answers(state.collected_answers),
            "question_story_qualities": hydrated_story_qualities,
            "question_story_ready_flags": hydrated_story_ready_flags,
            "question_statuses": _normalize_question_statuses(
                state.model_copy(
                    update={
                        "question_answers": hydrated_answers,
                        "collected_answers": _dedupe_answers(state.collected_answers),
                        "question_story_qualities": hydrated_story_qualities,
                        "question_story_ready_flags": hydrated_story_ready_flags,
                    }
                )
            ),
        }
    )


def is_question_completed(
    state: VoiceInterviewState,
    question_no: int,
) -> bool:
    status = _normalize_question_statuses(state).get(_question_key(question_no), "pending")
    return status in {"completed", "skipped"}


def can_move_to_next_question(state: VoiceInterviewState) -> bool:
    return is_question_completed(state, state.current_question_no)


def serialize_voice_interview_state(state: VoiceInterviewState) -> str:
    return f"{INTERVIEW_STATE_PREFIX}{state.model_dump_json(exclude_none=True)}"


def parse_voice_interview_state(content: str) -> VoiceInterviewState | None:
    if not content.startswith(INTERVIEW_STATE_PREFIX):
        return None
    payload = content[len(INTERVIEW_STATE_PREFIX) :]
    try:
        return _hydrate_legacy_state(VoiceInterviewState.model_validate_json(payload))
    except Exception:
        return None


def is_voice_interview_state_message(content: str) -> bool:
    return content.startswith(INTERVIEW_STATE_PREFIX)


def derive_voice_interview_state_from_session_messages(
    messages: list[dict],
) -> VoiceInterviewState:
    latest_state: VoiceInterviewState | None = None
    user_turn_count = 0

    for row in messages:
        role = str(row.get("role") or "").strip().lower()
        content = str(row.get("content") or "").strip()
        if role == "user" and content:
            user_turn_count += 1
        if role == INTERVIEW_STATE_ROLE and content:
            parsed = parse_voice_interview_state(content)
            if parsed is not None:
                latest_state = parsed

    if latest_state is not None:
        return _hydrate_legacy_state(latest_state)

    total_questions = get_total_question_count()
    if user_turn_count >= total_questions:
        return _hydrate_legacy_state(
            VoiceInterviewState(
                current_question_no=total_questions,
                is_interview_complete=True,
            )
        )
    if user_turn_count <= 0:
        return build_initial_voice_interview_state()

    return _hydrate_legacy_state(VoiceInterviewState(current_question_no=user_turn_count + 1))


def build_question_answer_conversation_history(
    state: VoiceInterviewState,
) -> list[dict[str, str]]:
    history: list[dict[str, str]] = []

    for question_no in range(1, get_total_question_count() + 1):
        answers = _dedupe_answers(state.question_answers.get(_question_key(question_no), []))
        if not answers:
            continue

        question = get_interview_question(question_no)
        assistant_text = f"Q{question_no}. {question.main_question}"
        if question.hint:
            assistant_text = f"{assistant_text} (힌트: {question.hint})"

        history.append({"role": "assistant", "content": assistant_text})
        history.append({"role": "user", "content": "\n".join(answers)})

    return history


def move_voice_interview_question(
    state: VoiceInterviewState,
    direction: Literal["previous", "next"],
) -> VoiceInterviewState | None:
    total_questions = get_total_question_count()
    preserved_question_answers = _with_question_answers(
        state,
        state.current_question_no,
        get_question_answers(state, state.current_question_no),
    )
    preserved_story_qualities = _normalize_question_story_qualities(state)
    preserved_story_ready_flags = _normalize_question_story_ready_flags(state)

    if direction == "previous":
        if state.is_interview_complete:
            target_question_no = total_questions
        elif state.current_question_no <= 1:
            return None
        else:
            target_question_no = state.current_question_no - 1
        is_complete = False
    else:
        if state.is_interview_complete:
            return None
        if state.current_question_no >= total_questions:
            target_question_no = total_questions
            is_complete = True
        else:
            target_question_no = state.current_question_no + 1
            is_complete = False

    target_answers = _dedupe_answers(
        preserved_question_answers.get(_question_key(target_question_no), [])
    )
    return _hydrate_legacy_state(
        VoiceInterviewState(
            current_question_no=target_question_no,
            follow_up_count=0,
            last_follow_up_question=None,
            collected_answers=target_answers,
            question_answers=preserved_question_answers,
            question_story_qualities=preserved_story_qualities,
            question_story_ready_flags=preserved_story_ready_flags,
            question_bank_version=state.question_bank_version or QUESTION_BANK_VERSION,
            is_interview_complete=is_complete,
            last_decision=None,
            last_reason_code=None,
            last_total_score=0,
            last_required_slot_hits=0,
            last_selected_missing_slot=None,
            last_pass_route=None,
        )
    )


def build_voice_interview_prompt_state(
    state: VoiceInterviewState,
) -> VoiceInterviewPromptState:
    total_questions = get_total_question_count()
    answer_count = get_question_answer_count(state, state.current_question_no)
    story_quality = get_question_story_quality(state, state.current_question_no)
    story_ready = is_question_story_generatable(state, state.current_question_no)
    story_target_question_no = get_story_generation_target_question_no(state)
    story_target_answer_count = (
        get_question_answer_count(state, story_target_question_no)
        if story_target_question_no is not None
        else 0
    )
    story_target_story_quality = (
        get_question_story_quality(state, story_target_question_no)
        if story_target_question_no is not None
        else "none"
    )
    if story_target_question_no is not None:
        story_target_story_ready = (
            is_question_story_generatable(state, story_target_question_no)
            and story_target_answer_count > 0
        )
    else:
        story_target_story_ready = False
    story_target_is_current_question = story_target_question_no == state.current_question_no
    if state.is_interview_complete:
        return VoiceInterviewPromptState(
            current_question_no=total_questions,
            total_questions=total_questions,
            main_question="질문이 모두 끝났어요.",
            question_hint="이제 이야기 생성하기를 눌러 자서전 초안을 만들어보세요.",
            follow_up_count=state.follow_up_count,
            question_status="completed",
            progress_percent=100,
            is_interview_complete=True,
            current_question_has_answer=answer_count > 0,
            current_question_answer_count=answer_count,
            current_question_story_ready=story_ready,
            current_question_story_quality=story_quality,
            story_target_question_no=story_target_question_no,
            story_target_has_answer=story_target_answer_count > 0,
            story_target_answer_count=story_target_answer_count,
            story_target_story_ready=story_target_story_ready,
            story_target_story_quality=story_target_story_quality,
            story_target_is_current_question=story_target_is_current_question,
        )

    question = get_interview_question(state.current_question_no)
    progress_percent = int((question.question_no / total_questions) * 100)
    current_question_completed = story_quality == "ready" and answer_count > 0
    question_status: QuestionStatus
    if current_question_completed:
        question_status = "completed"
    elif state.follow_up_count > 0 and state.last_follow_up_question:
        question_status = "follow_up"
    else:
        question_status = "main"

    return VoiceInterviewPromptState(
        current_question_no=question.question_no,
        total_questions=total_questions,
        main_question=question.main_question,
        question_hint=question.hint,
        follow_up_count=state.follow_up_count,
        question_status=question_status,
        progress_percent=progress_percent,
        is_interview_complete=False,
        current_question_has_answer=answer_count > 0,
        current_question_answer_count=answer_count,
        current_question_story_ready=story_ready,
        current_question_story_quality=story_quality,
        current_question_completed=current_question_completed,
        current_question_can_move_next=current_question_completed,
        story_target_question_no=story_target_question_no,
        story_target_has_answer=story_target_answer_count > 0,
        story_target_answer_count=story_target_answer_count,
        story_target_story_ready=story_target_story_ready,
        story_target_story_quality=story_target_story_quality,
        story_target_is_current_question=story_target_is_current_question,
    )
