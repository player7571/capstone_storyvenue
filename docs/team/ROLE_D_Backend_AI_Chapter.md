# 역할 D — 백엔드: AI · 챕터 · 책 생성

> **담당자**: \_\_\_\_\_\_\_\_  
> **브랜치 접두사**: `feature/be-ai/`  
> **핵심 기술**: Python · FastAPI · OpenAI GPT-4.1-mini · Supabase

---

## 내 역할 한 줄 요약

인터뷰 내용을 바탕으로 **AI가 챕터(글)를 자동으로 생성**하고,  
챕터들을 모아서 **책으로 엮는** 서버 기능을 담당합니다.  
프로젝트에서 가장 핵심적인 AI 파이프라인 역할입니다.

---

## 내가 만들 API 목록

| API | 설명 |
|-----|------|
| `POST /chapters/generate` | 인터뷰 내용으로 챕터 자동 생성 |
| `GET /chapters` | 내 챕터 목록 조회 |
| `GET /chapters/{id}` | 챕터 상세 조회 |
| `PUT /chapters/{id}` | 챕터 내용 수정 |
| `POST /book/compile` | 챕터들을 책으로 편집 |
| `GET /book` | 내 책 목록 조회 |
| `GET /memory` | 사용자 기억 정보 조회 |

---

## 개발 환경 세팅 (처음 1회만)

역할 C와 동일한 서버 프로젝트를 공유합니다.

### 1. Python 가상환경 활성화
```bash
cd <프로젝트 폴더>/server
source venv/bin/activate        # Mac
venv\Scripts\activate           # Windows
```

### 2. 패키지 설치 (추가분)
```bash
pip install fastapi uvicorn supabase openai python-multipart pydantic-settings python-dotenv
```

### 3. 서버 실행 확인
```bash
uvicorn app.main:app --reload --port 8000
```

> **역할 C와 같은 서버 프로젝트를 공유합니다.**  
> 작업 전에 항상 `git pull origin dev`로 최신 코드를 받아오세요.

---

## 단계별 할 일

### STEP 1 — 챕터 생성 AI 프롬프트 설계

챕터 생성의 품질은 프롬프트에 달려 있습니다. 먼저 프롬프트를 잘 짜는 것이 중요합니다.

**Codex에 붙여넣을 프롬프트:**
```
OpenAI gpt-4.1-mini를 사용해서 자서전 챕터를 생성하는 Python 함수를 만들어줘.

함수명: generate_chapter_content
입력:
- conversation_history: list[dict]  # [{"role": "user/assistant", "content": "..."}]
- chapter_type: str  # "childhood", "youth", "career", "love", "reflection" 중 하나
- user_name: str  # 사용자 이름

동작:
- 시스템 프롬프트: "당신은 한국의 베스트셀러 작가입니다. 
  인터뷰 내용을 바탕으로 따뜻하고 감동적인 자서전 챕터를 작성합니다.
  1인칭 시점으로 쓰고, 문학적 표현을 사용하며, 최소 600자 이상 써야 합니다."
- chapter_type에 따라 적절한 톤으로 챕터 작성
- 반환: { "title": str, "content": str }
```

**커밋:**
```bash
git commit -m "feat(chapter): 챕터 생성 AI 함수 구현"
```

---

### STEP 2 — 챕터 생성 API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI chapters.py에 아래 API를 만들어줘.
Supabase의 chapter_drafts 테이블을 사용해줘.
테이블 컬럼: id(uuid), user_id(uuid), session_id(uuid), title(text), content(text), chapter_type(text), version_no(int), created_at

POST /chapters/generate (인증 필요)
- 요청: { "session_id": UUID, "chapter_type": string }
- 처리:
  1. session_id로 해당 세션의 대화 기록 전체를 가져오기
  2. 대화 기록으로 generate_chapter_content 함수 호출
  3. 생성된 챕터를 chapter_drafts 테이블에 저장
  4. 저장된 챕터 정보 반환
- 생성에 실패하면 500 에러와 한국어 메시지 반환

GET /chapters (인증 필요)
- 내 챕터 목록 반환 (최신순)
- 쿼리 파라미터: session_id (선택)

GET /chapters/{chapter_id} (인증 필요)
- 챕터 상세 반환

PUT /chapters/{chapter_id} (인증 필요)
- 요청: { "title": string, "content": string }
- 챕터 내용 수정 후 반환
```

**완료 확인:**
```bash
# 챕터 생성 테스트 (토큰은 역할 C 테스트에서 받은 값 사용)
curl -X POST http://127.0.0.1:8000/chapters/generate \
  -H "Authorization: Bearer <토큰>" \
  -H "Content-Type: application/json" \
  -d '{"session_id":"<세션ID>","chapter_type":"childhood"}'
```

**커밋:**
```bash
git commit -m "feat(chapter): 챕터 CRUD API 구현"
```

---

### STEP 3 — 기억 추출 기능 만들기

인터뷰 중 사용자가 언급한 중요한 정보(이름, 날짜, 장소 등)를 추출해서 다음 인터뷰에 활용합니다.

**Codex에 붙여넣을 프롬프트:**
```
OpenAI gpt-4.1-mini를 사용해서 대화 내용에서 중요 정보를 추출하는 Python 함수를 만들어줘.

함수명: extract_memories
입력:
- conversation_history: list[dict]

동작:
- 시스템 프롬프트: "대화에서 중요한 사실을 추출하세요.
  추출 항목: 인물 이름, 장소, 날짜/연도, 중요한 사건, 감정"
- JSON 형식으로 반환:
  { "people": [], "places": [], "dates": [], "events": [], "emotions": [] }

그리고 FastAPI에 아래 API를 추가해줘.

GET /memory (인증 필요)
- 사용자의 저장된 기억 목록 반환
Supabase user_memories 테이블 사용
테이블 컬럼: id(uuid), user_id(uuid), session_id(uuid), memory_type(text), content(text), created_at
```

**커밋:**
```bash
git commit -m "feat(memory): 인터뷰 기억 추출 API 구현"
```

---

### STEP 4 — 책 편집(compile) API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI book.py에 아래 API를 만들어줘.
Supabase의 book_versions 테이블을 사용해줘.
테이블 컬럼: id(uuid), user_id(uuid), title(text), subtitle(text), chapters(jsonb), created_at

POST /book/compile (인증 필요)
- 요청: { "chapter_ids": [UUID, ...], "title": string }
- 처리:
  1. chapter_ids에 해당하는 챕터들을 순서대로 가져오기
  2. OpenAI gpt-4.1-mini로 책 소개글(subtitle) 자동 생성
     프롬프트: "이 챕터들의 제목을 보고 따뜻한 책 소개 한 문장을 써줘"
  3. book_versions 테이블에 저장
  4. 저장된 책 정보 반환

GET /book (인증 필요)
- 내 책 목록 반환

GET /book/{book_id} (인증 필요)
- 책 상세 반환 (챕터 내용 포함)
```

**커밋:**
```bash
git commit -m "feat(book): 책 편집 및 조회 API 구현"
```

---

### STEP 5 — 챕터 품질 개선 (프롬프트 튜닝)

API가 작동한 뒤 실제로 생성된 챕터를 읽어보고 품질을 높입니다.

**테스트 방법:**
1. 서버 실행 후 실제로 음성 인터뷰 3~5턴 진행
2. 챕터 생성 API 호출
3. 결과물을 읽어보기
4. 마음에 안 들면 Codex에게 프롬프트 개선 요청

**Codex에 붙여넣을 프롬프트 예시:**
```
현재 챕터 생성 프롬프트가 너무 딱딱하게 나와.
아래 생성 결과물을 보고 더 문학적이고 감동적으로 나오도록
시스템 프롬프트를 개선해줘.

[생성 결과물을 여기에 붙여넣기]
```

**커밋:**
```bash
git commit -m "refactor(chapter): 챕터 생성 프롬프트 품질 개선"
```

---

## 이 역할의 완료 기준

- [ ] 인터뷰 세션 ID를 넣으면 챕터가 자동 생성된다
- [ ] 생성된 챕터가 한국어로 600자 이상이다
- [ ] 챕터를 수정하고 저장할 수 있다
- [ ] 여러 챕터를 모아서 책으로 만들 수 있다
- [ ] 기억 추출이 제대로 동작한다

---

## Supabase 테이블 설계 (팀장과 공유)

```sql
-- 챕터 테이블
CREATE TABLE chapter_drafts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  session_id UUID REFERENCES interview_sessions(id),
  title TEXT,
  content TEXT,
  chapter_type TEXT,
  version_no INTEGER DEFAULT 1,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 책 테이블
CREATE TABLE book_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  title TEXT,
  subtitle TEXT,
  chapters JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 기억 테이블
CREATE TABLE user_memories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  session_id UUID REFERENCES interview_sessions(id),
  memory_type TEXT,
  content TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

---

## 참고 규칙

- 깃 커밋/브랜치 규칙: [GIT_CONVENTION.md](./GIT_CONVENTION.md)
- API 명세: `docs/API.md`
- 서버 실행: `uvicorn app.main:app --reload --port 8000`
- API 문서: `http://127.0.0.1:8000/docs`
