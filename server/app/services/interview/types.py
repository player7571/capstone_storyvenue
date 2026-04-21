from typing import Literal

from pydantic import BaseModel, Field

INTERVIEW_STATE_ROLE = "assistant"
INTERVIEW_STATE_PREFIX = "__INTERVIEW_STATE__:"

SlotName = Literal["person", "place", "time", "event", "emotion", "scene", "value"]
TurnDecision = Literal["pass", "follow_up", "move_on", "repeat"]
QuestionStatus = Literal["main", "follow_up", "completed"]


class InterviewQuestion(BaseModel):
    question_no: int
    main_question: str
    hint: str
    target_slots: list[SlotName]
    required_slots: list[SlotName] = Field(default_factory=list)
    required_slot_min_hits: int = 1
    min_filled_slots: int = 2
    pass_score: int = 3
    alt_pass_routes: list[list[SlotName]] = Field(default_factory=list)
    allow_emotion_exception: bool = False
    base_follow_ups: int = 2
    near_pass_extra_follow_ups: int = 1


class VoiceInterviewState(BaseModel):
    current_question_no: int = 1
    follow_up_count: int = 0
    last_follow_up_question: str | None = None
    collected_answers: list[str] = Field(default_factory=list)
    question_answers: dict[str, list[str]] = Field(default_factory=dict)
    question_bank_version: int = 1
    is_interview_complete: bool = False
    last_decision: TurnDecision | None = None
    last_reason_code: str | None = None
    last_total_score: int = 0
    last_required_slot_hits: int = 0
    last_selected_missing_slot: SlotName | None = None
    last_pass_route: str | None = None


class VoiceInterviewPromptState(BaseModel):
    current_question_no: int
    total_questions: int
    main_question: str
    question_hint: str | None = None
    follow_up_count: int = 0
    question_status: QuestionStatus = "main"
    progress_percent: int
    is_interview_complete: bool = False


class VoiceInterviewAssessment(BaseModel):
    filled_slots: list[SlotName] = Field(default_factory=list)
    missing_slots: list[SlotName] = Field(default_factory=list)
    answer_summary: str = ""
    relevance_score: int = 0
    detail_score: int = 0
    reflection_score: int = 0
    transcript_unclear: bool = False
    off_topic: bool = False


class FollowUpQuestionResponse(BaseModel):
    follow_up_question: str


class VoiceInterviewDecision(BaseModel):
    decision: TurnDecision
    reason_code: str
    selected_missing_slot: SlotName | None = None
    pass_route: str | None = None
    total_score: int = 0
    required_slot_hits: int = 0
    follow_up_question: str | None = None


class VoiceInterviewTurnOutcome(BaseModel):
    decision: TurnDecision
    assistant_text: str
    next_state: VoiceInterviewState
    prompt_state: VoiceInterviewPromptState
    answer_summary: str = ""
    filled_slots: list[SlotName] = Field(default_factory=list)
    missing_slots: list[SlotName] = Field(default_factory=list)
