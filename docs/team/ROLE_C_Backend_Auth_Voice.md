# 역할 C — 백엔드: 인증 · 세션 · 음성

> **담당자**: \_\_\_\_\_\_\_\_  
> **브랜치 접두사**: `feature/be-auth/`  
> **핵심 기술**: Python · FastAPI · Supabase · OpenAI STT/TTS

---

## 내 역할 한 줄 요약

앱의 **로그인/회원가입**과 **음성 인터뷰의 핵심 흐름**을 서버에서 처리하는 역할입니다.  
사용자가 말하면 → 텍스트로 변환하고 → AI가 응답하고 → 음성으로 다시 내보내는  
파이프라인 전체를 담당합니다.

---

## 내가 만들 API 목록

| API | 설명 |
|-----|------|
| `POST /auth/signup` | 회원가입 |
| `POST /auth/login` | 로그인 → JWT 토큰 반환 |
| `POST /sessions` | 새 인터뷰 세션 생성 |
| `GET /sessions` | 내 세션 목록 조회 |
| `GET /sessions/{id}` | 세션 상세 조회 |
| `POST /voice/turn` | 음성 파일 받아서 STT → AI 응답 → TTS 반환 |
| `GET /messages` | 세션 대화 기록 조회 |

---

## 개발 환경 세팅 (처음 1회만)

### 1. Python 설치
- 구글에서 "Python 3.11 다운로드" 검색 후 설치
- 설치 시 **"Add Python to PATH"** 체크 꼭 하기

### 2. 새 프로젝트 클론
```bash
git clone <팀장에게 받은 GitHub 주소>
cd <프로젝트 폴더>/server
```

### 3. Python 가상환경 만들기
```bash
python -m venv venv
source venv/bin/activate        # Mac
venv\Scripts\activate           # Windows
```

### 4. 패키지 설치
```bash
pip install fastapi uvicorn supabase openai python-multipart pydantic-settings python-dotenv
```

### 5. 환경 변수 파일 만들기
`server/.env` 파일을 만들고 팀장에게 받은 값을 넣기:
```
SUPABASE_URL=팀장에게받은값
SUPABASE_ANON_KEY=팀장에게받은값
SUPABASE_SERVICE_ROLE_KEY=팀장에게받은값
OPENAI_API_KEY=팀장에게받은값
```

### 6. 서버 실행 확인
```bash
uvicorn app.main:app --reload --port 8000
```
브라우저에서 `http://127.0.0.1:8000/docs` 열면 API 문서가 보이면 성공

---

## 단계별 할 일

### STEP 1 — FastAPI 기본 구조 만들기

**Codex에 붙여넣을 프롬프트:**
```
Python FastAPI 프로젝트를 새로 만들어줘.
폴더 구조:
server/
  app/
    main.py          (FastAPI 앱 진입점)
    core/
      config.py      (환경 변수 설정)
    api/
      routes/
        auth.py      (인증 라우터)
        sessions.py  (세션 라우터)
        voice.py     (음성 라우터)
        messages.py  (메시지 라우터)
        health.py    (헬스체크)
    db/
      client.py      (Supabase 클라이언트)

main.py에서 모든 라우터를 연결하고,
GET /health 가 {"status": "ok"} 를 반환하게 해줘.
```

**완료 확인:**
```bash
curl http://127.0.0.1:8000/health
# {"status":"ok"} 나오면 성공
```

**커밋:**
```bash
git commit -m "chore: FastAPI 프로젝트 기본 구조 생성"
```

---

### STEP 2 — Supabase 연결 설정

**Codex에 붙여넣을 프롬프트:**
```
FastAPI에서 Supabase를 연결하는 코드를 만들어줘.
- .env 파일에서 SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY를 읽기
- anon client: 일반 요청용
- service_role client: 관리자 권한 요청용
- pydantic-settings를 사용해서 config.py에 Settings 클래스 만들기
```

**커밋:**
```bash
git commit -m "chore: Supabase 클라이언트 연결 설정"
```

---

### STEP 3 — 회원가입 · 로그인 API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI auth.py에 아래 두 API를 만들어줘.

POST /auth/signup
- 요청: { "email": string, "password": string, "name": string }
- Supabase Auth로 회원가입
- 성공 시: { "message": "회원가입 성공" }
- 실패 시: 적절한 에러 메시지

POST /auth/login
- 요청: { "email": string, "password": string }
- Supabase Auth로 로그인
- 성공 시: { "access_token": string, "user_id": string }
- 실패 시: 401 에러

모든 에러 메시지는 한국어로 해줘.
```

**완료 확인:**
```bash
# 회원가입 테스트
curl -X POST http://127.0.0.1:8000/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234","name":"테스트"}'
```

**커밋:**
```bash
git commit -m "feat(auth): 회원가입/로그인 API 구현"
```

---

### STEP 4 — JWT 인증 미들웨어 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI에서 JWT 토큰으로 사용자를 인증하는 의존성(Dependency)을 만들어줘.
- Authorization: Bearer <토큰> 헤더에서 토큰을 읽기
- Supabase로 토큰 검증
- 검증 성공 시 user_id(UUID)를 반환
- 검증 실패 시 401 에러 반환
함수 이름: get_current_user_id
```

**커밋:**
```bash
git commit -m "feat(auth): JWT 인증 미들웨어 구현"
```

---

### STEP 5 — 세션 API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI sessions.py에 아래 API를 만들어줘.
Supabase의 interview_sessions 테이블을 사용해줘.
테이블 컬럼: id(uuid), user_id(uuid), title(text), theme(text), status(text), created_at

POST /sessions (인증 필요)
- 요청: { "title": string, "theme": string }
- 새 세션 생성 후 세션 정보 반환

GET /sessions (인증 필요)
- 내 세션 목록 반환 (최신순)

GET /sessions/{session_id} (인증 필요)
- 특정 세션 상세 반환
```

**커밋:**
```bash
git commit -m "feat(session): 인터뷰 세션 CRUD API 구현"
```

---

### STEP 6 — 음성 인터뷰 API 만들기 (핵심!)

**Codex에 붙여넣을 프롬프트:**
```
FastAPI voice.py에 음성 인터뷰 API를 만들어줘.

POST /voice/turn (인증 필요)
- 요청: multipart/form-data
  - session_id: UUID (Form)
  - audio_file: UploadFile (File)
- 처리 순서:
  1. OpenAI Whisper(gpt-4o-transcribe)로 음성을 텍스트로 변환
     모델: "gpt-4o-transcribe", 언어: "ko"
  2. 변환된 텍스트를 Supabase session_messages 테이블에 저장 (role: "user")
  3. OpenAI gpt-4.1-mini로 AI 인터뷰어 응답 생성
     시스템 프롬프트: "당신은 따뜻한 자서전 인터뷰어입니다. 사용자의 이야기를 깊이 있게 끌어내세요."
  4. AI 응답을 session_messages 테이블에 저장 (role: "assistant")
  5. OpenAI TTS(gpt-4o-mini-tts)로 AI 응답을 음성으로 변환
     보이스: "coral"
  6. 응답: { "user_text": string, "assistant_text": string, "audio_url": string }

session_messages 테이블 컬럼: id, session_id, role, content, created_at
```

**커밋:**
```bash
git commit -m "feat(voice): STT→AI응답→TTS 음성 인터뷰 API 구현"
```

---

### STEP 7 — 대화 기록 API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI messages.py에 아래 API를 만들어줘.

GET /messages (인증 필요)
- 쿼리 파라미터: session_id (UUID)
- 해당 세션의 대화 기록을 시간순으로 반환
- 반환: [{ "id": string, "role": string, "content": string, "created_at": string }]
```

**커밋:**
```bash
git commit -m "feat(session): 대화 기록 조회 API 구현"
```

---

## 이 역할의 완료 기준

- [ ] `/health` 가 `{"status":"ok"}` 를 반환한다
- [ ] 회원가입 후 로그인하면 `access_token` 이 반환된다
- [ ] 세션을 만들고 목록을 조회할 수 있다
- [ ] 음성 파일을 보내면 텍스트와 AI 음성 응답이 돌아온다
- [ ] 안드로이드 앱에서 로그인이 실제로 된다

---

## Supabase 테이블 설계 (팀장과 공유)

```sql
-- 세션 테이블
CREATE TABLE interview_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  title TEXT,
  theme TEXT,
  status TEXT DEFAULT 'active',
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 메시지 테이블
CREATE TABLE session_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES interview_sessions(id),
  role TEXT CHECK (role IN ('user', 'assistant')),
  content TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

---

## 참고 규칙

- 깃 커밋/브랜치 규칙: [GIT_CONVENTION.md](./GIT_CONVENTION.md)
- API 명세: `docs/API.md`
- 서버 실행: `uvicorn app.main:app --reload --port 8000`
- API 문서 확인: `http://127.0.0.1:8000/docs`
