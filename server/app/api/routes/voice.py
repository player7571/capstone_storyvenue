from functools import lru_cache
from io import BytesIO
from pathlib import Path
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from openai import OpenAI

from app.api.dependencies.auth import get_current_user_id
from app.api.schemas.voice import VoiceTurnResponse
from app.core.config import get_settings
from app.db.supabase import get_supabase

router = APIRouter(prefix="/voice", tags=["voice"])

INTERVIEWER_SYSTEM_PROMPT = (
    "당신은 따뜻한 자서전 인터뷰어입니다. "
    "사용자의 이야기를 깊이 있게 끌어내세요."
)
AUDIO_CACHE_DIR = Path(__file__).resolve().parents[3] / ".generated-audio"


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
        .select("id, user_id, status")
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
        if role not in {"user", "assistant"} or not content:
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


def _transcribe_audio(audio_bytes: bytes, filename: str | None) -> str:
    transcription = _get_openai_client().audio.transcriptions.create(
        model="gpt-4o-transcribe",
        language="ko",
        file=_build_audio_buffer(audio_bytes, filename),
    )
    user_text = str(getattr(transcription, "text", "")).strip()
    if not user_text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="음성에서 텍스트를 인식하지 못했습니다.",
        )
    return user_text


def _generate_assistant_reply(history: list[dict[str, str]]) -> str:
    response = _get_openai_client().responses.create(
        model="gpt-4.1-mini",
        input=[
            {"role": "system", "content": INTERVIEWER_SYSTEM_PROMPT},
            *history,
        ],
        temperature=0.7,
    )
    assistant_text = str(getattr(response, "output_text", "")).strip()
    if not assistant_text:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="AI 응답 생성에 실패했습니다.",
        )
    return assistant_text


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


@router.post("/turn", response_model=VoiceTurnResponse)
async def voice_turn(
    session_id: UUID = Form(...),
    audio_file: UploadFile = File(...),
    user_id: str = Depends(get_current_user_id),
) -> VoiceTurnResponse:
    _get_session_or_404(session_id, user_id)

    try:
        audio_bytes = await audio_file.read()
        user_text = _transcribe_audio(audio_bytes, audio_file.filename)
        _insert_session_message(session_id, "user", user_text)

        history = _load_conversation_history(session_id)
        assistant_text = _generate_assistant_reply(history)
        _insert_session_message(session_id, "assistant", assistant_text)

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
    )
