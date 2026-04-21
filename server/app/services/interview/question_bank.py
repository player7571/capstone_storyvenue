from app.services.interview.types import InterviewQuestion

QUESTION_BANK_VERSION = 1
MAX_FOLLOW_UPS = 2
MAX_EXTRA_FOLLOW_UPS_FOR_NEAR_PASS = 1


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


def get_total_question_count() -> int:
    return len(VOICE_INTERVIEW_QUESTIONS)


def get_interview_question(question_no: int) -> InterviewQuestion:
    total = get_total_question_count()
    safe_question_no = min(max(question_no, 1), total)
    return VOICE_INTERVIEW_QUESTIONS[safe_question_no - 1]
