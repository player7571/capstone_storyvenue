from functools import lru_cache
from textwrap import dedent
from typing import Literal

from openai import OpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings

INTERVIEW_STATE_ROLE = "assistant"
INTERVIEW_STATE_PREFIX = "__INTERVIEW_STATE__:"
QUESTION_BANK_VERSION = 1
MAX_FOLLOW_UPS = 2
MAX_EXTRA_FOLLOW_UPS_FOR_NEAR_PASS = 1

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
    base_follow_ups: int = MAX_FOLLOW_UPS
    near_pass_extra_follow_ups: int = MAX_EXTRA_FOLLOW_UPS_FOR_NEAR_PASS


class VoiceInterviewState(BaseModel):
    current_question_no: int = 1
    follow_up_count: int = 0
    last_follow_up_question: str | None = None
    collected_answers: list[str] = Field(default_factory=list)
    question_answers: dict[str, list[str]] = Field(default_factory=dict)
    question_bank_version: int = QUESTION_BANK_VERSION
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


VOICE_INTERVIEW_QUESTIONS: list[InterviewQuestion] = [
    InterviewQuestion(
        question_no=1,
        main_question="어린 시절은 어떠했나요?",
        hint="집, 가족, 동네 중 떠오르는 것부터 말씀해주세요.",
        target_slots=["place", "person", "scene", "emotion"],
        required_slots=["place", "person"],
        alt_pass_routes=[
            ["place", "person"],
            ["place", "emotion"],
            ["scene", "emotion"],
        ],
        allow_emotion_exception=True,
    ),
    InterviewQuestion(
        question_no=2,
        main_question="젊었을 때의 나는 어떤 사람이었나요?",
        hint="그때 자주 하던 일이나 마음을 떠올려보셔도 좋아요.",
        target_slots=["event", "emotion", "value", "scene"],
        required_slots=["event", "value"],
        alt_pass_routes=[
            ["event", "emotion"],
            ["value", "emotion"],
            ["event", "scene"],
        ],
    ),
    InterviewQuestion(
        question_no=3,
        main_question="살면서 기억에 남는 사람이 있나요?",
        hint="가족, 친구, 이웃, 선생님 누구든 괜찮아요.",
        target_slots=["person", "event", "emotion", "value"],
        required_slots=["person"],
        alt_pass_routes=[
            ["person", "emotion"],
            ["person", "value"],
            ["person", "event"],
        ],
    ),
    InterviewQuestion(
        question_no=4,
        main_question="오래도록 마음에 남은 일이 있나요?",
        hint="기뻤던 일이나 힘들었던 일 중 하나를 말씀해주세요.",
        target_slots=["event", "time", "emotion", "scene"],
        required_slots=["event"],
        alt_pass_routes=[
            ["event", "emotion"],
            ["event", "time"],
            ["event", "scene"],
        ],
        allow_emotion_exception=True,
    ),
    InterviewQuestion(
        question_no=5,
        main_question="살면서 많이 달라졌다고 느낀 때가 있나요?",
        hint="어떤 일을 겪은 뒤 생각이나 생활이 달라졌는지 떠올려보셔도 좋아요.",
        target_slots=["event", "time", "value", "emotion"],
        required_slots=["event", "value"],
        alt_pass_routes=[
            ["event", "value"],
            ["event", "emotion"],
            ["time", "value"],
        ],
    ),
    InterviewQuestion(
        question_no=6,
        main_question="가족과 함께한 시간 중 기억에 남는 순간이 있나요?",
        hint="특별한 날이 아니어도 좋고, 평범한 하루도 괜찮아요.",
        target_slots=["person", "event", "emotion", "scene"],
        required_slots=["person", "event"],
        alt_pass_routes=[
            ["person", "emotion"],
            ["event", "emotion"],
            ["scene", "emotion"],
        ],
    ),
    InterviewQuestion(
        question_no=7,
        main_question="일하거나 바쁘게 지내던 시절 이야기를 들려주실 수 있나요?",
        hint="처음 시작한 일이나 오래 했던 일을 말씀해주셔도 좋아요.",
        target_slots=["event", "time", "place", "emotion"],
        required_slots=["event", "time", "place"],
        alt_pass_routes=[
            ["event", "place"],
            ["event", "time"],
            ["event", "emotion"],
        ],
        near_pass_extra_follow_ups=2,
    ),
    InterviewQuestion(
        question_no=8,
        main_question="힘들 때 나를 버티게 해준 것이 있었나요?",
        hint="사람, 말, 일, 마음가짐 중 떠오르는 것이 있으면 말씀해주세요.",
        target_slots=["person", "event", "emotion", "value"],
        required_slots=["person", "event", "value"],
        alt_pass_routes=[
            ["person", "emotion"],
            ["value", "emotion"],
            ["person", "value"],
        ],
        allow_emotion_exception=True,
        near_pass_extra_follow_ups=2,
    ),
    InterviewQuestion(
        question_no=9,
        main_question="지금도 자주 떠오르는 장면이나 기억이 있나요?",
        hint="어떤 때였는지, 왜 기억나는지만 편하게 말씀해주세요.",
        target_slots=["scene", "place", "event", "emotion"],
        required_slots=["scene", "event"],
        alt_pass_routes=[
            ["scene", "emotion"],
            ["scene", "place"],
            ["event", "emotion"],
        ],
        allow_emotion_exception=True,
    ),
    InterviewQuestion(
        question_no=10,
        main_question="지금 돌아보면 꼭 남기고 싶은 이야기가 있나요?",
        hint="가족에게 하고 싶은 말이나 소중했던 것을 말씀해주셔도 좋아요.",
        target_slots=["value", "person", "emotion", "event"],
        required_slots=["value", "person", "event"],
        alt_pass_routes=[
            ["value", "person"],
            ["value", "emotion"],
            ["person", "emotion"],
        ],
        allow_emotion_exception=True,
        near_pass_extra_follow_ups=2,
    ),
]

ASSESSMENT_SYSTEM_PROMPT = dedent(
    """
    당신은 노인 사용자의 자서전 인터뷰를 돕는 한국어 인터뷰 분석 도우미입니다.
    현재 질문과 사용자의 누적 답변을 보고, 어떤 정보가 이미 나왔는지 구조화해서 반환하세요.

    중요:
    - 최종 통과 여부(pass/follow_up/move_on/repeat)는 당신이 결정하지 않습니다.
    - 당신은 정보 추출과 점수화만 담당합니다.
    - filled_slots와 missing_slots에는 반드시 person, place, time, event, emotion, scene, value 중에서만 고르세요.
    - relevance_score는 0~2:
      0 = 질문과 거의 무관함
      1 = 부분적으로 관련 있음
      2 = 질문에 분명히 맞는 답변
    - detail_score는 0~2:
      0 = 정보가 거의 없음
      1 = 정보가 1개 정도 있음
      2 = 정보가 2개 이상 비교적 또렷함
    - reflection_score는 0~1:
      0 = 감정/의미/가치가 거의 없음
      1 = 감정이나 의미가 드러남
    - transcript_unclear는 소음이 많거나 뜻을 거의 파악하기 어려울 때만 true로 하세요.
    - off_topic은 말 자체는 들리지만 현재 질문과 방향이 꽤 어긋날 때만 true로 하세요.
    - answer_summary는 사용자의 답변을 1문장 이내로 짧게 요약합니다.
    """
).strip()

FOLLOW_UP_GENERATION_SYSTEM_PROMPT = dedent(
    """
    당신은 노인 사용자의 자서전 인터뷰를 돕는 따뜻한 한국어 인터뷰어입니다.
    주어진 메인 질문과 사용자의 최근 답변 요약을 바탕으로, 빠진 정보 하나만 자연스럽게 묻는 보조 질문 1문장을 만드세요.

    규칙:
    - 쉬운 한국어로 작성하세요.
    - 한 문장만 작성하세요.
    - 사용자를 평가하지 마세요.
    - off_topic이 true이면 현재 질문으로 부드럽게 다시 이끄세요.
    - selected_missing_slot이 있으면 그 정보 하나만 보완하도록 유도하세요.
    - 너무 길지 않게 35자 안팎으로 작성하세요.
    """
).strip()

FOLLOW_UP_BY_SLOT: dict[SlotName, str] = {
    "person": "그때 함께한 사람이 떠오르신다면 누구였을까요?",
    "place": "그때 어디였는지도 기억나시나요?",
    "time": "그 일은 언제쯤이었는지도 말씀해주실 수 있을까요?",
    "event": "그때 어떤 일이 있었는지 조금만 더 들려주실 수 있을까요?",
    "emotion": "그때 마음은 어떠셨는지 떠오르시나요?",
    "scene": "그 장면에서 가장 먼저 생각나는 모습이 있으실까요?",
    "value": "그 일을 겪으며 어떤 생각이 남으셨는지도 궁금해요.",
}

REFOCUS_FOLLOW_UP_BY_QUESTION_NO: dict[int, str] = {
    1: "지금 말씀도 잘 들었어요. 어린 시절을 떠올리면 집이나 가족, 동네 모습 중 먼저 생각나는 게 있으실까요?",
    2: "지금 말씀해주신 것도 괜찮아요. 젊었을 때 자주 하던 일이나 그때 마음부터 편하게 떠올려보실래요?",
    3: "잘 들었습니다. 살면서 기억에 남는 사람이 있다면 누구인지부터 천천히 말씀해주실 수 있을까요?",
    4: "괜찮아요. 오래도록 마음에 남은 일 하나를 떠올리신다면 어떤 일이 먼저 생각나시나요?",
    5: "잘 들었어요. 살면서 많이 달라졌다고 느꼈던 때를 하나만 떠올려보셔도 괜찮아요.",
    6: "좋아요. 가족과 함께한 시간 중에 아직도 기억나는 장면 하나를 편하게 말씀해주실 수 있을까요?",
    7: "괜찮아요. 일하거나 바쁘게 지내던 시절 이야기 중 하나만 먼저 들려주셔도 좋아요.",
    8: "잘 들었습니다. 힘들 때 나를 버티게 해준 사람이나 마음가짐이 있었는지 떠올려보실래요?",
    9: "좋아요. 지금도 자주 떠오르는 장면이 있다면 그때 모습부터 천천히 말씀해주세요.",
    10: "잘 들었어요. 지금 돌아보며 꼭 남기고 싶은 말이나 이야기가 있다면 편하게 들려주세요.",
}

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

LOCAL_SLOT_KEYWORDS: dict[SlotName, tuple[str, ...]] = {
    "person": (
        "어머니",
        "아버지",
        "엄마",
        "아빠",
        "형",
        "누나",
        "언니",
        "오빠",
        "동생",
        "할머니",
        "할아버지",
        "친구",
        "선생님",
        "가족",
        "아내",
        "남편",
        "아이",
        "자식",
    ),
    "place": (
        "서울",
        "부산",
        "대구",
        "인천",
        "광주",
        "시골",
        "마을",
        "동네",
        "집",
        "학교",
        "공장",
        "시장",
        "회사",
        "교회",
        "역",
    ),
    "time": (
        "어릴 때",
        "젊었을 때",
        "그때",
        "옛날",
        "초등학교",
        "중학교",
        "고등학교",
        "스무 살",
        "스무살",
        "서른",
        "마흔",
        "명절",
        "겨울",
        "여름",
        "봄",
        "가을",
    ),
    "event": (
        "결혼",
        "졸업",
        "입학",
        "취직",
        "취업",
        "일했",
        "장사",
        "이사",
        "전쟁",
        "사고",
        "병원",
        "출근",
        "첫 직장",
        "군대",
    ),
    "emotion": (
        "외로",
        "기뻤",
        "슬펐",
        "무서",
        "걱정",
        "감사",
        "행복",
        "먹먹",
        "따뜻",
        "힘들",
        "좋았",
        "떨렸",
        "긴장",
        "뿌듯",
    ),
    "scene": (
        "장면",
        "모습",
        "기억",
        "눈앞",
        "마당",
        "냇가",
        "식탁",
        "밥상",
        "기차역",
        "시장",
        "길",
    ),
    "value": (
        "책임감",
        "소중",
        "감사",
        "버텨",
        "참아",
        "사람 마음",
        "가족이 먼저",
        "정직",
        "성실",
        "포기",
    ),
}


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def get_total_question_count() -> int:
    return len(VOICE_INTERVIEW_QUESTIONS)


def get_interview_question(question_no: int) -> InterviewQuestion:
    total = get_total_question_count()
    safe_question_no = min(max(question_no, 1), total)
    return VOICE_INTERVIEW_QUESTIONS[safe_question_no - 1]


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


def _append_question_answer(
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
    except Exception:  # noqa: BLE001
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

    target_answers = _dedupe_answers(preserved_question_answers.get(_question_key(target_question_no), []))
    return VoiceInterviewState(
        current_question_no=target_question_no,
        follow_up_count=0,
        last_follow_up_question=None,
        collected_answers=target_answers,
        question_answers=preserved_question_answers,
        question_bank_version=state.question_bank_version,
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


def _build_assessment_input(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    user_text: str,
) -> str:
    current_answers = get_question_answers(state, state.current_question_no)
    previous_answers = "\n".join(f"- {text}" for text in current_answers if text.strip())
    previous_answers_text = previous_answers or "- 없음"
    last_follow_up = state.last_follow_up_question or "없음"

    return dedent(
        f"""
        현재 메인 질문:
        {question.main_question}

        질문 힌트:
        {question.hint}

        이 질문에서 보고 싶은 정보:
        {", ".join(question.target_slots)}

        이 질문에서 특히 중요하게 보고 싶은 정보:
        {", ".join(question.required_slots) if question.required_slots else "없음"}

        이전까지 모인 답변:
        {previous_answers_text}

        직전에 물었던 보조 질문:
        {last_follow_up}

        이번 사용자 답변:
        {user_text}

        지금까지 사용한 보조 질문 횟수:
        {state.follow_up_count}
        """
    ).strip()


def _build_follow_up_generation_input(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    user_text: str,
) -> str:
    return dedent(
        f"""
        메인 질문:
        {question.main_question}

        질문 힌트:
        {question.hint}

        최근 사용자 답변:
        {user_text}

        사용자 답변 요약:
        {assessment.answer_summary or user_text}

        이미 확인된 정보:
        {", ".join(assessment.filled_slots) if assessment.filled_slots else "없음"}

        아직 더 필요한 정보:
        {", ".join(assessment.missing_slots) if assessment.missing_slots else "없음"}

        selected_missing_slot:
        {decision.selected_missing_slot or "없음"}

        off_topic:
        {"true" if assessment.off_topic else "false"}
        """
    ).strip()


def _looks_like_meaningful_answer(text: str) -> bool:
    stripped = text.strip()
    if len(stripped) >= 6:
        return True
    if " " in stripped and len(stripped) >= 4:
        return True
    return False


def _build_refocus_follow_up(question: InterviewQuestion) -> str:
    return REFOCUS_FOLLOW_UP_BY_QUESTION_NO.get(
        question.question_no,
        f"잘 들었습니다. {question.main_question}",
    )


def _request_assessment(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    user_text: str,
) -> VoiceInterviewAssessment:
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=ASSESSMENT_SYSTEM_PROMPT,
        input=_build_assessment_input(question, state, user_text),
        temperature=0.2,
        text_format=VoiceInterviewAssessment,
    )

    parsed = response.output_parsed
    if parsed is None:
        raise RuntimeError("인터뷰 답변 평가 응답을 해석하지 못했습니다.")
    return parsed


def _request_follow_up_question(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    user_text: str,
) -> str | None:
    response = _get_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=FOLLOW_UP_GENERATION_SYSTEM_PROMPT,
        input=_build_follow_up_generation_input(question, assessment, decision, user_text),
        temperature=0.6,
        text_format=FollowUpQuestionResponse,
    )
    parsed = response.output_parsed
    if parsed is None:
        return None
    question_text = parsed.follow_up_question.strip()
    return question_text or None


def _extract_local_slots(text: str) -> list[SlotName]:
    lowered = text.lower()
    found: list[SlotName] = []
    for slot, keywords in LOCAL_SLOT_KEYWORDS.items():
        if any(keyword.lower() in lowered for keyword in keywords):
            found.append(slot)
    return found


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

    combined_text = " ".join(
        [*get_question_answers(state, state.current_question_no), user_text]
    ).strip()
    for slot in _extract_local_slots(combined_text):
        if slot not in assessment.filled_slots:
            assessment.filled_slots.append(slot)

    if (
        "emotion" in assessment.filled_slots
        and assessment.reflection_score < 1
        and question.allow_emotion_exception
    ):
        assessment.reflection_score = 1

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
        ] or list(question.target_slots)

    return assessment


def _count_required_slot_hits(question: InterviewQuestion, filled_slots: list[SlotName]) -> int:
    if not question.required_slots:
        return 0
    return sum(1 for slot in question.required_slots if slot in filled_slots)


def _total_assessment_score(assessment: VoiceInterviewAssessment) -> int:
    return assessment.relevance_score + assessment.detail_score + assessment.reflection_score


def _passes_question_rules(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> bool:
    required_hits = _count_required_slot_hits(question, assessment.filled_slots)
    has_required_slots = (
        not question.required_slots or required_hits >= question.required_slot_min_hits
    )
    has_enough_slots = len(assessment.filled_slots) >= question.min_filled_slots
    has_enough_relevance = assessment.relevance_score >= 1
    has_enough_score = _total_assessment_score(assessment) >= question.pass_score
    return has_required_slots and has_enough_slots and has_enough_relevance and has_enough_score


def _matched_alt_pass_route(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> str | None:
    if assessment.relevance_score < 1:
        return None

    total_score = _total_assessment_score(assessment)
    for route in question.alt_pass_routes:
        if all(slot in assessment.filled_slots for slot in route) and total_score >= max(
            question.pass_score - 1,
            2,
        ):
            return "+".join(route)
    return None


def _passes_emotion_exception(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> bool:
    return (
        question.allow_emotion_exception
        and assessment.relevance_score >= 1
        and "emotion" in assessment.filled_slots
        and _total_assessment_score(assessment) >= max(question.pass_score - 1, 2)
    )


def _is_near_pass(question: InterviewQuestion, assessment: VoiceInterviewAssessment) -> bool:
    required_hits = _count_required_slot_hits(question, assessment.filled_slots)
    required_ok = not question.required_slots or required_hits >= question.required_slot_min_hits
    score_gap = question.pass_score - _total_assessment_score(assessment)
    slot_gap = question.min_filled_slots - len(assessment.filled_slots)
    return (
        assessment.relevance_score >= 1
        and required_ok
        and score_gap <= 1
        and slot_gap <= 1
    )


def _get_allowed_follow_ups(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
) -> int:
    if _is_near_pass(question, assessment):
        return question.base_follow_ups + question.near_pass_extra_follow_ups
    return question.base_follow_ups


def _pick_missing_slot(
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


def _build_slot_follow_up(slot: SlotName, meaningful_answer: bool) -> str:
    base = FOLLOW_UP_BY_SLOT[slot]
    if meaningful_answer:
        return f"말씀해주신 이야기를 이어서, {base}"
    return base


def _build_follow_up_fallback(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    cleaned_text: str,
) -> str:
    meaningful_answer = _looks_like_meaningful_answer(cleaned_text)
    if assessment.off_topic:
        return _build_refocus_follow_up(question)

    selected_slot = decision.selected_missing_slot or _pick_missing_slot(question, assessment)
    return _build_slot_follow_up(selected_slot, meaningful_answer)


def _decide_interview_turn(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    cleaned_text: str,
) -> VoiceInterviewDecision:
    total_score = _total_assessment_score(assessment)
    required_hits = _count_required_slot_hits(question, assessment.filled_slots)
    meaningful_answer = _looks_like_meaningful_answer(cleaned_text)

    if assessment.transcript_unclear and not meaningful_answer:
        return VoiceInterviewDecision(
            decision="repeat",
            reason_code="transcript_unclear",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    if _passes_question_rules(question, assessment):
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="score_and_slots",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    pass_route = _matched_alt_pass_route(question, assessment)
    if pass_route is not None:
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="alt_pass_route",
            pass_route=pass_route,
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    if _passes_emotion_exception(question, assessment):
        return VoiceInterviewDecision(
            decision="pass",
            reason_code="emotion_exception",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    allowed_follow_ups = _get_allowed_follow_ups(question, assessment)
    if state.follow_up_count >= allowed_follow_ups:
        return VoiceInterviewDecision(
            decision="move_on",
            reason_code="follow_up_limit",
            total_score=total_score,
            required_slot_hits=required_hits,
        )

    selected_missing_slot = _pick_missing_slot(question, assessment)
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

    assessment = _normalize_assessment(
        question,
        state,
        _request_assessment(question, state, cleaned_text),
        cleaned_text,
    )
    decision = _decide_interview_turn(question, state, assessment, cleaned_text)

    if decision.decision == "follow_up":
        decision.follow_up_question = _request_follow_up_question(
            question,
            assessment,
            decision,
            cleaned_text,
        )
        if not decision.follow_up_question:
            decision.follow_up_question = _build_follow_up_fallback(
                question,
                assessment,
                decision,
                cleaned_text,
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


def _build_follow_up_state(
    state: VoiceInterviewState,
    user_text: str,
    decision: VoiceInterviewDecision,
) -> VoiceInterviewState:
    answers = get_question_answers(state, state.current_question_no)
    next_answers = _append_question_answer(state, state.current_question_no, user_text)
    updated_current_answers = next_answers.get(_question_key(state.current_question_no), answers)
    next_state = VoiceInterviewState(
        current_question_no=state.current_question_no,
        follow_up_count=state.follow_up_count + 1,
        last_follow_up_question=decision.follow_up_question,
        collected_answers=updated_current_answers,
        question_answers=next_answers,
        question_bank_version=state.question_bank_version,
        is_interview_complete=False,
    )
    return _build_state_with_metadata(next_state, decision)


def _build_next_question_state(
    state: VoiceInterviewState,
    user_text: str | None,
    decision: VoiceInterviewDecision,
) -> VoiceInterviewState:
    next_question_answers = _append_question_answer(
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
            collected_answers=next_question_answers.get(_question_key(total_questions), []),
            question_answers=next_question_answers,
            question_bank_version=state.question_bank_version,
            is_interview_complete=True,
        )
        return _build_state_with_metadata(next_state, decision)

    next_state = VoiceInterviewState(
        current_question_no=next_question_no,
        follow_up_count=0,
        last_follow_up_question=None,
        collected_answers=next_question_answers.get(_question_key(next_question_no), []),
        question_answers=next_question_answers,
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
            answer_summary="이미 인터뷰 완료 상태",
        )

    question = get_interview_question(state.current_question_no)
    assessment, decision = assess_voice_interview_answer(question, state, user_text)
    summary = assessment.answer_summary.strip() or user_text.strip()

    if decision.decision == "repeat":
        prompt_state = build_voice_interview_prompt_state(state)
        return VoiceInterviewTurnOutcome(
            decision="repeat",
            assistant_text="말씀을 정확히 알아듣지 못했어요. 같은 내용을 조금만 천천히 다시 말씀해주세요.",
            next_state=_build_state_with_metadata(state, decision),
            prompt_state=prompt_state,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    if decision.decision == "follow_up":
        safe_follow_up_question = (
            decision.follow_up_question
            or _build_follow_up_fallback(question, assessment, decision, user_text.strip())
        ).strip()
        decision.follow_up_question = safe_follow_up_question
        next_state = _build_follow_up_state(state, user_text, decision)
        prompt_state = build_voice_interview_prompt_state(next_state)
        return VoiceInterviewTurnOutcome(
            decision="follow_up",
            assistant_text=safe_follow_up_question,
            next_state=next_state,
            prompt_state=prompt_state,
            answer_summary=summary,
            filled_slots=assessment.filled_slots,
            missing_slots=assessment.missing_slots,
        )

    should_store_answer = decision.reason_code != "user_skip_requested"
    next_state = _build_next_question_state(
        state,
        user_text if should_store_answer else None,
        decision,
    )
    prompt_state = build_voice_interview_prompt_state(next_state)

    if next_state.is_interview_complete:
        assistant_text = "잘 들었습니다. 질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
    elif decision.decision == "move_on":
        assistant_text = "괜찮아요. 기억나는 만큼으로도 충분해요. 다음 이야기로 넘어가볼게요."
    else:
        assistant_text = "잘 들었습니다. 다음 이야기로 넘어가볼게요."

    return VoiceInterviewTurnOutcome(
        decision=decision.decision,
        assistant_text=assistant_text,
        next_state=next_state,
        prompt_state=prompt_state,
        answer_summary=summary,
        filled_slots=assessment.filled_slots,
        missing_slots=assessment.missing_slots,
    )
