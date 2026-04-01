# 사진 기반 인터뷰 기능 구현 계획

> **담당 역할**: 역할 C 또는 D (백엔드 세션/AI 담당)  
> **브랜치**: `feature/be-auth/photo-interview` 또는 `feature/be-ai/photo-interview`  
> **상태**: 미착수

---

## 기능 설명

사용자가 **추억이 담긴 사진을 업로드**하면:
1. AI가 사진을 분석하여 어떤 내용인지 설명
2. 사진과 관련된 질문을 하며 대화 진행
3. 대화 내용이 세션에 저장
4. 저장된 대화를 바탕으로 자서전 챕터 생성에 활용

```
사진 업로드 → AI 비전 분석 → 질문/대화 → 저장 → 챕터 생성에 활용
```

---

## 핵심 설계

**기존 파이프라인을 그대로 재사용:**
- 사진 분석 결과 → `session_messages`에 assistant 메시지로 저장
- 이후 대화 → 같은 `session_messages` 테이블에 저장
- 챕터 생성 시 `_load_conversation_history(session_id)` → 사진 대화도 자동 포함
- **`generate_chapter_content()`, `extract_memories()` 수정 없음**

---

## API 설계

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/sessions/photo` | 사진 업로드 → 세션 생성 → AI 분석 + 첫 질문 반환 |
| `POST` | `/sessions/{id}/reply` | 사용자 텍스트 답변 → AI 후속 질문 반환 |
| `POST` | `/sessions/{id}/end` | 세션 종료 (선택: 기억 추출) |

### POST /sessions/photo

**요청**: `multipart/form-data` (이미지 파일, JPEG/PNG, 최대 5MB)

**처리 흐름:**
1. UploadFile로 이미지 수신
2. `interview_sessions` 테이블에 세션 생성 (`session_type="photo"`)
3. Supabase Storage `interview-photos` 버킷에 업로드
4. 세션 row에 `photo_url` 업데이트
5. GPT-4o-mini 비전 API로 사진 분석 → 설명 + 첫 질문 생성
6. `session_messages`에 assistant 메시지 저장
7. 응답 반환

**응답 예시:**
```json
{
  "session_id": "uuid",
  "photo_url": "https://...",
  "ai_message": "이 사진에는 해변가에서 가족이 함께 찍은 것으로 보이는 모습이 담겨 있네요. 뒤쪽에 보이는 바다와 모래사장이 인상적입니다. 이 사진은 언제, 어디서 찍으신 건가요?",
  "created_at": "2026-04-01T..."
}
```

### POST /sessions/{id}/reply

**요청:**
```json
{ "content": "이건 2005년 여름에 부산 해운대에서 찍은 사진이에요. 가족 여행이었죠." }
```

**처리 흐름:**
1. 세션 소유자 검증
2. user 메시지 `session_messages`에 저장
3. 전체 대화 이력 로드
4. GPT-4o-mini로 후속 질문 생성 (텍스트 전용, 비전 불필요)
5. assistant 메시지 저장
6. 응답 반환

**응답 예시:**
```json
{
  "user_message_id": "uuid",
  "ai_message": "2005년 부산 해운대라니, 정말 좋은 추억이겠네요! 그때 가족 여행에서 가장 기억에 남는 순간은 무엇이었나요?",
  "ai_message_id": "uuid"
}
```

### POST /sessions/{id}/end

세션을 종료하고, 원하면 기억 추출(`extract_memories`)을 실행합니다.  
이후 `POST /chapters/generate`에 이 세션의 `session_id`를 전달하면 사진 대화 기반 자서전 챕터가 생성됩니다.

---

## 구현할 파일 목록

| 파일 | 작업 | 설명 |
|------|------|------|
| `server/app/api/schemas/sessions.py` | **새로 생성** | 요청/응답 스키마 |
| `server/app/services/photo_interview.py` | **새로 생성** | 비전 분석 + 후속 질문 AI 서비스 |
| `server/app/api/routes/sessions.py` | **수정** (현재 빈 스텁) | 3개 엔드포인트 구현 |

**기존 파일 수정 없음**: chapters.py, chapter_generation.py, memory_extraction.py, main.py

---

## DB 변경 사항 (Supabase 대시보드에서 실행)

```sql
-- interview_sessions 테이블에 컬럼 추가
ALTER TABLE interview_sessions
  ADD COLUMN IF NOT EXISTS photo_url text,
  ADD COLUMN IF NOT EXISTS session_type text DEFAULT 'voice';
```

- Supabase Storage에 `interview-photos` 버킷 생성 (비공개)

---

## 참고할 기존 코드

| 파일 | 참고 내용 |
|------|-----------|
| `server/app/services/chapter_generation.py` | OpenAI 클라이언트 패턴 (`_get_openai_client()`) |
| `server/app/api/routes/chapters.py` | 세션 검증, 대화 이력 로드 패턴 |
| `server/app/services/memory_extraction.py` | 기억 추출 함수 호출 방법 |

---

## 비전 API 호출 예시 (OpenAI SDK 1.78.1)

```python
from openai import OpenAI

client = OpenAI(api_key="...")

response = client.responses.create(
    model="gpt-4o-mini",
    instructions="당신은 자서전 프로젝트의 따뜻한 인터뷰어입니다. 사진을 보고 한국어로 설명한 뒤, 이 기억에 대해 열린 질문을 해주세요.",
    input=[
        {
            "role": "user",
            "content": [
                {
                    "type": "input_image",
                    "image_url": f"data:{mime_type};base64,{image_base64}",
                },
                {
                    "type": "input_text",
                    "text": "이 사진에 대해 설명해주시고, 이 기억에 대해 질문해주세요.",
                },
            ],
        }
    ],
)
```

---

## 검증 방법

1. 서버 실행 후 Swagger UI(`/docs`)에서:
   - `POST /sessions/photo` — 사진 업로드 → session_id, ai_message 확인
   - `POST /sessions/{id}/reply` — 답변 → AI 후속 질문 확인
   - `POST /sessions/{id}/end` — 세션 종료 확인
   - `POST /chapters/generate` — 해당 session_id로 챕터 생성 → 사진 내용 반영 확인
