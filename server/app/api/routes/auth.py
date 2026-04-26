import hashlib
import hmac
import json
import secrets
import time
from datetime import UTC, datetime, timedelta
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request as UrlRequest, urlopen

from fastapi import APIRouter, HTTPException, Request, status
from fastapi.responses import RedirectResponse
from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.db.client import get_supabase_anon_client, get_supabase_service_client

router = APIRouter(prefix="/auth", tags=["auth"])

KAKAO_AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize"
KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token"
KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me"
DEV_KAKAO_CODE_PREFIX = "local-dev-kakao-"
DEV_KAKAO_ID = "local-dev-user"
KAKAO_STATE_TTL_SECONDS = 10 * 60


class LegacySignupRequest(BaseModel):
    email: str = Field(min_length=5, max_length=255)
    password: str = Field(min_length=6, max_length=128)
    name: str = Field(min_length=1, max_length=50)


class LegacyLoginRequest(BaseModel):
    email: str = Field(min_length=5, max_length=255)
    password: str = Field(min_length=6, max_length=128)


class KakaoLoginRequest(BaseModel):
    code: str = Field(min_length=1, max_length=2048)
    state: str | None = Field(default=None, max_length=512)


class TokenRefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=1, max_length=4096)


class KakaoAuthorizeUrlResponse(BaseModel):
    authorize_url: str
    state: str


class KakaoAuthResponse(BaseModel):
    access_token: str
    refresh_token: str
    user_id: str
    name: str
    email: str
    token_type: str = "bearer"
    expires_in: int | None = None


def _auth_bad_request(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=message)


def _auth_unauthorized(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=message)


def _read_json_response(request: UrlRequest, unauthorized_message: str) -> dict:
    try:
        with urlopen(request, timeout=12) as response:  # noqa: S310
            raw = response.read().decode("utf-8")
    except HTTPError as exc:
        detail = unauthorized_message
        try:
            error_payload = json.loads(exc.read().decode("utf-8"))
            detail = str(
                error_payload.get("error_description")
                or error_payload.get("error")
                or error_payload.get("msg")
                or unauthorized_message
            )
        except Exception:
            pass
        raise _auth_unauthorized(detail) from exc
    except URLError as exc:
        raise _auth_unauthorized("인증 서버 통신에 실패했습니다. 잠시 후 다시 시도해주세요.") from exc
    except Exception as exc:
        raise _auth_unauthorized(unauthorized_message) from exc

    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise _auth_unauthorized(unauthorized_message) from exc


def _require_kakao_settings() -> tuple[str, str, str]:
    settings = get_settings()
    rest_key = str(settings.kakao_rest_api_key or "").strip()
    client_secret = str(settings.kakao_client_secret or "").strip()
    redirect_uri = str(settings.kakao_redirect_uri or "").strip()

    missing = [
        name
        for name, value in (
            ("KAKAO_REST_API_KEY", rest_key),
            ("KAKAO_REDIRECT_URI", redirect_uri),
        )
        if not value
    ]
    if missing:
        raise _auth_bad_request(
            "카카오 인증 환경변수가 설정되지 않았습니다: " + ", ".join(missing)
        )
    return rest_key, client_secret, redirect_uri


def _is_local_env() -> bool:
    return str(get_settings().app_env or "").strip().lower() in {"local", "dev", "development"}


def _is_dev_kakao_login_enabled() -> bool:
    settings = get_settings()
    return bool(settings.kakao_allow_dev_login) and _is_local_env()


def _configured_redirect_uri() -> str:
    redirect_uri = str(get_settings().kakao_redirect_uri or "").strip()
    if not redirect_uri:
        raise _auth_bad_request(
            "카카오 인증 환경변수가 설정되지 않았습니다: KAKAO_REDIRECT_URI"
        )
    return redirect_uri


def _is_dev_kakao_code(code: str) -> bool:
    return code.startswith(DEV_KAKAO_CODE_PREFIX)


def _state_secret() -> str:
    settings = get_settings()
    secret = str(
        settings.auth_refresh_token_pepper
        or settings.auth_shadow_password_pepper
        or ""
    ).strip()
    if not secret:
        raise _auth_bad_request(
            "인증 보안 환경변수가 설정되지 않았습니다: AUTH_REFRESH_TOKEN_PEPPER"
        )
    return secret


def _sign_oauth_state(payload: str) -> str:
    return hmac.new(
        _state_secret().encode("utf-8"),
        payload.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def _create_oauth_state() -> str:
    payload = f"{int(time.time())}.{secrets.token_urlsafe(24)}"
    return f"{payload}.{_sign_oauth_state(payload)}"


def _verify_oauth_state(state: str | None) -> None:
    value = str(state or "").strip()
    if not value:
        raise _auth_bad_request("카카오 인증 state 값이 필요합니다.")

    parts = value.split(".")
    if len(parts) != 3:
        raise _auth_bad_request("카카오 인증 state 값이 올바르지 않습니다.")

    issued_at_raw, nonce, signature = parts
    payload = f"{issued_at_raw}.{nonce}"
    expected_signature = _sign_oauth_state(payload)
    if not hmac.compare_digest(signature, expected_signature):
        raise _auth_bad_request("카카오 인증 state 검증에 실패했습니다.")

    try:
        issued_at = int(issued_at_raw)
    except ValueError as exc:
        raise _auth_bad_request("카카오 인증 state 값이 올바르지 않습니다.") from exc

    if int(time.time()) - issued_at > KAKAO_STATE_TTL_SECONDS:
        raise _auth_bad_request("카카오 인증 시간이 만료되었습니다. 다시 시도해주세요.")


def _require_shadow_password_pepper() -> str:
    pepper = str(get_settings().auth_shadow_password_pepper or "").strip()
    if not pepper:
        raise _auth_bad_request(
            "인증 보안 환경변수가 설정되지 않았습니다: AUTH_SHADOW_PASSWORD_PEPPER"
        )
    return pepper


def _shadow_email_from_kakao_id(kakao_id: str) -> str:
    return f"kakao_{kakao_id}@storyvenue.local"


def _shadow_password_from_kakao_id(kakao_id: str) -> str:
    digest = hmac.new(
        _require_shadow_password_pepper().encode("utf-8"),
        f"kakao:{kakao_id}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return f"Kakao!{digest[:48]}"


def _extract_kakao_access_token(code: str) -> str:
    rest_key, client_secret, redirect_uri = _require_kakao_settings()

    payload_data = {
        "grant_type": "authorization_code",
        "client_id": rest_key,
        "redirect_uri": redirect_uri,
        "code": code,
    }
    if client_secret:
        payload_data["client_secret"] = client_secret

    payload = urlencode(payload_data).encode("utf-8")
    request = UrlRequest(
        KAKAO_TOKEN_URL,
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded;charset=utf-8"},
        method="POST",
    )
    token_data = _read_json_response(request, "카카오 인증 코드 검증에 실패했습니다.")
    access_token = str(token_data.get("access_token") or "").strip()
    if not access_token:
        raise _auth_unauthorized("카카오 인증 토큰 발급에 실패했습니다.")
    return access_token


def _load_kakao_user_profile(kakao_access_token: str) -> tuple[str, str, str]:
    request = UrlRequest(
        KAKAO_USER_INFO_URL,
        headers={"Authorization": f"Bearer {kakao_access_token}"},
        method="GET",
    )
    user_data = _read_json_response(request, "카카오 사용자 정보 조회에 실패했습니다.")

    kakao_id = str(user_data.get("id") or "").strip()
    if not kakao_id:
        raise _auth_unauthorized("카카오 사용자 식별값을 확인할 수 없습니다.")

    kakao_account = user_data.get("kakao_account") or {}
    profile = kakao_account.get("profile") or {}
    properties = user_data.get("properties") or {}

    nickname = str(
        profile.get("nickname")
        or properties.get("nickname")
        or f"카카오사용자_{kakao_id[-6:]}"
    ).strip()
    email = str(kakao_account.get("email") or "").strip()
    return kakao_id, nickname or f"카카오사용자_{kakao_id[-6:]}", email


def _safe_attr(value: object, key: str) -> str:
    if isinstance(value, dict):
        return str(value.get(key) or "").strip()
    return str(getattr(value, key, "") or "").strip()


def _find_auth_user_id_by_email(email: str) -> str | None:
    admin = get_supabase_service_client().auth.admin
    page = 1
    per_page = 200
    while True:
        users = admin.list_users(page=page, per_page=per_page)
        if not users:
            return None

        for user in users:
            if _safe_attr(user, "email").lower() == email.lower():
                user_id = _safe_attr(user, "id")
                if user_id:
                    return user_id

        if len(users) < per_page:
            return None
        page += 1


def _ensure_shadow_user(kakao_id: str, nickname: str, kakao_email: str) -> tuple[str, str, str]:
    shadow_email = _shadow_email_from_kakao_id(kakao_id)
    shadow_password = _shadow_password_from_kakao_id(kakao_id)
    service = get_supabase_service_client()

    user_id = _find_auth_user_id_by_email(shadow_email)
    metadata = {
        "name": nickname,
        "provider": "kakao",
        "kakao_id": kakao_id,
    }
    if kakao_email:
        metadata["kakao_email"] = kakao_email

    try:
        if user_id:
            service.auth.admin.update_user_by_id(
                user_id,
                {
                    "password": shadow_password,
                    "email_confirm": True,
                    "user_metadata": metadata,
                    "app_metadata": {"provider": "kakao"},
                },
            )
        else:
            created = service.auth.admin.create_user(
                {
                    "email": shadow_email,
                    "password": shadow_password,
                    "email_confirm": True,
                    "user_metadata": metadata,
                    "app_metadata": {"provider": "kakao"},
                }
            )
            created_user = getattr(created, "user", None)
            user_id = _safe_attr(created_user, "id")
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise _auth_unauthorized("서비스 사용자 계정 생성에 실패했습니다.") from exc

    if not user_id:
        raise _auth_unauthorized("서비스 사용자 계정을 확인할 수 없습니다.")

    profile_email = kakao_email or shadow_email
    try:
        (
            service.table("profiles")
            .upsert(
                {
                    "id": user_id,
                    "name": nickname,
                    "email": profile_email,
                },
                on_conflict="id",
            )
            .execute()
        )
    except Exception as exc:  # noqa: BLE001
        raise _auth_unauthorized("프로필 동기화에 실패했습니다.") from exc

    return user_id, shadow_email, shadow_password


def _extract_session_tokens(auth_response: object) -> tuple[str, str, str, int | None]:
    session = getattr(auth_response, "session", None)
    user = getattr(auth_response, "user", None)
    access_token = str(getattr(session, "access_token", "") or "").strip()
    refresh_token = str(getattr(session, "refresh_token", "") or "").strip()
    user_id = _safe_attr(user, "id")
    expires_in_raw = getattr(session, "expires_in", None)
    expires_in = int(expires_in_raw) if isinstance(expires_in_raw, int | float) else None

    if not access_token or not refresh_token or not user_id:
        raise _auth_unauthorized("서비스 토큰 발급에 실패했습니다.")
    return access_token, refresh_token, user_id, expires_in


def _refresh_hash_pepper() -> str:
    settings = get_settings()
    pepper = str(settings.auth_refresh_token_pepper or "").strip()
    if pepper:
        return pepper
    return _require_shadow_password_pepper()


def _hash_refresh_token(refresh_token: str) -> str:
    raw = f"{_refresh_hash_pepper()}:{refresh_token}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _store_refresh_token_hash(user_id: str, refresh_token: str, expires_in: int | None) -> None:
    token_hash = _hash_refresh_token(refresh_token)
    expires_at = None
    if isinstance(expires_in, int) and expires_in > 0:
        expires_at = (datetime.now(UTC) + timedelta(seconds=expires_in)).isoformat()

    try:
        (
            get_supabase_service_client()
            .table("service_refresh_tokens")
            .insert(
                {
                    "user_id": user_id,
                    "token_hash": token_hash,
                    "provider": "kakao",
                    "expires_at": expires_at,
                }
            )
            .execute()
        )
    except Exception:
        # 테이블 미생성 환경을 고려해 인증 흐름은 유지한다.
        pass


def _load_profile_identity(user_id: str) -> tuple[str, str]:
    try:
        result = (
            get_supabase_service_client()
            .table("profiles")
            .select("name, email")
            .eq("id", user_id)
            .maybe_single()
            .execute()
        )
        row = result.data or {}
        return str(row.get("name") or "").strip(), str(row.get("email") or "").strip()
    except Exception:
        return "", ""


def _mark_refresh_token_revoked(refresh_token: str) -> None:
    token_hash = _hash_refresh_token(refresh_token)
    try:
        (
            get_supabase_service_client()
            .table("service_refresh_tokens")
            .update({"revoked_at": datetime.now(UTC).isoformat()})
            .eq("token_hash", token_hash)
            .is_("revoked_at", "null")
            .execute()
        )
    except Exception:
        pass


def _validate_refresh_token_hash(refresh_token: str) -> None:
    token_hash = _hash_refresh_token(refresh_token)
    try:
        result = (
            get_supabase_service_client()
            .table("service_refresh_tokens")
            .select("id, revoked_at")
            .eq("token_hash", token_hash)
            .maybe_single()
            .execute()
        )
        row = result.data
        if row and not row.get("revoked_at"):
            return
    except Exception:
        # 테이블 미생성 환경에서는 검증을 스킵한다.
        return

    raise _auth_unauthorized("유효하지 않은 리프레시 토큰입니다.")


@router.get("/kakao/authorize-url", response_model=KakaoAuthorizeUrlResponse)
def get_kakao_authorize_url(request: Request) -> KakaoAuthorizeUrlResponse:
    settings = get_settings()
    rest_key = str(settings.kakao_rest_api_key or "").strip()
    redirect_uri = _configured_redirect_uri()
    _require_shadow_password_pepper()
    state = _create_oauth_state()

    if not rest_key:
        if not _is_dev_kakao_login_enabled():
            raise _auth_bad_request(
                "실제 카카오 로그인을 사용하려면 서버 환경변수 KAKAO_REST_API_KEY를 설정해주세요."
            )
        query = urlencode({"redirect_uri": redirect_uri, "state": state})
        dev_authorize_url = str(request.url_for("dev_kakao_authorize")) + f"?{query}"
        return KakaoAuthorizeUrlResponse(authorize_url=dev_authorize_url, state=state)

    query = urlencode(
        {
            "client_id": rest_key,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": "profile_nickname",
            "prompt": "select_account",
            "state": state,
        }
    )
    return KakaoAuthorizeUrlResponse(authorize_url=f"{KAKAO_AUTHORIZE_URL}?{query}", state=state)


@router.get("/kakao/dev/authorize", include_in_schema=False)
def dev_kakao_authorize(redirect_uri: str, state: str | None = None) -> RedirectResponse:
    if not _is_dev_kakao_login_enabled():
        raise _auth_bad_request("개발용 카카오 로그인은 로컬 환경에서만 사용할 수 있습니다.")
    _verify_oauth_state(state)
    code = f"{DEV_KAKAO_CODE_PREFIX}{int(datetime.now(UTC).timestamp())}"
    separator = "&" if "?" in redirect_uri else "?"
    query = urlencode({"code": code, "state": state})
    return RedirectResponse(url=f"{redirect_uri}{separator}{query}")


@router.get("/kakao/callback", include_in_schema=False)
def kakao_callback(code: str | None = None, state: str | None = None, error: str | None = None) -> dict:
    return {
        "code": code,
        "state": state,
        "error": error,
        "message": "카카오 인증 콜백을 수신했습니다. 앱에서 자동으로 처리를 계속합니다.",
    }


@router.post("/kakao/login", response_model=KakaoAuthResponse)
def kakao_login(payload: KakaoLoginRequest) -> KakaoAuthResponse:
    code = str(payload.code).strip()
    if not code:
        raise _auth_bad_request("카카오 인증 코드가 필요합니다.")
    _verify_oauth_state(payload.state)

    if _is_dev_kakao_code(code):
        if not _is_dev_kakao_login_enabled():
            raise _auth_unauthorized("개발용 카카오 인증 코드는 사용할 수 없습니다.")
        kakao_id, nickname, kakao_email = (
            DEV_KAKAO_ID,
            "카카오 개발 사용자",
            "kakao-dev@storyvenue.local",
        )
    else:
        kakao_access_token = _extract_kakao_access_token(code)
        kakao_id, nickname, kakao_email = _load_kakao_user_profile(kakao_access_token)

    _, shadow_email, shadow_password = _ensure_shadow_user(kakao_id, nickname, kakao_email)
    profile_email = kakao_email or shadow_email

    try:
        auth_response = get_supabase_anon_client().auth.sign_in_with_password(
            {
                "email": shadow_email,
                "password": shadow_password,
            }
        )
    except Exception as exc:  # noqa: BLE001
        raise _auth_unauthorized("서비스 로그인에 실패했습니다.") from exc

    access_token, refresh_token, user_id, expires_in = _extract_session_tokens(auth_response)
    _store_refresh_token_hash(user_id, refresh_token, expires_in)
    return KakaoAuthResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        user_id=user_id,
        name=nickname,
        email=profile_email,
        expires_in=expires_in,
    )


@router.post("/kakao/refresh", response_model=KakaoAuthResponse)
def kakao_refresh(payload: TokenRefreshRequest) -> KakaoAuthResponse:
    refresh_token = str(payload.refresh_token).strip()
    if not refresh_token:
        raise _auth_bad_request("리프레시 토큰이 필요합니다.")

    _validate_refresh_token_hash(refresh_token)

    try:
        auth_response = get_supabase_anon_client().auth.refresh_session(refresh_token)
    except Exception as exc:  # noqa: BLE001
        raise _auth_unauthorized("리프레시 토큰 검증에 실패했습니다.") from exc

    access_token, new_refresh_token, user_id, expires_in = _extract_session_tokens(auth_response)
    _mark_refresh_token_revoked(refresh_token)
    _store_refresh_token_hash(user_id, new_refresh_token, expires_in)
    name, email = _load_profile_identity(user_id)
    return KakaoAuthResponse(
        access_token=access_token,
        refresh_token=new_refresh_token,
        user_id=user_id,
        name=name,
        email=email,
        expires_in=expires_in,
    )


@router.post("/signup")
def signup(_: LegacySignupRequest) -> None:
    raise _auth_bad_request("이메일 회원가입은 종료되었습니다. 카카오 로그인을 이용해주세요.")


@router.post("/login")
def login(_: LegacyLoginRequest) -> None:
    raise _auth_bad_request("이메일 로그인은 종료되었습니다. 카카오 로그인을 이용해주세요.")
