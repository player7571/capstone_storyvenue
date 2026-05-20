import logging
from textwrap import dedent

from app.services.interview.decision import looks_like_meaningful_answer, pick_missing_slot
from app.services.interview.llm import get_interview_openai_client
from app.services.interview.types import (
    FollowUpQuestionResponse,
    InterviewerAcknowledgementResponse,
    InterviewQuestion,
    SlotName,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
    VoiceInterviewPromptState,
)

logger = logging.getLogger(__name__)


def _truncate_for_log(value: str | None, limit: int = 300) -> str:
    text = str(value or "").strip()
    if len(text) <= limit:
        return text
    return f"{text[:limit]}...(+{len(text) - limit} chars)"


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

INTERVIEWER_ACKNOWLEDGEMENT_SYSTEM_PROMPT = dedent(
    """
    당신은 노인 사용자의 자서전 인터뷰를 돕는 따뜻한 한국어 AI 인터뷰어입니다.
    사용자의 방금 답변을 받아주는 짧은 반영 문장 1개만 작성하세요.

    규칙:
    - 쉬운 한국어로 작성하세요.
    - 사용자를 평가하지 마세요.
    - 운영 안내(다음 질문, 이야기 생성, 더 말씀해달라 등)는 절대 쓰지 마세요.
    - 사용자의 답변을 길게 반복하지 마세요.
    - 한 문장만 작성하세요.
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

FOLLOW_UP_BY_QUESTION_AND_SLOT: dict[tuple[int, SlotName], str] = {
    (1, "place"): "그때는 어디에서 지내셨나요?",
    (1, "person"): "누구와 함께 지내셨나요?",
    (2, "event"): "그때 어떤 일이 있었는지 조금 더 들려주실 수 있을까요?",
    (2, "scene"): "그 기억 속 장면은 어떻게 떠오르시나요?",
    (3, "person"): "그 시절 기억나는 친구나 선생님이 있으실까요?",
    (3, "event"): "그 또래 시절에는 주로 어떤 일을 하며 지내셨나요?",
    (4, "event"): "그때 하루를 주로 어떻게 보내셨나요?",
    (4, "value"): "그 시절 중요하게 여기던 것이 있었나요?",
    (5, "person"): "그분이 누구인지부터 말씀해주실 수 있을까요?",
    (5, "emotion"): "그분을 떠올리면 어떤 마음이 드시나요?",
    (6, "event"): "어떤 일을 하셨는지 조금 더 들려주실 수 있을까요?",
    (6, "place"): "그 일은 어디에서 하셨나요?",
    (7, "event"): "그 시기에 어떤 일이 있었나요?",
    (7, "value"): "그 시간을 버티게 해준 마음가짐이 있었나요?",
    (8, "event"): "그때 어떤 일이 있었나요?",
    (8, "emotion"): "왜 그렇게 기쁘거나 뿌듯하셨나요?",
    (9, "event"): "어떤 일을 겪고 달라졌다고 느끼셨나요?",
    (9, "value"): "그 뒤에 생각이 어떻게 달라졌나요?",
    (10, "value"): "꼭 남기고 싶은 마음은 무엇인가요?",
    (10, "person"): "그 말을 누구에게 전하고 싶으신가요?",
}

REFOCUS_FOLLOW_UP_BY_QUESTION_NO: dict[int, str] = {
    1: "잘 들었습니다. 어릴 적 살던 곳이나 집안 분위기부터 천천히 떠올려보실래요?",
    2: "괜찮아요. 어릴 때 가장 선명하게 남은 일이나 장면 하나를 먼저 말씀해주세요.",
    3: "좋아요. 학교 기억이나 그 또래 시절 생활부터 편하게 들려주세요.",
    4: "잘 들었어요. 젊었을 때 하루하루 어떻게 지내셨는지부터 말씀해주셔도 좋아요.",
    5: "괜찮아요. 살면서 기억에 남는 사람이 있다면 누구인지부터 말씀해주세요.",
    6: "좋아요. 살면서 했던 일 중 가장 오래 했거나 가장 기억나는 일부터 말씀해주세요.",
    7: "천천히 괜찮습니다. 가장 힘들었던 시기와 그때의 마음을 먼저 들려주세요.",
    8: "좋아요. 가장 기뻤거나 가장 뿌듯했던 순간 하나를 떠올려보셔도 괜찮아요.",
    9: "괜찮아요. 살면서 많이 달라졌다고 느꼈던 일을 하나 떠올려보실래요?",
    10: "잘 들었습니다. 지금 꼭 남기고 싶은 말이 있다면 편하게 말씀해주세요.",
}


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


def build_refocus_follow_up(question: InterviewQuestion) -> str:
    return REFOCUS_FOLLOW_UP_BY_QUESTION_NO.get(
        question.question_no,
        f"잘 들었습니다. {question.main_question}",
    )


def build_slot_follow_up(
    question: InterviewQuestion,
    slot: SlotName,
    meaningful_answer: bool,
) -> str:
    base = FOLLOW_UP_BY_QUESTION_AND_SLOT.get((question.question_no, slot), FOLLOW_UP_BY_SLOT[slot])
    if meaningful_answer:
        return f"말씀해주신 이야기를 이어서, {base}"
    return base


def request_follow_up_question(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    user_text: str,
) -> str | None:
    generation_input = _build_follow_up_generation_input(question, assessment, decision, user_text)
    logger.info(
        "[follow_up_generation] question_no=%s reason_code=%s selected_missing_slot=%s input=%s",
        question.question_no,
        decision.reason_code,
        decision.selected_missing_slot,
        _truncate_for_log(generation_input),
    )
    response = get_interview_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=FOLLOW_UP_GENERATION_SYSTEM_PROMPT,
        input=generation_input,
        temperature=0.6,
        text_format=FollowUpQuestionResponse,
    )
    parsed = response.output_parsed
    if parsed is None:
        logger.warning(
            "[follow_up_generation] question_no=%s parsed_output_missing",
            question.question_no,
        )
        return None
    question_text = parsed.follow_up_question.strip()
    logger.info(
        "[follow_up_generation] question_no=%s output=%s",
        question.question_no,
        _truncate_for_log(question_text),
    )
    return question_text or None


def build_follow_up_fallback(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    cleaned_text: str,
) -> str:
    meaningful_answer = looks_like_meaningful_answer(cleaned_text)
    if assessment.off_topic:
        return build_refocus_follow_up(question)

    selected_slot = decision.selected_missing_slot or pick_missing_slot(question, assessment)
    return build_slot_follow_up(question, selected_slot, meaningful_answer)


def _build_interviewer_acknowledgement_input(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
) -> str:
    return dedent(
        f"""
        메인 질문:
        {question.main_question}

        질문 힌트:
        {question.hint}

        decision:
        {decision.decision}

        reason_code:
        {decision.reason_code}

        selected_missing_slot:
        {decision.selected_missing_slot or "없음"}

        답변 요약:
        {assessment.answer_summary or "없음"}

        현재 답변 관련성:
        relevance={assessment.relevance_score}
        detail={assessment.detail_score}
        reflection={assessment.reflection_score}

        off_topic:
        {"true" if assessment.off_topic else "false"}
        """
    ).strip()


def request_interviewer_acknowledgement(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
) -> str | None:
    response = get_interview_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=INTERVIEWER_ACKNOWLEDGEMENT_SYSTEM_PROMPT,
        input=_build_interviewer_acknowledgement_input(
            question,
            assessment,
            decision,
        ),
        temperature=0.6,
        text_format=InterviewerAcknowledgementResponse,
    )
    parsed = response.output_parsed
    if parsed is None:
        return None
    message = parsed.acknowledgement.strip()
    return message or None


def build_interviewer_acknowledgement_fallback(
    decision: VoiceInterviewDecision,
    assessment: VoiceInterviewAssessment,
) -> str:
    if decision.decision == "follow_up":
        if "scene" in assessment.filled_slots or "event" in assessment.filled_slots:
            return "지금 떠오른 장면이 잘 전해졌어요."
        return "지금 말씀해주신 기억이 잘 전해졌어요."
    if decision.decision == "pass":
        return "말씀해주신 기억이 참 생생하게 전해졌어요."
    if decision.decision == "repeat":
        return ""
    return "잘 들었습니다."


def build_interviewer_guidance(
    decision: VoiceInterviewDecision,
    prompt_state: VoiceInterviewPromptState,
    core_message: str,
) -> str:
    base = core_message.strip()
    if decision.decision == "follow_up":
        if prompt_state.current_question_story_ready:
            return f"지금 말씀만으로도 이야기를 만들 수 있지만, 이 질문에 더 맞게 보려면 {base}"
        return f"이 질문에 더 잘 맞게 보려면 {base}"
    if decision.decision == "pass":
        return "더 떠오르는 게 없으면 다음 질문으로 넘어가거나 지금 이야기를 만들어도 괜찮아요."
    if decision.decision == "repeat":
        return base
    if prompt_state.is_interview_complete:
        return "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
    if decision.decision == "move_on":
        return "기억나는 만큼으로도 충분해요. 다음 이야기로 넘어가볼게요."
    return base
