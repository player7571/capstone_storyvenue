import json

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field

from app.db.client import get_supabase_anon_client, get_supabase_service_client

router = APIRouter(prefix="/auth", tags=["auth"])


class SignupRequest(BaseModel):
    email: str = Field(min_length=5, max_length=255)
    password: str = Field(min_length=6, max_length=128)
    name: str = Field(min_length=1, max_length=50)


class SignupResponse(BaseModel):
    message: str


class LoginRequest(BaseModel):
    email: str = Field(min_length=5, max_length=255)
    password: str = Field(min_length=6, max_length=128)


class LoginResponse(BaseModel):
    access_token: str
    user_id: str


def _extract_error_text(exc: Exception) -> str:
    raw = str(exc).strip()
    if not raw:
        return ""

    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return raw

    if isinstance(parsed, dict):
        for key in ("msg", "message", "error_description", "error", "code"):
            value = parsed.get(key)
            if value:
                return str(value)
    return raw


def _to_korean_error_message(exc: Exception, default_message: str) -> str:
    text = _extract_error_text(exc).lower()

    keyword_map: list[tuple[tuple[str, ...], str]] = [
        (("already registered", "user already exists"), "이미 가입된 이메일입니다."),
        (("invalid login credentials", "invalid_credentials"), "이메일 또는 비밀번호가 올바르지 않습니다."),
        (("email not confirmed", "email_not_confirmed"), "이메일 인증이 완료되지 않았습니다."),
        (("password", "weak_password"), "비밀번호는 6자 이상으로 입력해주세요."),
        (("invalid email", "email address"), "올바른 이메일 형식이 아닙니다."),
        (("network", "timeout"), "네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    ]

    for keywords, message in keyword_map:
        if any(keyword in text for keyword in keywords):
            return message

    return default_message


@router.post("/signup", response_model=SignupResponse)
def signup(payload: SignupRequest) -> SignupResponse:
    anon_supabase = get_supabase_anon_client()

    try:
        response = anon_supabase.auth.sign_up(
            {
                "email": payload.email,
                "password": payload.password,
                "options": {"data": {"name": payload.name}},
            }
        )
    except Exception as exc:  # noqa: BLE001
        message = _to_korean_error_message(
            exc,
            "회원가입 처리 중 오류가 발생했습니다. 입력값을 확인해주세요.",
        )
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=message) from exc

    if getattr(response, "user", None) is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="회원가입에 실패했습니다. 입력 정보를 다시 확인해주세요.",
        )

    try:
        user = response.user
        (
            get_supabase_service_client()
            .table("profiles")
            .upsert(
                {
                    "id": str(user.id),
                    "name": payload.name,
                    "email": payload.email,
                    "notification_enabled": True,
                },
                on_conflict="id",
            )
            .execute()
        )
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="회원가입은 되었지만 프로필 생성에 실패했습니다. 관리자에게 문의해주세요.",
        ) from exc

    return SignupResponse(message="회원가입 성공")


@router.post("/login", response_model=LoginResponse)
def login(payload: LoginRequest) -> LoginResponse:
    supabase = get_supabase_anon_client()

    try:
        response = supabase.auth.sign_in_with_password(
            {
                "email": payload.email,
                "password": payload.password,
            }
        )
    except Exception as exc:  # noqa: BLE001
        message = _to_korean_error_message(
            exc,
            "로그인에 실패했습니다. 이메일 또는 비밀번호를 확인해주세요.",
        )
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=message) from exc

    session = getattr(response, "session", None)
    user = getattr(response, "user", None)
    access_token = getattr(session, "access_token", None) if session else None
    user_id = getattr(user, "id", None) if user else None

    if not access_token or not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="로그인에 실패했습니다. 계정 상태를 확인해주세요.",
        )

    return LoginResponse(access_token=access_token, user_id=user_id)
