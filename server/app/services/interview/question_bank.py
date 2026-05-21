from app.services.interview.types import InterviewQuestion

QUESTION_BANK_VERSION = 3
MAX_FOLLOW_UPS = 2
MAX_EXTRA_FOLLOW_UPS_FOR_NEAR_PASS = 1


VOICE_INTERVIEW_QUESTIONS: list[InterviewQuestion] = [
    InterviewQuestion(
        question_no=1,
        main_question="어릴 적 살던 곳과 집안 분위기는 어떠했나요?",
        hint="집, 가족, 동네 모습 중 떠오르는 것부터 말씀해주세요.",
        chapter_type="childhood",
        target_slots=["place", "person", "scene", "emotion"],
        required_slots=["place", "person"],
        alt_pass_routes=[
            ["place", "person"],
            ["place", "emotion"],
            ["scene", "emotion"],
        ],
        story_generatable_routes=[
            ["place", "person", "scene"],
            ["person", "scene"],
            ["place", "person", "emotion"],
        ],
        story_generatable_min_length=32,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        follow_up_flow="background_memory",
    ),
    InterviewQuestion(
        question_no=2,
        main_question="어릴 때 가장 선명하게 남아 있는 기억은 무엇인가요?",
        hint="기뻤던 일이나 놀랐던 일, 오래 남은 장면을 말씀해주세요.",
        chapter_type="childhood",
        target_slots=["event", "scene", "emotion", "time"],
        required_slots=["event"],
        alt_pass_routes=[
            ["event", "emotion"],
            ["event", "scene"],
            ["scene", "emotion"],
        ],
        story_generatable_routes=[
            ["event", "scene", "emotion"],
            ["event", "time", "scene", "emotion"],
        ],
        story_generatable_min_length=34,
        story_min_detail_score=2,
        story_min_distinct_slots=2,
        story_min_reflection_score=1,
        follow_up_flow="event_sequence",
    ),
    InterviewQuestion(
        question_no=3,
        main_question="학교를 다녔다면 어떤 기억이 남아 있나요? 다니지 않았다면 그 또래 시절은 어떠했나요?",
        hint="학교, 친구, 선생님, 집안일, 하루하루의 생활을 떠올려보셔도 좋아요.",
        chapter_type="youth",
        target_slots=["event", "person", "scene", "value"],
        required_slots=["event", "person"],
        alt_pass_routes=[
            ["event", "person"],
            ["event", "scene"],
            ["person", "emotion"],
        ],
        story_generatable_routes=[
            ["event", "person", "scene"],
            ["event", "person", "value"],
            ["person", "scene", "value"],
        ],
        story_generatable_min_length=36,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        follow_up_flow="peer_life_memory",
    ),
    InterviewQuestion(
        question_no=4,
        main_question="젊었을 때는 주로 어떤 생활을 하며 지내셨나요?",
        hint="일상, 일, 사람들과의 관계, 그때 마음을 말씀해주셔도 좋아요.",
        chapter_type="youth",
        target_slots=["event", "value", "emotion", "scene"],
        required_slots=["event", "value"],
        alt_pass_routes=[
            ["event", "emotion"],
            ["value", "emotion"],
            ["event", "scene"],
        ],
        story_generatable_routes=[
            ["event", "scene", "emotion"],
            ["event", "value", "emotion"],
            ["event", "scene", "value"],
        ],
        story_generatable_min_length=40,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        story_min_reflection_score=1,
        follow_up_flow="event_sequence",
    ),
    InterviewQuestion(
        question_no=5,
        main_question="살면서 기억에 남는 사람이 있나요?",
        hint="가족, 친구, 이웃, 선생님 누구든 괜찮아요.",
        chapter_type="love",
        target_slots=["person", "event", "emotion", "value"],
        required_slots=["person"],
        alt_pass_routes=[
            ["person", "emotion"],
            ["person", "value"],
            ["person", "event"],
        ],
        story_generatable_routes=[
            ["person", "event"],
            ["person", "emotion", "event"],
            ["person", "value", "event"],
        ],
        story_generatable_min_length=28,
        story_min_detail_score=1,
        story_min_distinct_slots=2,
        follow_up_flow="person_memory",
    ),
    InterviewQuestion(
        question_no=6,
        main_question="살면서 어떤 일을 하며 지내셨나요? 가장 오래 했거나 기억에 남는 일을 들려주세요.",
        hint="처음 시작한 일, 오래 한 일, 가장 기억나는 일을 말씀해주세요.",
        chapter_type="career",
        target_slots=["event", "time", "place", "emotion"],
        required_slots=["event", "place"],
        alt_pass_routes=[
            ["event", "place"],
            ["event", "time"],
            ["event", "emotion"],
        ],
        story_generatable_routes=[
            ["event", "place", "emotion"],
            ["event", "place", "scene", "emotion"],
        ],
        story_generatable_min_length=36,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        story_min_reflection_score=1,
        follow_up_flow="event_sequence",
        near_pass_extra_follow_ups=1,
    ),
    InterviewQuestion(
        question_no=7,
        main_question="살면서 가장 힘들었던 시기와, 그 시간을 어떻게 견디셨는지 들려주세요.",
        hint="무슨 일이 있었는지, 누가 힘이 되었는지, 어떤 마음으로 버티셨는지 말씀해주세요.",
        chapter_type="reflection",
        target_slots=["event", "person", "emotion", "value"],
        required_slots=["event", "value"],
        alt_pass_routes=[
            ["event", "emotion"],
            ["event", "value"],
            ["person", "emotion"],
        ],
        story_generatable_routes=[
            ["event", "emotion", "value"],
            ["event", "person", "emotion"],
            ["event", "emotion", "scene"],
        ],
        story_generatable_min_length=40,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        story_min_reflection_score=1,
        follow_up_flow="event_sequence",
        near_pass_extra_follow_ups=1,
    ),
    InterviewQuestion(
        question_no=8,
        main_question="살면서 가장 기뻤거나 가장 뿌듯했던 순간이 있나요?",
        hint="작은 일이어도 괜찮아요. 마음이 환해졌던 순간을 말씀해주세요.",
        chapter_type="reflection",
        target_slots=["event", "emotion", "person", "scene"],
        required_slots=["event", "emotion"],
        alt_pass_routes=[
            ["event", "emotion"],
            ["event", "person"],
            ["scene", "emotion"],
        ],
        story_generatable_routes=[
            ["event", "emotion", "person"],
            ["event", "emotion", "scene"],
        ],
        story_generatable_min_length=32,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        story_min_reflection_score=1,
        follow_up_flow="event_sequence",
    ),
    InterviewQuestion(
        question_no=9,
        main_question="살면서 많이 달라졌다고 느낀 때가 있나요?",
        hint="어떤 일을 겪고 나서 생각이나 삶이 바뀌었는지 말씀해주세요.",
        chapter_type="reflection",
        target_slots=["event", "time", "value", "emotion"],
        required_slots=["event", "value"],
        alt_pass_routes=[
            ["event", "value"],
            ["event", "emotion"],
            ["time", "value"],
        ],
        story_generatable_routes=[
            ["event", "value", "emotion"],
            ["event", "time", "value"],
        ],
        story_generatable_min_length=36,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        story_min_reflection_score=1,
        follow_up_flow="event_sequence",
    ),
    InterviewQuestion(
        question_no=10,
        main_question="지금 돌아보면 꼭 남기고 싶은 이야기나 말이 있나요?",
        hint="가족에게 하고 싶은 말이나, 내 삶에서 가장 소중했던 것을 말씀해주셔도 좋아요.",
        chapter_type="reflection",
        target_slots=["value", "person", "emotion", "event"],
        required_slots=["value", "person"],
        alt_pass_routes=[
            ["value", "person"],
            ["value", "emotion"],
            ["person", "emotion"],
        ],
        story_generatable_routes=[
            ["value", "person", "emotion"],
            ["value", "event", "emotion"],
            ["value", "person", "event"],
        ],
        story_generatable_min_length=40,
        story_min_detail_score=2,
        story_min_distinct_slots=3,
        story_min_reflection_score=1,
        follow_up_flow="legacy_message",
        near_pass_extra_follow_ups=1,
    ),
]


def get_total_question_count() -> int:
    return len(VOICE_INTERVIEW_QUESTIONS)


def get_interview_question(question_no: int) -> InterviewQuestion:
    total = get_total_question_count()
    safe_question_no = min(max(question_no, 1), total)
    return VOICE_INTERVIEW_QUESTIONS[safe_question_no - 1]
