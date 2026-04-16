import json

from openai import OpenAI

from app.core.config import get_settings

DEFAULT_SYSTEM_PROMPT = (
    "주어진 텍스트가 혐오표현, 폭력, 성인 콘텐츠를 포함하는지 판단하세요. "
    "반드시 JSON으로만 응답하세요. 형식: {\"safe\": true/false, \"reason\": \"이유\"}"
)

MODERATION_MODEL = "omni-moderation-latest"


def _get_client() -> OpenAI:
    settings = get_settings()
    return OpenAI(api_key=settings.openai_api_key)


def _run_moderation(client: OpenAI, content: str) -> dict:
    """OpenAI Moderation API(무료)로 1차 필터링."""
    response = client.moderations.create(model=MODERATION_MODEL, input=content)
    result = response.results[0]

    categories = result.categories
    if hasattr(categories, "model_dump"):
        categories_dict = categories.model_dump()
    else:
        categories_dict = dict(categories)

    flagged_categories = [k for k, v in categories_dict.items() if v]
    return {"flagged": bool(result.flagged), "categories": flagged_categories}


def _run_detailed_check(client: OpenAI, content: str, flagged_categories: list[str]) -> dict:
    """gpt-4o-mini로 자연스러운 한국어 사유를 생성하며 재확인한다."""
    settings = get_settings()
    system_prompt = settings.openai_safety_prompt or DEFAULT_SYSTEM_PROMPT

    user_content = content
    if flagged_categories:
        hint = ", ".join(flagged_categories)
        user_content = f"[사전 검사에서 감지된 카테고리: {hint}]\n\n{content}"

    response = client.chat.completions.create(
        model=settings.openai_safety_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_content},
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
        return {"safe": False, "reason": "안전 검사 응답 파싱 실패"}


def check_content_safety(content: str) -> dict:
    """2단계 콘텐츠 안전 검사.

    1) OpenAI Moderation API(무료)로 1차 필터 — 대부분 여기서 통과
    2) 플래그된 경우에만 gpt-4o-mini로 재확인 + 자연스러운 한국어 사유 생성
    """
    if not content or not content.strip():
        return {"safe": True, "reason": ""}

    client = _get_client()

    moderation = _run_moderation(client, content)
    if not moderation["flagged"]:
        return {"safe": True, "reason": ""}

    return _run_detailed_check(client, content, moderation["categories"])
