import json

from openai import OpenAI

from app.core.config import get_settings

DEFAULT_SYSTEM_PROMPT = (
    "주어진 텍스트가 혐오표현, 폭력, 성인 콘텐츠를 포함하는지 판단하세요. "
    "반드시 JSON으로만 응답하세요. 형식: {\"safe\": true/false, \"reason\": \"이유\"}"
)


def check_content_safety(content: str) -> dict:
    """OpenAI를 사용하여 콘텐츠가 안전한지 검사한다."""
    settings = get_settings()
    client = OpenAI(api_key=settings.openai_api_key)

    system_prompt = settings.openai_safety_prompt or DEFAULT_SYSTEM_PROMPT

    response = client.chat.completions.create(
        model=settings.openai_safety_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": content},
        ],
        response_format={"type": "json_object"},
        temperature=0,
    )

    raw = response.choices[0].message.content or "{}"
    try:
        data = json.loads(raw)
        return {
            "safe": bool(data.get("safe", False)),
            "reason": str(data.get("reason", "")),
        }
    except json.JSONDecodeError:
        # 파싱 실패 시 안전하지 않은 것으로 간주
        return {"safe": False, "reason": "안전 검사 응답 파싱 실패"}
