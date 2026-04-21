from collections import defaultdict
from uuid import UUID

from app.db.supabase import get_supabase
from app.services.interview.question_bank import QUESTION_BANK_VERSION, get_interview_question, get_total_question_count
from app.services.interview.state import build_initial_voice_interview_state, get_question_answers
from app.services.interview.types import VoiceInterviewState

QUESTION_STATE_TABLE = "session_question_states"
QUESTION_ANSWER_TABLE = "session_question_answers"


def _question_state_status(question_no: int, current_question_no: int, has_answer: bool) -> str:
    if question_no == current_question_no:
        return "in_progress"
    if has_answer:
        return "answered"
    if question_no < current_question_no:
        return "skipped"
    return "pending"


def initialize_question_state_rows(session_id: UUID) -> None:
    rows = [
        {
            "session_id": str(session_id),
            "question_no": question_no,
            "main_question": get_interview_question(question_no).main_question,
            "question_hint": get_interview_question(question_no).hint,
            "status": "in_progress" if question_no == 1 else "pending",
            "follow_up_count": 0,
            "aggregated_answer_text": "",
            "has_answer": False,
            "story_ready": False,
            "last_decision": None,
            "last_reason_code": None,
            "total_score": 0,
            "required_slot_hits": 0,
            "last_selected_missing_slot": None,
            "last_pass_route": None,
            "question_bank_version": QUESTION_BANK_VERSION,
        }
        for question_no in range(1, get_total_question_count() + 1)
    ]
    get_supabase().table(QUESTION_STATE_TABLE).upsert(
        rows,
        on_conflict="session_id,question_no",
    ).execute()


def append_question_answer_record(
    session_id: UUID,
    question_no: int,
    user_text: str,
    source_type: str,
    stt_raw_text: str | None = None,
) -> None:
    cleaned_text = str(user_text or "").strip()
    if not cleaned_text:
        return
    row = {
        "session_id": str(session_id),
        "question_no": question_no,
        "source_type": source_type,
        "user_text": cleaned_text,
    }
    if stt_raw_text:
        row["stt_raw_text"] = str(stt_raw_text).strip()
    get_supabase().table(QUESTION_ANSWER_TABLE).insert(row).execute()


def load_voice_interview_state_from_store(session_id: UUID) -> VoiceInterviewState | None:
    session_result = (
        get_supabase()
        .table("interview_sessions")
        .select("current_question_no, question_bank_version, is_interview_complete")
        .eq("id", str(session_id))
        .maybe_single()
        .execute()
    )
    session = session_result.data or {}
    current_question_no = int(session.get("current_question_no") or 1)
    question_bank_version = int(session.get("question_bank_version") or QUESTION_BANK_VERSION)
    is_interview_complete = bool(session.get("is_interview_complete") or False)

    state_rows = (
        get_supabase()
        .table(QUESTION_STATE_TABLE)
        .select(
            "question_no, follow_up_count, last_follow_up_question, "
            "last_decision, last_reason_code, total_score, required_slot_hits, "
            "last_selected_missing_slot, last_pass_route"
        )
        .eq("session_id", str(session_id))
        .order("question_no", desc=False)
        .execute()
    ).data or []

    answer_rows = (
        get_supabase()
        .table(QUESTION_ANSWER_TABLE)
        .select("question_no, user_text")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .execute()
    ).data or []

    if not state_rows and not answer_rows:
        return None

    grouped_answers: dict[str, list[str]] = defaultdict(list)
    for row in answer_rows:
        try:
            question_no = int(row.get("question_no") or 0)
        except (TypeError, ValueError):
            continue
        text = str(row.get("user_text") or "").strip()
        if question_no <= 0 or not text:
            continue
        grouped_answers[str(question_no)].append(text)

    state_row_by_question = {
        int(row.get("question_no") or 0): row
        for row in state_rows
        if int(row.get("question_no") or 0) > 0
    }
    current_row = state_row_by_question.get(current_question_no, {})
    collected_answers = grouped_answers.get(str(current_question_no), [])

    return VoiceInterviewState(
        current_question_no=current_question_no,
        follow_up_count=int(current_row.get("follow_up_count") or 0),
        last_follow_up_question=str(current_row.get("last_follow_up_question") or "").strip() or None,
        collected_answers=collected_answers,
        question_answers=dict(grouped_answers),
        question_bank_version=question_bank_version,
        is_interview_complete=is_interview_complete,
        last_decision=current_row.get("last_decision") or None,
        last_reason_code=str(current_row.get("last_reason_code") or "").strip() or None,
        last_total_score=int(current_row.get("total_score") or 0),
        last_required_slot_hits=int(current_row.get("required_slot_hits") or 0),
        last_selected_missing_slot=current_row.get("last_selected_missing_slot") or None,
        last_pass_route=str(current_row.get("last_pass_route") or "").strip() or None,
    )


def save_voice_interview_state_to_store(session_id: UUID, state: VoiceInterviewState) -> None:
    (
        get_supabase()
        .table("interview_sessions")
        .update(
            {
                "current_question_no": state.current_question_no,
                "question_bank_version": state.question_bank_version or QUESTION_BANK_VERSION,
                "is_interview_complete": state.is_interview_complete,
            }
        )
        .eq("id", str(session_id))
        .execute()
    )

    rows = []
    for question_no in range(1, get_total_question_count() + 1):
        answers = get_question_answers(state, question_no)
        rows.append(
            {
                "session_id": str(session_id),
                "question_no": question_no,
                "main_question": get_interview_question(question_no).main_question,
                "question_hint": get_interview_question(question_no).hint,
                "status": _question_state_status(question_no, state.current_question_no, bool(answers)),
                "follow_up_count": state.follow_up_count if question_no == state.current_question_no else 0,
                "last_follow_up_question": state.last_follow_up_question if question_no == state.current_question_no else None,
                "aggregated_answer_text": "\n".join(answers),
                "has_answer": bool(answers),
                "story_ready": bool(answers),
                "last_decision": state.last_decision if question_no == state.current_question_no else None,
                "last_reason_code": state.last_reason_code if question_no == state.current_question_no else None,
                "total_score": state.last_total_score if question_no == state.current_question_no else 0,
                "required_slot_hits": (
                    state.last_required_slot_hits if question_no == state.current_question_no else 0
                ),
                "last_selected_missing_slot": (
                    state.last_selected_missing_slot if question_no == state.current_question_no else None
                ),
                "last_pass_route": state.last_pass_route if question_no == state.current_question_no else None,
                "question_bank_version": state.question_bank_version or QUESTION_BANK_VERSION,
            }
        )

    get_supabase().table(QUESTION_STATE_TABLE).upsert(
        rows,
        on_conflict="session_id,question_no",
    ).execute()


def delete_voice_interview_state_from_store(session_id: UUID) -> None:
    get_supabase().table(QUESTION_ANSWER_TABLE).delete().eq("session_id", str(session_id)).execute()
    get_supabase().table(QUESTION_STATE_TABLE).delete().eq("session_id", str(session_id)).execute()
