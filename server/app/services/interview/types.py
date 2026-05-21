from typing import Literal

from pydantic import BaseModel, Field

INTERVIEW_STATE_ROLE = "assistant"
INTERVIEW_STATE_PREFIX = "__INTERVIEW_STATE__:"

ChapterType = Literal["childhood", "youth", "career", "love", "reflection"]
SlotName = Literal["person", "place", "time", "event", "emotion", "scene", "value"]
TurnDecision = Literal["pass", "follow_up", "move_on", "repeat"]
QuestionStatus = Literal["main", "follow_up", "completed"]
StoryQuality = Literal["none", "basic", "almost_ready", "ready"]
QuestionFlow = Literal[
    "default",
    "event_sequence",
    "person_focus",
    "value_focus",
    "background_memory",
    "peer_life_memory",
    "person_memory",
    "legacy_message",
]
EmotionalTone = Literal["positive", "negative", "fearful", "warm", "neutral"]
EmotionalBlend = Literal[
    "none",
    "warm_relief",
    "support",
    "gratitude",
    "pride_after_hardship",
    "sad_warmth",
    "regret",
]
AckTone = Literal["comfort", "fear_ack", "warm", "celebrate", "neutral"]
EmotionAlignment = Literal["aligned", "over_positive", "ungrounded"]
QuestionAxis = Literal["event", "scene", "result", "emotion", "reason", "person", "none"]
QuestionFocus = Literal["setup", "development", "result", "emotion", "meaning", "person", "none"]
FollowUpGoal = Literal[
    "deepen_person",
    "deepen_scene",
    "deepen_result",
    "deepen_event",
    "deepen_emotion",
    "deepen_reason",
    "refocus",
    "close",
    "retry",
]


class InterviewQuestion(BaseModel):
    question_no: int
    main_question: str
    hint: str
    chapter_type: ChapterType
    target_slots: list[SlotName]
    required_slots: list[SlotName] = Field(default_factory=list)
    required_slot_min_hits: int = 1
    min_filled_slots: int = 2
    pass_score: int = 3
    alt_pass_routes: list[list[SlotName]] = Field(default_factory=list)
    story_generatable_routes: list[list[SlotName]] = Field(default_factory=list)
    story_generatable_min_length: int = 30
    story_min_detail_score: int = 1
    story_min_distinct_slots: int = 2
    story_min_reflection_score: int = 0
    follow_up_flow: QuestionFlow = "default"
    base_follow_ups: int = 2
    near_pass_extra_follow_ups: int = 1


class VoiceInterviewState(BaseModel):
    current_question_no: int = 1
    follow_up_count: int = 0
    last_follow_up_question: str | None = None
    collected_answers: list[str] = Field(default_factory=list)
    question_answers: dict[str, list[str]] = Field(default_factory=dict)
    question_statuses: dict[str, str] = Field(default_factory=dict)
    question_story_qualities: dict[str, StoryQuality] = Field(default_factory=dict)
    question_story_ready_flags: dict[str, bool] = Field(default_factory=dict)
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
    current_question_has_answer: bool = False
    current_question_answer_count: int = 0
    current_question_story_ready: bool = False
    current_question_story_quality: StoryQuality = "none"
    current_question_completed: bool = False
    current_question_can_move_next: bool = False
    story_target_question_no: int | None = None
    story_target_has_answer: bool = False
    story_target_answer_count: int = 0
    story_target_story_ready: bool = False
    story_target_story_quality: StoryQuality = "none"
    story_target_is_current_question: bool = True


class VoiceInterviewAssessment(BaseModel):
    filled_slots: list[SlotName] = Field(default_factory=list)
    missing_slots: list[SlotName] = Field(default_factory=list)
    answer_summary: str = ""
    relevance_score: int = 0
    detail_score: int = 0
    reflection_score: int = 0
    flow_type: QuestionFlow = "default"
    emotional_tone: EmotionalTone = "neutral"
    emotional_blend: EmotionalBlend = "none"
    person_present: bool = False
    setup_present: bool = False
    development_present: bool = False
    result_present: bool = False
    emotion_present: bool = False
    meaning_present: bool = False
    transcript_unclear: bool = False
    off_topic: bool = False
    question_echo: bool = False


class FollowUpQuestionResponse(BaseModel):
    follow_up_question: str


class InterviewerAcknowledgementResponse(BaseModel):
    acknowledgement: str


class InterviewerTurnResponse(BaseModel):
    assistant_text: str
    next_question: str | None = None
    ack_tone: AckTone | None = None
    emotion_alignment: EmotionAlignment = "aligned"
    question_axis: QuestionAxis | None = None
    question_focus: QuestionFocus | None = None


class VoiceInterviewDecision(BaseModel):
    decision: TurnDecision
    reason_code: str
    selected_missing_slot: SlotName | None = None
    pass_route: str | None = None
    follow_up_goal: FollowUpGoal | None = None
    total_score: int = 0
    required_slot_hits: int = 0
    follow_up_question: str | None = None


class VoiceInterviewTurnOutcome(BaseModel):
    decision: TurnDecision
    assistant_text: str
    next_state: VoiceInterviewState
    prompt_state: VoiceInterviewPromptState
    reason_code: str | None = None
    answer_summary: str = ""
    filled_slots: list[SlotName] = Field(default_factory=list)
    missing_slots: list[SlotName] = Field(default_factory=list)
