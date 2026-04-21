from functools import lru_cache
from io import BytesIO
from pathlib import Path
import re
from textwrap import dedent
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from openai import OpenAI
from pydantic import BaseModel, Field

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.sessions import InterviewStateResponse
from app.api.schemas.voice import VoiceTurnResponse
from app.core.config import get_settings
from app.db.supabase import get_supabase
from app.services.interview import (
    append_question_answer_record,
    INTERVIEW_STATE_ROLE,
    derive_voice_interview_state_from_session_messages,
    load_voice_interview_state_from_store,
    is_voice_interview_state_message,
    save_voice_interview_state_to_store,
    serialize_voice_interview_state,
)
from app.services.adaptive_interview import (
    get_interview_question,
    process_voice_interview_answer,
)
from app.services.photo_interview import generate_photo_follow_up_message

router = APIRouter(prefix="/voice", tags=["voice"])

INTERVIEWER_SYSTEM_PROMPT = (
    "당신은 따뜻한 자서전 인터뷰어입니다. "
    "사용자의 이야기를 깊이 있게 끌어내세요."
)
AUDIO_CACHE_DIR = Path(__file__).resolve().parents[3] / ".generated-audio"
PHOTO_SESSION_TYPE = "photo"
VOICE_COMPLETED_STATUS = "completed"


class UserAnswerInsights(BaseModel):
    summary: str = Field(min_length=1, description="사용자 답변 핵심 요약 한 문장")
    anchor_phrases: list[str] = Field(
        default_factory=list,
        description="원문에서 그대로 발췌한 근거 문구 1~3개",
    )


@lru_cache
def _get_openai_client() -> OpenAI:
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")
    return OpenAI(api_key=settings.openai_api_key)


def _get_session_or_404(session_id: UUID, user_id: str) -> dict:
    result = (
        get_supabase()
        .table("interview_sessions")
        .select("id, user_id, status, session_type")
        .eq("id", str(session_id))
        .eq("user_id", user_id)
        .maybe_single()
        .execute()
    )
    session = result.data
    if not session or str(session.get("status") or "").strip().lower() == "deleted":
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="문답을 찾을 수 없습니다.",
        )
    return session


def _insert_session_message(session_id: UUID, role: str, content: str) -> None:
    (
        get_supabase()
        .table("session_messages")
        .insert(
            {
                "session_id": str(session_id),
                "role": role,
                "content": content,
            }
        )
        .execute()
    )


def _load_voice_interview_state(session_id: UUID):
    try:
        stored = load_voice_interview_state_from_store(session_id)
        if stored is not None:
            return stored
    except Exception:
        pass

    result = (
        get_supabase()
        .table("session_messages")
        .select("role, content")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .execute()
    )
    return derive_voice_interview_state_from_session_messages(result.data or [])


def _save_voice_interview_state(session_id: UUID, state) -> None:
    try:
        save_voice_interview_state_to_store(session_id, state)
    except Exception:
        _insert_session_message(
            session_id,
            INTERVIEW_STATE_ROLE,
            serialize_voice_interview_state(state),
        )


def _update_voice_session_status(session_id: UUID, user_id: str, status_value: str) -> None:
    (
        get_supabase()
        .table("interview_sessions")
        .update({"status": status_value})
        .eq("id", str(session_id))
        .eq("user_id", user_id)
        .execute()
    )


def _load_conversation_history(session_id: UUID) -> list[dict[str, str]]:
    result = (
        get_supabase()
        .table("session_messages")
        .select("role, content")
        .eq("session_id", str(session_id))
        .order("created_at", desc=False)
        .limit(50)
        .execute()
    )

    history: list[dict[str, str]] = []
    for row in result.data or []:
        role = str(row.get("role", "")).strip()
        content = str(row.get("content", "")).strip()
        if role not in {"user", "assistant"} or not content or is_voice_interview_state_message(content):
            continue
        history.append({"role": role, "content": content})
    return history


def _build_audio_buffer(audio_bytes: bytes, filename: str | None) -> BytesIO:
    if not audio_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="오디오 파일이 비어 있습니다.",
        )
    buffer = BytesIO(audio_bytes)
    buffer.name = filename or "recording.wav"
    return buffer


def _build_transcription_prompt(session_id: UUID, session_type: str) -> str:
    if session_type == PHOTO_SESSION_TYPE:
        return dedent(
            """
            한국어 자서전 사진 인터뷰 음성입니다.
            사진을 보며 떠오른 기억, 사람, 장소, 감정을 자연스럽게 유지해 전사하세요.
            짧은 답변이라도 사용자의 실제 표현을 우선해 적어주세요.
            """
        ).strip()

    state = _load_voice_interview_state(session_id)
    question = get_interview_question(state.current_question_no)
    return dedent(
        f"""
        한국어 자서전 인터뷰 음성입니다.
        사용자는 현재 아래 질문에 답하고 있습니다.
        현재 질문: {question.main_question}
        질문 힌트: {question.hint}
        인물, 장소, 시기, 사건, 감정 표현을 자연스럽게 유지하며 전사하세요.
        """
    ).strip()


def _transcribe_audio(
    audio_bytes: bytes,
    filename: str | None,
    prompt_text: str | None = None,
) -> str:
    request_kwargs = {
        "model": "gpt-4o-transcribe",
        "language": "ko",
        "file": _build_audio_buffer(audio_bytes, filename),
    }
    if prompt_text:
        request_kwargs["prompt"] = prompt_text

    transcription = _get_openai_client().audio.transcriptions.create(**request_kwargs)
    user_text = str(getattr(transcription, "text", "")).strip()
    if not user_text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="음성에서 텍스트를 인식하지 못했습니다.",
        )
    return user_text


def _normalize_stt_text(user_text: str) -> str:
    normalized = re.sub(r"\s+", " ", user_text).strip()
    normalized = re.sub(r"\s+([,.!?])", r"\1", normalized)
    return normalized


def _validate_anchor_phrases(anchor_phrases: list[str], source_text: str) -> list[str]:
    valid: list[str] = []
    for raw in anchor_phrases:
        phrase = str(raw).strip().strip("\"'“”")
        if len(phrase) < 2:
            continue
        if phrase not in source_text:
            continue
        if phrase in valid:
            continue
        valid.append(phrase)
        if len(valid) >= 2:
            break
    return valid


def _fallback_anchor_phrases(source_text: str) -> list[str]:
    candidates: list[str] = []
    for segment in re.split(r"[.!?。\n]+", source_text):
        seg = segment.strip()
        if len(seg) < 6:
            continue
        candidates.append(seg[:24].strip())
        if len(candidates) >= 2:
            break
    if not candidates and source_text:
        candidates.append(source_text[:24].strip())
    return [p for p in candidates if p]


def _analyze_user_answer(source_text: str) -> UserAnswerInsights:
    cleaned = source_text.strip()
    if not cleaned:
        return UserAnswerInsights(summary="", anchor_phrases=[])

    sentences = [
        part.strip()
        for part in re.split(r"(?<=[.!?])\s+|\n+", cleaned)
        if part.strip()
    ]
    if not sentences:
        sentences = [cleaned]

    # STT 원문 내부에서만 요약/근거를 추출해 환각을 줄입니다.
    summary = " ".join(sentences[:2]).strip()
    if len(summary) > 120:
        summary = summary[:120].rstrip() + "..."

    anchors = _fallback_anchor_phrases(cleaned)
    return UserAnswerInsights(
        summary=summary or cleaned[:90].strip(),
        anchor_phrases=anchors,
    )


def _build_grounded_follow_up_question(anchor_phrases: list[str]) -> str:
    if len(anchor_phrases) >= 2:
        return (
            f"말씀해주신 '{anchor_phrases[0]}'와 '{anchor_phrases[1]}' 장면에서, "
            "그 순간 마음이 가장 편안해졌던 이유를 조금 더 들려주실 수 있을까요?"
        )
    if len(anchor_phrases) == 1:
        return (
            f"말씀해주신 '{anchor_phrases[0]}' 장면에서 "
            "그때 가장 선명했던 소리나 냄새는 무엇이었나요?"
        )
    return "방금 이야기에서 가장 마음이 놓였던 순간을 조금 더 자세히 들려주실 수 있을까요?"


def _generate_assistant_reply_from_user_text(user_text: str) -> str:
    insights = _analyze_user_answer(user_text)
    follow_up = _build_grounded_follow_up_question(insights.anchor_phrases)
    summary = insights.summary.strip()
    if summary:
        return f"정리하면, {summary}\n{follow_up}"
    return follow_up


def _extract_tts_bytes(tts_response: object) -> bytes:
    if hasattr(tts_response, "read"):
        try:
            data = tts_response.read()
            if isinstance(data, bytes):
                return data
        except TypeError:
            pass
    content = getattr(tts_response, "content", None)
    if isinstance(content, bytes):
        return content
    if isinstance(content, bytearray):
        return bytes(content)
    raise RuntimeError("TTS 응답에서 오디오 데이터를 추출하지 못했습니다.")


def _synthesize_tts(assistant_text: str) -> bytes:
    tts_response = _get_openai_client().audio.speech.create(
        model="gpt-4o-mini-tts",
        voice="coral",
        input=assistant_text,
    )
    audio_bytes = _extract_tts_bytes(tts_response)
    if not audio_bytes:
        raise RuntimeError("TTS 오디오가 비어 있습니다.")
    return audio_bytes


def _save_tts_file(session_id: UUID, audio_bytes: bytes) -> str:
    AUDIO_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    filename = f"{session_id}-{uuid4().hex}.mp3"
    output_path = AUDIO_CACHE_DIR / filename
    output_path.write_bytes(audio_bytes)
    return f"/generated-audio/{filename}"


def _run_user_turn(
    session_id: UUID,
    user_id: str,
    session_type: str,
    user_text: str,
    source_type: str,
) -> tuple[str, str | None, InterviewStateResponse | None]:
    decision = None
    interview_state = None

    if session_type == PHOTO_SESSION_TYPE:
        _insert_session_message(session_id, "user", user_text)
        history = _load_conversation_history(session_id)
        assistant_text = generate_photo_follow_up_message(history)
    else:
        current_state = _load_voice_interview_state(session_id)
        if current_state.is_interview_complete:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="이미 모든 질문이 완료되었습니다.",
            )

        _insert_session_message(session_id, "user", user_text)
        try:
            append_question_answer_record(
                session_id=session_id,
                question_no=current_state.current_question_no,
                user_text=user_text,
                source_type=source_type,
            )
        except Exception:
            pass
        outcome = process_voice_interview_answer(current_state, user_text)
        assistant_text = outcome.assistant_text
        decision = outcome.decision
        interview_state = InterviewStateResponse(**outcome.prompt_state.model_dump())
        _save_voice_interview_state(session_id, outcome.next_state)
        if outcome.next_state.is_interview_complete:
            _update_voice_session_status(session_id, user_id, VOICE_COMPLETED_STATUS)

    _insert_session_message(session_id, "assistant", assistant_text)
    return assistant_text, decision, interview_state


@router.post("/turn", response_model=VoiceTurnResponse)
async def voice_turn(
    session_id: UUID = Form(...),
    audio_file: UploadFile = File(...),
    user_id: str = Depends(get_current_user_id),
) -> VoiceTurnResponse:
    session = _get_session_or_404(session_id, user_id)

    try:
        session_type = str(session.get("session_type") or "voice").strip().lower()
        audio_bytes = await audio_file.read()
        prompt_text = _build_transcription_prompt(session_id, session_type)
        raw_user_text = _transcribe_audio(audio_bytes, audio_file.filename, prompt_text)
        user_text = _normalize_stt_text(raw_user_text)
        assistant_text, decision, interview_state = _run_user_turn(
            session_id=session_id,
            user_id=user_id,
            session_type=session_type,
            user_text=user_text,
            source_type="voice",
        )
        tts_audio = _synthesize_tts(assistant_text)
        audio_url = _save_tts_file(session_id, tts_audio)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"음성 턴 처리 중 오류가 발생했습니다: {exc}",
        ) from exc

    return VoiceTurnResponse(
        user_text=user_text,
        assistant_text=assistant_text,
        audio_url=audio_url,
        decision=decision,
        interview_state=interview_state,
    )


@router.post("/text-turn", response_model=VoiceTurnResponse)
async def voice_text_turn(
    session_id: UUID = Form(...),
    user_text: str = Form(...),
    user_id: str = Depends(get_current_user_id),
) -> VoiceTurnResponse:
    session = _get_session_or_404(session_id, user_id)
    cleaned_text = user_text.strip()
    if not cleaned_text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="텍스트 답변이 비어 있습니다.",
        )

    try:
        session_type = str(session.get("session_type") or "voice").strip().lower()
        assistant_text, decision, interview_state = _run_user_turn(
            session_id=session_id,
            user_id=user_id,
            session_type=session_type,
            user_text=cleaned_text,
            source_type="text",
        )
        tts_audio = _synthesize_tts(assistant_text)
        audio_url = _save_tts_file(session_id, tts_audio)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"텍스트 턴 처리 중 오류가 발생했습니다: {exc}",
        ) from exc

    return VoiceTurnResponse(
        user_text=cleaned_text,
        assistant_text=assistant_text,
        audio_url=audio_url,
        decision=decision,
        interview_state=interview_state,
    )
