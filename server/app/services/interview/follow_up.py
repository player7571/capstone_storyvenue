from textwrap import dedent

from app.services.interview.decision import looks_like_meaningful_answer, pick_missing_slot
from app.services.interview.llm import get_interview_openai_client
from app.services.interview.state import get_question_answers
from app.services.interview.types import (
    AckTone,
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

INTERVIEWER_TURN_SYSTEM_PROMPT = dedent(
    """
    당신은 노인 사용자의 자서전 인터뷰를 돕는 따뜻한 한국어 AI 인터뷰어입니다.
    응답은 JSON으로 작성하세요.

    - assistant_text는 쉬운 한국어 1~2문장으로 작성하세요.
    - ack_tone을 반드시 아래 중 하나로 고르세요:
      - comfort: 힘듦, 상실, 고단함을 조심스럽게 수긍하는 톤
      - fear_ack: 무서움, 놀람, 크게 남은 충격을 조심스럽게 수긍하는 톤
      - warm: 따뜻함, 정겨움, 사람의 온기를 받아주는 톤
      - celebrate: 기쁨, 뿌듯함, 환한 순간을 함께 받아주는 톤
      - neutral: 과한 해석 없이 담담하게 받아주는 톤
    - 첫 문장은 반드시 사용자의 최근 답변에 짧게 수긍하는 문장으로 시작하세요.
      - emotional_tone이 positive면 ack_tone은 celebrate를 고르세요.
      - emotional_tone이 warm이면 ack_tone은 warm을 고르세요.
      - emotional_tone이 negative면 ack_tone은 comfort를 고르세요.
      - emotional_tone이 fearful이면 ack_tone은 fear_ack를 고르세요.
      - emotional_tone이 neutral이면 ack_tone은 neutral을 고르세요.
      - emotional_tone이 negative/fearful일 때는 따뜻하다, 기쁘다, 뿌듯하다, 환하다 같은 긍정적 표현을 쓰지 마세요.
      - emotional_tone이 negative일 때는 힘들었겠다, 안타깝다, 마음에 오래 남았겠다 같은 조용한 수긍을 우선하세요.
      - emotional_tone이 fearful일 때는 무서웠겠다, 놀랐겠다, 크게 남았겠다 같은 조심스러운 수긍을 우선하세요.
    - 사용자가 방금 말한 표현을 가능하면 일부 이어받으세요.
    - 사용자가 말하지 않은 사실은 보태지 마세요.
    - 아픈 기억은 과하게 미화하거나 예쁘게 꾸미지 마세요.
    - 운영 안내(다음 질문, 이야기 생성, 버튼, 정리)는 절대 쓰지 마세요.
    - 질문은 최대 1개만 포함하세요.
    - follow_up일 때만 구체적인 질문을 포함하세요.
    - pass일 때는 기본적으로 질문 없이 짧게 받아주기만 하세요.
    - 다만 pass이면서 follow_up_goal이 deepen_reason이면, 이미 충분히 답한 이야기의 의미나 남은 마음을 묻는 짧은 질문 1개는 허용됩니다.
    - repeat일 때는 답변을 다시 부탁하되 새로운 주제를 꺼내지 마세요.
    - move_on일 때는 기억나는 만큼으로도 괜찮다는 뜻만 짧게 전하세요.
    - 이미 나온 축은 되풀이해서 묻지 마세요.
    - event_sequence 흐름에서는 setup -> development -> result -> emotion -> meaning 순서를 따르세요.
    - background_memory 흐름에서는 사건보다 집, 동네, 가족, 집안 분위기와 먼저 떠오르는 모습에 집중하세요.
    - peer_life_memory 흐름에서는 학교 사건 하나보다 그 시절 하루 생활, 또래 관계, 집안일 같은 생활감을 먼저 여세요.
    - person_memory 흐름에서는 사람을 먼저 또렷하게 하고, 그 사람과 함께한 장면과 마음으로 이어가세요.
    - legacy_message 흐름에서는 남기고 싶은 말, 그 말을 전하고 싶은 대상, 왜 그런 말을 남기고 싶은지 순서로 좁혀가세요.
    - follow_up_goal이 deepen_scene이면 사건의 전개나 장면을 먼저 물으세요.
    - follow_up_goal이 deepen_result이면 그 뒤에 어떻게 되었는지 물으세요.
    - follow_up_goal이 deepen_emotion이면 그때 어떤 마음이 들었는지 물으세요.
    - follow_up_goal이 deepen_reason이면 왜 오래 남았는지, 어떤 의미였는지 물으세요.
    - follow_up_goal이 deepen_person이면 함께 있었던 사람이나 먼저 떠오르는 사람을 물으세요.
    - follow_up_goal이 deepen_event이면 어떤 일이 있었는지 더 구체적으로 물으세요.
    - background_memory에서 deepen_scene이면 집이나 동네 모습, 집안 분위기, 먼저 떠오르는 장면을 물으세요.
    - peer_life_memory에서 deepen_event이면 학교를 다녔는지보다 그 시절 하루가 어떻게 흘러갔는지, 어떤 생활을 했는지 물으세요.
    - peer_life_memory에서 deepen_event이면 친구나 선생님보다 하루 생활, 집안일, 학교 오가던 일상 쪽을 먼저 물으세요.
    - person_memory에서 deepen_person이면 그 사람이 어떤 분이었는지 물으세요.
    - person_memory에서 deepen_event이면 장소나 때만 묻지 말고 그 사람과 함께했던 일이나 장면을 먼저 물으세요.
    - legacy_message에서 deepen_reason이면 무엇을 남기고 싶은지 또는 왜 그런 말을 남기고 싶은지 물으세요.
    - legacy_message에서 deepen_person이면 그 말을 누구에게 전하고 싶은지 물으세요.
    - legacy_message에서 deepen_person이면 가족처럼 넓은 말이 이미 나왔더라도, 가장 먼저 전하고 싶은 구체적인 대상을 물으세요.
    - legacy_message에서 deepen_event이면 어디였는지보다 그런 생각을 하게 만든 경험이나 일을 물으세요.

    next_question 규칙:
    - follow_up일 때, 또는 pass이면서 follow_up_goal이 deepen_reason일 때만 assistant_text 안에 실제로 들어간 질문 문장을 그대로 넣으세요.
    - close 성격의 pass / repeat / move_on일 때는 null로 두세요.
    - follow_up일 때, 또는 pass이면서 follow_up_goal이 deepen_reason일 때는 question_axis를 반드시 아래 중 하나로 고르세요:
      - event: 어떤 일이 있었는지 더 구체적으로 묻는 질문
      - scene: 장면, 전개, 주변 모습, 그때 보인 것/벌어진 일을 묻는 질문
      - result: 그 뒤 어떻게 되었는지, 어떤 결과가 있었는지 묻는 질문
      - emotion: 그때 어떤 감정이나 마음이 들었는지 묻는 질문
      - reason: 왜 기억에 남았는지, 어떤 의미였는지 묻는 질문
      - person: 함께 있던 사람이나 먼저 떠오르는 사람을 묻는 질문
    - close 성격의 pass / repeat / move_on이면 question_axis는 null로 두세요.
    - question_focus도 함께 넣으세요.
      - deepen_scene이면 development
      - deepen_result이면 result
      - deepen_emotion이면 emotion
      - deepen_reason이면 meaning
      - deepen_person이면 person
      - deepen_event이면 setup
      - close 성격의 pass / repeat / move_on이면 null로 두세요.
    """
).strip()

_POSITIVE_FRAMING_TOKENS = (
    "따뜻",
    "기쁘",
    "뿌듯",
    "환해",
    "포근",
    "반갑",
    "좋았",
    "좋으셨",
)

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

    goal = decision.follow_up_goal
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
    if assessment.emotional_tone == "positive":
        return "그때의 기쁨이 또렷하게 전해졌어요."
    if assessment.emotional_tone == "warm":
        return "그 장면이 따뜻하게 남아 있으시군요."
    if assessment.emotional_tone == "negative":
        return "그 기억이 오래 마음에 남아 있으시겠어요."
    if assessment.emotional_tone == "fearful":
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


def _has_positive_framing(text: str) -> bool:
    lowered = text.lower()
    return any(token in lowered for token in _POSITIVE_FRAMING_TOKENS)


def _normalize_ack_tone(raw_tone: str | None) -> AckTone | None:
    tone = (raw_tone or "").strip().lower()
    if tone in {"comfort", "fear_ack", "warm", "celebrate", "neutral"}:
        return tone  # type: ignore[return-value]
    return None


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

        이전 누적 답변:
        {previous_answers_text}

        마지막 follow-up 질문:
        {state.last_follow_up_question or "없음"}

        방금 사용자 답변:
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

    if assessment.emotional_tone in {"negative", "fearful"}:
        if _has_positive_framing(assistant_text):
            return False

    if decision.decision == "pass":
        goal = decision.follow_up_goal
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
        instructions=INTERVIEWER_TURN_SYSTEM_PROMPT,
        input=_build_interviewer_turn_input(
            question,
            state,
            assessment,
            decision,
            prompt_state,
            user_text,
        ),
        temperature=0.6,
        text_format=InterviewerTurnResponse,
    )
    parsed = response.output_parsed
    if parsed is None:
        return None
    assistant_text = parsed.assistant_text.strip()
    next_question = (parsed.next_question or "").strip() or None
    ack_tone = _normalize_ack_tone(parsed.ack_tone)
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
            question_axis=_normalize_question_axis(_goal_to_axis(decision.follow_up_goal), decision),
            question_focus=_default_focus_for_goal(decision.follow_up_goal),
        )

    if decision.decision == "pass":
        if decision.follow_up_goal == "deepen_reason":
            question_text = "지금 돌아보면 그 시간이 선생님 삶에 어떤 의미로 남아 있으신가요?"
            acknowledgement = build_interviewer_acknowledgement_fallback(decision, assessment).strip()
            assistant_text = f"{acknowledgement} {question_text}".strip()
            return InterviewerTurnResponse(
                assistant_text=assistant_text,
                next_question=question_text,
                ack_tone=_default_ack_tone_for_emotional_tone(assessment.emotional_tone),
                question_axis=_normalize_question_axis(_goal_to_axis(decision.follow_up_goal), decision),
                question_focus=_default_focus_for_goal(decision.follow_up_goal),
            )
        return InterviewerTurnResponse(
            assistant_text=build_interviewer_acknowledgement_fallback(decision, assessment).strip() or "잘 들었습니다.",
            next_question=None,
            ack_tone=_default_ack_tone_for_emotional_tone(assessment.emotional_tone),
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
        question_axis=None,
        question_focus=None,
    )
