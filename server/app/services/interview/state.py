from typing import Literal

from app.services.interview.question_bank import (
    QUESTION_BANK_VERSION,
    get_interview_question,
    get_total_question_count,
)
from app.services.interview.types import (
    INTERVIEW_STATE_PREFIX,
    INTERVIEW_STATE_ROLE,
    QuestionStatus,
    VoiceInterviewPromptState,
    VoiceInterviewState,
)


def build_initial_voice_interview_state() -> VoiceInterviewState:
    return VoiceInterviewState()


def _question_key(question_no: int) -> str:
    return str(question_no)


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
    return state.model_copy(
        update={
            "question_answers": hydrated_answers,
            "collected_answers": _dedupe_answers(state.collected_answers),
        }
    )


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
    return VoiceInterviewState(
        current_question_no=target_question_no,
        follow_up_count=0,
        last_follow_up_question=None,
        collected_answers=target_answers,
        question_answers=preserved_question_answers,
        question_bank_version=state.question_bank_version or QUESTION_BANK_VERSION,
        is_interview_complete=is_complete,
        last_decision=None,
        last_reason_code=None,
        last_total_score=0,
        last_required_slot_hits=0,
        last_selected_missing_slot=None,
        last_pass_route=None,
    )


def build_voice_interview_prompt_state(
    state: VoiceInterviewState,
) -> VoiceInterviewPromptState:
    total_questions = get_total_question_count()
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
        )

    question = get_interview_question(state.current_question_no)
    progress_percent = int((question.question_no / total_questions) * 100)
    question_status: QuestionStatus = (
        "follow_up" if state.follow_up_count > 0 and state.last_follow_up_question else "main"
    )

    return VoiceInterviewPromptState(
        current_question_no=question.question_no,
        total_questions=total_questions,
        main_question=question.main_question,
        question_hint=question.hint,
        follow_up_count=state.follow_up_count,
        question_status=question_status,
        progress_percent=progress_percent,
        is_interview_complete=False,
    )
