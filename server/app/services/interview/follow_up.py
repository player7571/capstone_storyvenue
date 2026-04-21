from textwrap import dedent

from app.services.interview.decision import looks_like_meaningful_answer, pick_missing_slot
from app.services.interview.llm import get_interview_openai_client
from app.services.interview.types import (
    FollowUpQuestionResponse,
    InterviewQuestion,
    SlotName,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
)

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


def build_slot_follow_up(slot: SlotName, meaningful_answer: bool) -> str:
    base = FOLLOW_UP_BY_SLOT[slot]
    if meaningful_answer:
        return f"말씀해주신 이야기를 이어서, {base}"
    return base


def request_follow_up_question(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    user_text: str,
) -> str | None:
    response = get_interview_openai_client().responses.parse(
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
    return build_slot_follow_up(selected_slot, meaningful_answer)
