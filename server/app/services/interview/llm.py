from functools import lru_cache
import logging
from typing import Any

from openai import OpenAI

from app.core.config import get_settings
from app.services.interview.types import InterviewQuestion


PROMPT_CACHE_VERSION = "v1"


def build_interview_prompt_cache_body(kind: str, question: InterviewQuestion) -> dict[str, str]:
    return {
        "prompt_cache_key": (
            f"storyvenue:interview:{kind}:q{question.question_no}:{PROMPT_CACHE_VERSION}"
        )
    }


def _read_usage_value(value: Any, key: str) -> Any:
    if value is None:
        return None
    if isinstance(value, dict):
        return value.get(key)
    return getattr(value, key, None)


def log_interview_prompt_cache_usage(
    logger: logging.Logger,
    kind: str,
    question: InterviewQuestion,
    response: Any,
) -> None:
    usage = _read_usage_value(response, "usage")
    if usage is None:
        return

    input_tokens = _read_usage_value(usage, "input_tokens")
    output_tokens = _read_usage_value(usage, "output_tokens")
    total_tokens = _read_usage_value(usage, "total_tokens")
    details = _read_usage_value(usage, "input_tokens_details") or _read_usage_value(
        usage,
        "prompt_tokens_details",
    )
    cached_tokens = _read_usage_value(details, "cached_tokens") or 0

    logger.info(
        "[prompt_cache] kind=%s question_no=%s cache_key=%s input_tokens=%s cached_tokens=%s output_tokens=%s total_tokens=%s",
        kind,
        question.question_no,
        build_interview_prompt_cache_body(kind, question)["prompt_cache_key"],
        input_tokens,
        cached_tokens,
        output_tokens,
        total_tokens,
    )


@lru_cache
def get_interview_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)
