import logging
from textwrap import dedent

from app.services.interview.decision import looks_like_meaningful_answer, pick_missing_slot
from app.services.interview.llm import (
    build_interview_prompt_cache_body,
    get_interview_openai_client,
    log_interview_prompt_cache_usage,
)
from app.services.interview.question_prompts import (
    build_follow_up_generation_system_prompt,
    build_interviewer_turn_system_prompt,
    get_question_fallback_question,
    get_question_goal_priority,
)
from app.services.interview.state import get_question_answers
from app.services.interview.types import (
    AckTone,
    EmotionAlignment,
    FollowUpQuestionResponse,
    InterviewerAcknowledgementResponse,
    InterviewerTurnResponse,
    FollowUpGoal,
    InterviewQuestion,
    QuestionAxis,
    QuestionFocus,
    SlotName,
    VoiceInterviewAssessment,
    VoiceInterviewDecision,
    VoiceInterviewPromptState,
    VoiceInterviewState,
)

logger = logging.getLogger(__name__)


def _truncate_for_log(value: str | None, limit: int = 300) -> str:
    text = str(value or "").strip()
    if len(text) <= limit:
        return text
    return f"{text[:limit]}...(+{len(text) - limit} chars)"


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

        감정:
        emotional_tone={assessment.emotional_tone}
        emotional_blend={assessment.emotional_blend}

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
        instructions=build_follow_up_generation_system_prompt(question),
        input=generation_input,
        temperature=0.6,
        max_output_tokens=80,
        text_format=FollowUpQuestionResponse,
        extra_body=build_interview_prompt_cache_body("follow_up_generation", question),
    )
    log_interview_prompt_cache_usage(logger, "follow_up_generation", question, response)
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

    goal = decision.follow_up_goal
    policy_fallback = get_question_fallback_question(question, goal)
    if policy_fallback:
        return policy_fallback

    if question.follow_up_flow == "background_memory":
        if goal == "deepen_scene":
            if "place" not in assessment.filled_slots:
                return "그때 살던 집이나 동네에서 먼저 떠오르는 모습이 있으실까요?"
            return "그 시절 집안 분위기나 눈앞에 떠오르는 장면이 있으실까요?"
        if goal == "deepen_person":
            return "그때 함께 지내던 가족이나 먼저 떠오르는 분은 누구신가요?"
        if goal == "deepen_emotion":
            return "지금 돌아보면 그 시절은 어떤 느낌으로 남아 있으신가요?"

    if question.follow_up_flow == "peer_life_memory":
        if goal == "deepen_event":
            return (
                "그 시절 하루는 어떻게 흘러갔는지 먼저 들려주실 수 있을까요?"
                if meaningful_answer
                else "그 시절 하루는 주로 어떻게 보내셨나요?"
            )
        if goal == "deepen_person":
            return "그 시절 기억나는 친구나 선생님, 또는 함께 지낸 분이 있으실까요?"
        if goal == "deepen_scene":
            return "교실이나 집안일하던 모습처럼 먼저 떠오르는 장면이 있으실까요?"
        if goal == "deepen_emotion":
            return "지금 돌아보면 그 시절은 어떤 느낌으로 남아 있으신가요?"
        if goal == "deepen_reason":
            return "그 시절이 선생님께 어떤 의미로 남아 있는지도 들려주실 수 있을까요?"

    if question.follow_up_flow == "person_memory":
        if goal == "deepen_person":
            return "그분은 평소 어떤 분이셨는지 먼저 들려주실 수 있을까요?"
        if goal == "deepen_event":
            return "그분과 함께했던 일 중 먼저 떠오르는 일이 있으실까요?"
        if goal == "deepen_scene":
            return "그분과 함께 있던 장면 중 가장 먼저 떠오르는 모습이 있으실까요?"
        if goal == "deepen_emotion":
            return "그분을 떠올리면 지금 어떤 마음이 가장 먼저 드시나요?"
        if goal == "deepen_reason":
            return "그분이 왜 오래 마음에 남으셨는지도 들려주실 수 있을까요?"

    if question.follow_up_flow == "legacy_message":
        if goal == "deepen_reason":
            if "value" not in assessment.filled_slots and not assessment.meaning_present:
                return "어떤 말을 꼭 남기고 싶으신지 조금 더 들려주실 수 있을까요?"
            return "왜 그 말을 꼭 남기고 싶으신지도 들려주실 수 있을까요?"
        if goal == "deepen_person":
            return "그 말을 가장 전하고 싶은 분은 누구이실까요?"
        if goal == "deepen_event":
            return "그런 생각을 하게 된 일이 있으셨을까요?"
        if goal == "deepen_emotion":
            return "그 말을 떠올리면 지금 어떤 마음이 드시나요?"

    if goal == "deepen_scene":
        return (
            "그때 어떤 장면이 펼쳐졌는지 먼저 들려주실 수 있을까요?"
            if meaningful_answer
            else "그때 어떤 장면이 먼저 떠오르시나요?"
        )
    if goal == "deepen_result":
        return (
            "그 뒤에는 어떻게 되었는지 이어서 들려주실 수 있을까요?"
            if meaningful_answer
            else "그 뒤에는 어떻게 되었나요?"
        )
    if goal == "deepen_emotion":
        return (
            "그때 마음에는 어떤 감정이 가장 먼저 들었는지 떠오르시나요?"
            if meaningful_answer
            else "그때 마음은 어떠셨나요?"
        )
    if goal == "deepen_reason":
        return (
            "그 일이 왜 오래 기억에 남았는지도 들려주실 수 있을까요?"
            if meaningful_answer
            else "왜 그 기억이 오래 남으셨나요?"
        )
    if goal == "deepen_person":
        return (
            "그때 함께 있었던 사람이나 먼저 떠오르는 분이 있으실까요?"
            if meaningful_answer
            else "그때 함께 있었던 분이 있으셨나요?"
        )

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

        감정:
        emotional_tone={assessment.emotional_tone}
        emotional_blend={assessment.emotional_blend}

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
        max_output_tokens=80,
        text_format=InterviewerAcknowledgementResponse,
        extra_body=build_interview_prompt_cache_body("acknowledgement", question),
    )
    log_interview_prompt_cache_usage(logger, "acknowledgement", question, response)
    parsed = response.output_parsed
    if parsed is None:
        return None
    message = parsed.acknowledgement.strip()
    return message or None


def build_interviewer_acknowledgement_fallback(
    decision: VoiceInterviewDecision,
    assessment: VoiceInterviewAssessment,
) -> str:
    # Short acknowledgement fallback used inside follow-up turns.
    # Follow-up responses append a separate question after this text, so this
    # should stay compact and avoid becoming a full closing response.
    if assessment.emotional_tone == "positive":
        return "그때의 기쁨이 또렷하게 전해졌어요."
    if assessment.emotional_tone == "warm":
        return "그 장면이 따뜻하게 남아 있으시군요."
    if assessment.emotional_tone == "negative":
        if assessment.emotional_blend == "support":
            return "힘든 시간 속에서도 곁의 도움이 크게 남으셨군요."
        if assessment.emotional_blend == "gratitude":
            return "힘든 기억 안에 고마움도 함께 남아 있으시군요."
        return "그 기억이 오래 마음에 남아 있으시겠어요."
    if assessment.emotional_tone == "fearful":
        if assessment.emotional_blend == "warm_relief":
            return "무서운 와중에도 함께였다는 점이 크게 남으셨군요."
        if assessment.emotional_blend == "support":
            return "놀란 마음 속에서도 곁의 도움이 남아 있으시군요."
        return "그날 일이 꽤 크게 남아 있으신 것 같아요."

    if decision.decision == "follow_up":
        if assessment.development_present or assessment.result_present:
            return "지금 떠오른 장면이 또렷하시군요."
        return "지금 말씀해주신 기억이 또렷하게 남아 있으시군요."
    if decision.decision == "pass":
        return "말씀해주신 기억이 또렷하게 전해졌어요."
    if decision.decision == "repeat":
        return ""
    return "잘 들었습니다."


def _clean_answer_summary_for_fallback(summary: str) -> str:
    text = " ".join(summary.strip().split()).strip("\"'“”‘’ .")
    for prefix in ("사용자는 ", "사용자님은 ", "선생님은 "):
        if text.startswith(prefix):
            text = text[len(prefix) :].strip()
            break
    if len(text) > 58:
        text = text[:58].rstrip() + "..."
    return text


def build_interviewer_close_fallback(
    decision: VoiceInterviewDecision,
    assessment: VoiceInterviewAssessment,
) -> str:
    # Closing fallback used when the LLM output is missing or rejected.
    # Keep it grounded in the assessment summary so the user does not see a
    # generic one-line response like "그날 일이 크게 남아 있으신 것 같아요."
    # This is intentionally longer than acknowledgement fallback because pass
    # turns do not add a follow-up question afterward.
    summary = _clean_answer_summary_for_fallback(assessment.answer_summary)

    if assessment.emotional_tone == "positive":
        if summary:
            return f"{summary}. 그 순간의 기쁨이 오래 남아 있으신 것 같습니다."
        return "말씀해주신 기억이 또렷하게 전해졌어요. 그 순간의 기쁨이 오래 남아 있으신 것 같습니다."
    if assessment.emotional_tone == "warm":
        if summary:
            return f"{summary}. 그 기억 안의 온기가 조용히 전해졌어요."
        return "말씀해주신 기억이 마음에 남습니다. 그 기억 안의 온기가 조용히 전해졌어요."
    if assessment.emotional_tone == "negative":
        if assessment.emotional_blend == "support":
            if summary:
                return f"{summary}. 힘든 시간 속에서도 곁의 도움이 큰 버팀목이 되었겠습니다."
            return "말씀해주신 기억이 무겁게 전해졌어요. 곁의 도움이 큰 버팀목이 되었겠습니다."
        if assessment.emotional_blend == "gratitude":
            if summary:
                return f"{summary}. 힘든 마음 속에서도 고마움이 함께 남아 있으신 것 같습니다."
            return "말씀해주신 기억이 무겁게 전해졌어요. 그 안의 고마움도 함께 남아 있으신 것 같습니다."
        if assessment.emotional_blend == "regret":
            if summary:
                return f"{summary}. 그 안에 남은 후회나 미안함도 쉽게 사라지지 않았겠습니다."
            return "말씀해주신 기억이 무겁게 전해졌어요. 남은 후회나 미안함도 쉽게 사라지지 않았겠습니다."
        if summary:
            return f"{summary}. 쉽게 지나갈 수 없는 시간이었겠습니다."
        return "말씀해주신 기억이 무겁게 전해졌어요. 쉽게 지나갈 수 없는 시간이었겠습니다."
    if assessment.emotional_tone == "fearful":
        if assessment.emotional_blend == "warm_relief":
            if summary:
                return f"{summary}. 무서운 와중에도 함께 있었다는 점이 안도감으로 남으셨겠습니다."
            return "말씀해주신 기억이 크게 다가옵니다. 무서운 와중에도 함께 있었다는 점이 안도감으로 남으셨겠습니다."
        if assessment.emotional_blend == "support":
            if summary:
                return f"{summary}. 놀란 마음 속에서도 곁의 도움이 버팀이 되었겠습니다."
            return "말씀해주신 기억이 크게 다가옵니다. 놀란 마음 속에서도 곁의 도움이 버팀이 되었겠습니다."
        if summary:
            return f"{summary}. 그때 많이 놀라고 무서우셨겠습니다."
        return "말씀해주신 기억이 크게 다가옵니다. 그때 많이 놀라고 무서우셨겠습니다."

    if decision.decision == "pass":
        if summary:
            return f"{summary}. 말씀해주신 흐름이 잘 이어졌습니다."
        return "말씀해주신 기억이 또렷하게 전해졌어요. 말씀해주신 흐름이 잘 이어졌습니다."
    return build_interviewer_acknowledgement_fallback(decision, assessment).strip() or "잘 들었습니다."


def _default_ack_tone_for_emotional_tone(emotional_tone: str) -> AckTone:
    if emotional_tone == "positive":
        return "celebrate"
    if emotional_tone == "warm":
        return "warm"
    if emotional_tone == "negative":
        return "comfort"
    if emotional_tone == "fearful":
        return "fear_ack"
    return "neutral"


def _normalize_ack_tone(raw_tone: str | None) -> AckTone | None:
    tone = (raw_tone or "").strip().lower()
    if tone in {"comfort", "fear_ack", "warm", "celebrate", "neutral"}:
        return tone  # type: ignore[return-value]
    return None


def _normalize_emotion_alignment(raw_alignment: str | None) -> EmotionAlignment:
    alignment = (raw_alignment or "").strip().lower()
    if alignment in {"aligned", "over_positive", "ungrounded"}:
        return alignment  # type: ignore[return-value]
    return "ungrounded"


def _ack_tone_matches_emotional_tone(
    ack_tone: AckTone | None,
    emotional_tone: str,
) -> bool:
    expected = _default_ack_tone_for_emotional_tone(emotional_tone)
    return ack_tone == expected


def _contains_question_form(text: str) -> bool:
    lowered = text.lower()
    if "?" in lowered:
        return True
    return any(
        token in lowered
        for token in (
            "나요",
            "까요",
            "을까요",
            "를까요",
            "인가요",
            "있나요",
            "있으신가요",
            "있으실까요",
            "떠오르시나요",
            "어떤가요",
            "어땠나요",
            "무엇인가요",
            "누구인가요",
            "누구신가요",
        )
    )


def _interviewer_text_content_length(text: str) -> int:
    return len("".join(text.split()))


def _is_interviewer_text_too_short(decision: VoiceInterviewDecision, text: str) -> bool:
    if decision.decision not in {"follow_up", "pass"}:
        return False
    if decision.decision == "pass" and decision.follow_up_goal in {"close", None}:
        return _interviewer_text_content_length(text) < 34
    return _interviewer_text_content_length(text) < 38


def _build_interviewer_turn_input(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    prompt_state: VoiceInterviewPromptState,
    user_text: str,
) -> str:
    previous_answers = [
        answer for answer in get_question_answers(state, question.question_no) if answer.strip()
    ]
    previous_answers_text = " | ".join(previous_answers) if previous_answers else "없음"
    return dedent(
        f"""
        현재 질문:
        {question.main_question}

        질문 힌트:
        {question.hint}

        현재 질문 흐름:
        {question.follow_up_flow}

        저장된 이전 답변:
        {previous_answers_text}

        마지막 follow-up 질문:
        {state.last_follow_up_question or "없음"}

        판단 기준 누적 답변:
        {user_text}

        답변 요약:
        {assessment.answer_summary or user_text}

        decision:
        {decision.decision}

        follow_up_goal:
        {decision.follow_up_goal or "없음"}

        selected_missing_slot:
        {decision.selected_missing_slot or "없음"}

        emotional_tone:
        {assessment.emotional_tone}

        emotional_blend:
        {assessment.emotional_blend}

        이미 확인된 정보:
        {", ".join(assessment.filled_slots) if assessment.filled_slots else "없음"}

        아직 더 필요한 정보:
        {", ".join(assessment.missing_slots) if assessment.missing_slots else "없음"}

        점수:
        relevance={assessment.relevance_score}
        detail={assessment.detail_score}
        reflection={assessment.reflection_score}

        구조 단서:
        flow_type={assessment.flow_type}
        setup_present={str(assessment.setup_present).lower()}
        development_present={str(assessment.development_present).lower()}
        result_present={str(assessment.result_present).lower()}
        emotion_present={str(assessment.emotion_present).lower()}
        meaning_present={str(assessment.meaning_present).lower()}
        person_present={str(assessment.person_present).lower()}

        상태:
        off_topic={str(assessment.off_topic).lower()}
        transcript_unclear={str(assessment.transcript_unclear).lower()}
        question_echo={str(assessment.question_echo).lower()}
        current_question_story_ready={str(prompt_state.current_question_story_ready).lower()}
        current_question_answer_count={prompt_state.current_question_answer_count}
        """
    ).strip()


def _axis_matches_follow_up_goal(
    question_axis: QuestionAxis | None,
    follow_up_goal: FollowUpGoal | None,
) -> bool:
    if not follow_up_goal:
        return True
    if follow_up_goal == "deepen_scene":
        return question_axis == "scene"
    if follow_up_goal == "deepen_result":
        return question_axis == "result"
    if follow_up_goal == "deepen_emotion":
        return question_axis == "emotion"
    if follow_up_goal == "deepen_reason":
        return question_axis == "reason"
    if follow_up_goal == "deepen_person":
        return question_axis == "person"
    if follow_up_goal == "deepen_event":
        return question_axis == "event"
    return True


def _question_matches_follow_up_goal_text(
    question_text: str,
    follow_up_goal: FollowUpGoal | None,
) -> bool:
    lowered = question_text.lower()
    if not follow_up_goal:
        return True
    if follow_up_goal == "deepen_scene":
        return any(
            token in lowered
            for token in ("장면", "모습", "눈", "보", "어디", "주변", "무엇을 하고")
        )
    if follow_up_goal == "deepen_result":
        return any(
            token in lowered
            for token in ("그 뒤", "그다음", "결국", "그 후", "어떻게 되었", "마지막에")
        )
    if follow_up_goal == "deepen_emotion":
        return any(token in lowered for token in ("마음", "기분", "감정", "무서", "어떠", "느낌"))
    if follow_up_goal == "deepen_reason":
        return any(token in lowered for token in ("왜", "이유", "의미", "남", "깨달", "어떤 생각"))
    if follow_up_goal == "deepen_person":
        return any(token in lowered for token in ("누구", "사람", "함께", "어른", "가족"))
    if follow_up_goal == "deepen_event":
        return any(token in lowered for token in ("어떤 일", "무슨 일", "어떻게"))
    return True


def _matches_flow_specific_follow_up_text(
    question: InterviewQuestion,
    question_text: str,
    goal: FollowUpGoal | None,
) -> bool:
    lowered = question_text.lower()

    if question.follow_up_flow == "peer_life_memory" and goal == "deepen_event":
        return any(
            token in lowered
            for token in ("하루", "지내", "보내", "생활", "집안일", "어떤 일", "무슨 일", "어떻게")
        )

    if question.follow_up_flow == "person_memory" and goal == "deepen_event":
        has_shared_memory = any(
            token in lowered
            for token in ("함께", "같이", "어떤 일", "무슨 일", "기억나는 일", "장면", "모습")
        )
        is_place_or_time_only = any(token in lowered for token in ("어디", "언제", "때")) and not has_shared_memory
        return has_shared_memory and not is_place_or_time_only

    if question.follow_up_flow == "legacy_message" and goal == "deepen_person":
        return any(
            token in lowered
            for token in ("누구", "전하고", "전해", "말하고 싶은", "가장 먼저", "대상")
        )

    if question.follow_up_flow == "legacy_message" and goal == "deepen_event":
        return any(
            token in lowered
            for token in ("어떤 일", "무슨 일", "계기", "겪", "생각을 하게", "경험")
        ) and not any(token in lowered for token in ("어디", "장소"))

    return True


def _goal_allowed_by_question_policy(
    question: InterviewQuestion,
    goal: FollowUpGoal | None,
) -> bool:
    if goal not in {
        "deepen_event",
        "deepen_scene",
        "deepen_result",
        "deepen_emotion",
        "deepen_reason",
        "deepen_person",
    }:
        return True
    return goal in get_question_goal_priority(question)


def _normalize_question_axis(raw_axis: str | None, decision: VoiceInterviewDecision) -> QuestionAxis | None:
    axis = (raw_axis or "").strip().lower()
    allows_question = decision.decision == "follow_up" or (
        decision.decision == "pass" and decision.follow_up_goal != "close"
    )
    if not allows_question:
        return None
    if axis in {"event", "scene", "result", "emotion", "reason", "person"}:
        return axis  # type: ignore[return-value]
    return None


def _normalize_question_focus(raw_focus: str | None, decision: VoiceInterviewDecision) -> QuestionFocus | None:
    focus = (raw_focus or "").strip().lower()
    allows_question = decision.decision == "follow_up" or (
        decision.decision == "pass" and decision.follow_up_goal != "close"
    )
    if not allows_question:
        return None
    if focus in {"setup", "development", "result", "emotion", "meaning", "person"}:
        return focus  # type: ignore[return-value]
    return None


def _goal_to_axis(goal: FollowUpGoal | None) -> QuestionAxis | None:
    if goal == "deepen_event":
        return "event"
    if goal == "deepen_scene":
        return "scene"
    if goal == "deepen_result":
        return "result"
    if goal == "deepen_emotion":
        return "emotion"
    if goal == "deepen_reason":
        return "reason"
    if goal == "deepen_person":
        return "person"
    return None


def _default_focus_for_goal(goal: FollowUpGoal | None) -> QuestionFocus | None:
    if goal == "deepen_event":
        return "setup"
    if goal == "deepen_scene":
        return "development"
    if goal == "deepen_result":
        return "result"
    if goal == "deepen_emotion":
        return "emotion"
    if goal == "deepen_reason":
        return "meaning"
    if goal == "deepen_person":
        return "person"
    return None


def _is_valid_interviewer_turn(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    result: InterviewerTurnResponse,
) -> bool:
    assistant_text = result.assistant_text.strip()
    if not assistant_text:
        return False

    if not result.ack_tone:
        return False
    if not _ack_tone_matches_emotional_tone(result.ack_tone, assessment.emotional_tone):
        return False

    if result.emotion_alignment != "aligned":
        return False

    if _is_interviewer_text_too_short(decision, assistant_text):
        return False

    if decision.decision == "pass":
        goal = decision.follow_up_goal
        if not _goal_allowed_by_question_policy(question, goal):
            return False
        if goal == "close" or not goal:
            if result.next_question:
                return False
            if _contains_question_form(assistant_text):
                return False
            return True

        question_text = (result.next_question or "").strip()
        if not question_text or question_text not in assistant_text:
            return False
        return (
            _axis_matches_follow_up_goal(result.question_axis, goal)
            and _question_matches_follow_up_goal_text(question_text, goal)
            and _matches_flow_specific_follow_up_text(question, question_text, goal)
        )

    if decision.decision != "follow_up":
        if result.next_question:
            return False
        return True

    question_text = (result.next_question or "").strip()
    if not question_text or question_text not in assistant_text:
        return False

    goal = decision.follow_up_goal
    if not _goal_allowed_by_question_policy(question, goal):
        return False

    if goal == "deepen_scene" and assessment.development_present:
        return False
    if goal == "deepen_result" and assessment.result_present:
        return False
    if goal == "deepen_emotion" and assessment.emotion_present:
        return False
    if goal == "deepen_reason" and assessment.meaning_present:
        return False
    if goal == "deepen_person" and assessment.person_present:
        return False

    if question.follow_up_flow in {"event_sequence", "person_memory", "legacy_message"}:
        return _axis_matches_follow_up_goal(result.question_axis, goal) and _matches_flow_specific_follow_up_text(
            question,
            question_text,
            goal,
        )

    return _question_matches_follow_up_goal_text(question_text, goal) and _matches_flow_specific_follow_up_text(
        question,
        question_text,
        goal,
    )


def request_interviewer_turn(
    question: InterviewQuestion,
    state: VoiceInterviewState,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    prompt_state: VoiceInterviewPromptState,
    user_text: str,
) -> InterviewerTurnResponse | None:
    response = get_interview_openai_client().responses.parse(
        model="gpt-4.1-mini",
        instructions=build_interviewer_turn_system_prompt(question),
        input=_build_interviewer_turn_input(
            question,
            state,
            assessment,
            decision,
            prompt_state,
            user_text,
        ),
        temperature=0.6,
        max_output_tokens=220,
        text_format=InterviewerTurnResponse,
        extra_body=build_interview_prompt_cache_body("interviewer_turn", question),
    )
    log_interview_prompt_cache_usage(logger, "interviewer_turn", question, response)
    parsed = response.output_parsed
    if parsed is None:
        return None
    assistant_text = parsed.assistant_text.strip()
    next_question = (parsed.next_question or "").strip() or None
    ack_tone = _normalize_ack_tone(parsed.ack_tone)
    emotion_alignment = _normalize_emotion_alignment(parsed.emotion_alignment)
    question_axis = _normalize_question_axis(parsed.question_axis, decision)
    question_focus = _normalize_question_focus(parsed.question_focus, decision)
    if not assistant_text:
        return None
    if decision.decision != "follow_up" and not (
        decision.decision == "pass" and decision.follow_up_goal == "deepen_reason"
    ):
        next_question = None
    result = InterviewerTurnResponse(
        assistant_text=assistant_text,
        next_question=next_question,
        ack_tone=ack_tone,
        emotion_alignment=emotion_alignment,
        question_axis=question_axis,
        question_focus=question_focus,
    )
    if not _is_valid_interviewer_turn(question, assessment, decision, result):
        return None
    return result


def build_interviewer_turn_fallback(
    question: InterviewQuestion,
    assessment: VoiceInterviewAssessment,
    decision: VoiceInterviewDecision,
    prompt_state: VoiceInterviewPromptState,
    user_text: str,
) -> InterviewerTurnResponse:
    # FALLBACK: LLM이 최종 인터뷰어 응답을 만들지 못했을 때만 쓰는 안전망입니다.
    if decision.decision == "follow_up":
        question_text = (
            decision.follow_up_question
            or build_follow_up_fallback(question, assessment, decision, user_text)
        ).strip()
        acknowledgement = build_interviewer_acknowledgement_fallback(decision, assessment).strip()
        assistant_text = f"{acknowledgement} {question_text}".strip()
        return InterviewerTurnResponse(
            assistant_text=assistant_text,
            next_question=question_text,
            ack_tone=_default_ack_tone_for_emotional_tone(assessment.emotional_tone),
            emotion_alignment="aligned",
            question_axis=_normalize_question_axis(_goal_to_axis(decision.follow_up_goal), decision),
            question_focus=_default_focus_for_goal(decision.follow_up_goal),
        )

    if decision.decision == "pass":
        if decision.follow_up_goal == "deepen_reason":
            question_text = (
                get_question_fallback_question(question, decision.follow_up_goal)
                or "지금 돌아보면 그 시간이 선생님 삶에 어떤 의미로 남아 있으신가요?"
            )
            acknowledgement = build_interviewer_acknowledgement_fallback(decision, assessment).strip()
            assistant_text = f"{acknowledgement} {question_text}".strip()
            return InterviewerTurnResponse(
                assistant_text=assistant_text,
                next_question=question_text,
                ack_tone=_default_ack_tone_for_emotional_tone(assessment.emotional_tone),
                emotion_alignment="aligned",
                question_axis=_normalize_question_axis(_goal_to_axis(decision.follow_up_goal), decision),
                question_focus=_default_focus_for_goal(decision.follow_up_goal),
            )
        return InterviewerTurnResponse(
            assistant_text=build_interviewer_close_fallback(decision, assessment).strip() or "잘 들었습니다.",
            next_question=None,
            ack_tone=_default_ack_tone_for_emotional_tone(assessment.emotional_tone),
            emotion_alignment="aligned",
            question_axis=None,
            question_focus=None,
        )

    if decision.decision == "repeat":
        if decision.reason_code == "question_echo":
            assistant_text = (
                f"질문이 다시 들린 것 같아요. 답변만 천천히 말씀해주세요. {question.hint}"
            ).strip()
        elif decision.reason_code == "empty_answer":
            assistant_text = "답변이 들리지 않았어요. 기억나는 내용부터 천천히 말씀해주세요."
        else:
            assistant_text = "말씀을 정확히 알아듣지 못했어요. 같은 내용을 조금만 천천히 다시 말씀해주세요."
        return InterviewerTurnResponse(
            assistant_text=assistant_text,
            next_question=None,
            ack_tone="neutral",
            emotion_alignment="aligned",
            question_axis=None,
            question_focus=None,
        )

    if prompt_state.is_interview_complete:
        assistant_text = "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
    elif decision.decision == "move_on":
        assistant_text = "기억나는 만큼으로도 충분해요. 다음 이야기로 넘어가볼게요."
    else:
        assistant_text = "잘 들었습니다."
    return InterviewerTurnResponse(
        assistant_text=assistant_text,
        next_question=None,
        ack_tone="neutral",
        emotion_alignment="aligned",
        question_axis=None,
        question_focus=None,
    )
