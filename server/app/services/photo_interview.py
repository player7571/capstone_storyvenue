from base64 import b64encode
from functools import lru_cache
from textwrap import dedent

from openai import OpenAI

from app.core.config import get_settings

PHOTO_INTERVIEW_MODEL = "gpt-4o-mini"

PHOTO_OPENING_SYSTEM_PROMPT = dedent(
    """
    당신은 자서전 프로젝트의 따뜻한 인터뷰어입니다.
    사진을 보고 한국어로 자연스럽게 장면을 설명한 뒤, 사용자의 기억을 끌어내는 열린 질문을 하나만 하세요.
    과도한 추측은 피하고, 사진에서 드러나는 요소와 사용자가 떠올릴 감정에 집중하세요.
    응답은 짧은 설명 1~2문장과 질문 1문장으로 구성하고, 목록이나 번호는 쓰지 마세요.
    """
).strip()

PHOTO_FOLLOW_UP_SYSTEM_PROMPT = dedent(
    """
    당신은 자서전 프로젝트의 따뜻한 인터뷰어입니다.
    대화 흐름을 바탕으로 공감 어린 반응 뒤에 열린 후속 질문 하나를 한국어로 작성하세요.
    이미 물은 질문을 반복하지 말고, 인물, 장소, 시기, 감정, 사건 중 아직 덜 드러난 축을 파고드세요.
    응답은 짧은 공감 1~2문장과 질문 1문장으로만 구성하고, 목록이나 번호는 쓰지 마세요.
    """
).strip()


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _build_data_url(image_bytes: bytes, mime_type: str) -> str:
    encoded = b64encode(image_bytes).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


def _format_conversation_history(conversation_history: list[dict[str, str]]) -> str:
    speaker_map = {
        "user": "사용자",
        "assistant": "인터뷰어",
    }
    lines: list[str] = []

    for message in conversation_history:
        role = str(message.get("role", "")).strip().lower()
        content = str(message.get("content", "")).strip()
        if not content:
            continue
        speaker = speaker_map.get(role, role or "알 수 없음")
        lines.append(f"{speaker}: {content}")

    if not lines:
        raise ValueError("유효한 대화 내용이 없습니다.")

    return "\n".join(lines)


def generate_photo_opening_message(image_bytes: bytes, mime_type: str) -> str:
    response = _get_openai_client().responses.create(
        model=PHOTO_INTERVIEW_MODEL,
        instructions=PHOTO_OPENING_SYSTEM_PROMPT,
        input=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_image",
                        "image_url": _build_data_url(image_bytes, mime_type),
                    },
                    {
                        "type": "input_text",
                        "text": "이 사진의 장면을 설명하고, 이 기억을 풀어낼 수 있는 질문을 하나 해주세요.",
                    },
                ],
            }
        ],
        temperature=0.75,
    )
    message = str(getattr(response, "output_text", "")).strip()
    if not message:
        raise RuntimeError("사진 분석 응답을 생성하지 못했습니다.")
    return message


def generate_photo_follow_up_message(
    conversation_history: list[dict[str, str]],
) -> str:
    transcript = _format_conversation_history(conversation_history)
    response = _get_openai_client().responses.create(
        model=PHOTO_INTERVIEW_MODEL,
        instructions=PHOTO_FOLLOW_UP_SYSTEM_PROMPT,
        input=dedent(
            f"""
            아래는 사진 기반 인터뷰 대화입니다.
            최근 흐름을 이어서 사용자의 기억을 더 깊게 끌어낼 후속 질문을 작성하세요.

            대화:
            {transcript}
            """
        ).strip(),
        temperature=0.8,
    )
    message = str(getattr(response, "output_text", "")).strip()
    if not message:
        raise RuntimeError("후속 질문 응답을 생성하지 못했습니다.")
    return message
