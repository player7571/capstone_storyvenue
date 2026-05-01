# StoryVenue API 명세서

> **Base URL**: `http://127.0.0.1:8000` (로컬) / `http://10.0.2.2:8000` (안드로이드 에뮬레이터)  
> **인증 방식**: `Authorization: Bearer <access_token>` 헤더  
> **공통 에러 응답**: `{ "detail": "에러 메시지 (한국어)" }`

---

## 목차

1. [인증 (Auth)](#1-인증-auth) — 역할 C
2. [프로필 (Users)](#2-프로필-users) — 역할 C
3. [인터뷰 세션 (Sessions)](#3-인터뷰-세션-sessions) — 역할 C
4. [음성 인터뷰 (Voice)](#4-음성-인터뷰-voice) — 역할 C
5. [대화 기록 (Messages)](#5-대화-기록-messages) — 역할 C
6. [챕터 (Chapters)](#6-챕터-chapters) — 역할 D
7. [책 (Book)](#7-책-book) — 역할 D
8. [기억 (Memory)](#8-기억-memory) — 역할 D
9. [피드 (Feed)](#9-피드-feed) — 역할 E
10. [댓글 (Comments)](#10-댓글-comments) — 역할 E
11. [좋아요 (Like)](#11-좋아요-like) — 역할 E
12. [알림 (Notifications)](#12-알림-notifications) — 역할 E
13. [채팅 (Chat)](#13-채팅-chat) — 역할 E
14. [안전 검사 (Safety)](#14-안전-검사-safety) — 역할 E
15. [헬스체크 (Health)](#15-헬스체크-health) — 역할 C

---

## 공통 사항

### 인증이 필요한 API

요청 헤더에 JWT 토큰을 포함해야 합니다.

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

토큰이 없거나 만료된 경우:
```json
// 401 Unauthorized
{ "detail": "인증이 필요합니다" }
```

### 공통 에러 코드

| 코드 | 의미 |
|------|------|
| 400 | 잘못된 요청 (필수 값 누락, 유효성 실패) |
| 401 | 인증 실패 (토큰 없음/만료) |
| 403 | 권한 없음 (본인 리소스가 아님) |
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 내부 오류 |

---

## 1. 인증 (Auth)

### `POST /auth/signup` — 회원가입

> 인증: 불필요

**요청**
```json
{
  "email": "user@example.com",
  "password": "test1234",
  "name": "홍길동"
}
```

**응답 — 200**
```json
{
  "message": "회원가입 성공"
}
```

**에러**
| 코드 | 상황 |
|------|------|
| 400 | 이미 존재하는 이메일 |
| 400 | 비밀번호 6자 미만 |

---

### `POST /auth/login` — 로그인

> 인증: 불필요

**요청**
```json
{
  "email": "user@example.com",
  "password": "test1234"
}
```

**응답 — 200**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "user_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**에러**
| 코드 | 상황 |
|------|------|
| 401 | 이메일 또는 비밀번호가 틀렸습니다 |

---

## 2. 프로필 (Users)

### `GET /users/me` — 내 프로필 조회

> 인증: 필요

**응답 — 200**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "홍길동",
  "email": "user@example.com",
  "notification_enabled": true,
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `PUT /users/me` — 프로필 수정

> 인증: 필요

**요청** (변경할 필드만 포함)
```json
{
  "name": "김철수",
  "notification_enabled": false
}
```

**응답 — 200**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "김철수",
  "email": "user@example.com",
  "notification_enabled": false,
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `DELETE /users/me` — 회원탈퇴

> 인증: 필요

**응답 — 200**
```json
{
  "message": "회원탈퇴가 완료되었습니다"
}
```

---

## 3. 인터뷰 세션 (Sessions)

### `POST /sessions` — 새 인터뷰 세션 생성

> 인증: 필요

**요청**
```json
{
  "title": "어린 시절 이야기",
  "theme": "childhood"
}
```

**응답 — 201**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "title": "어린 시절 이야기",
  "theme": "childhood",
  "status": "active",
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `GET /sessions` — 내 세션 목록 조회

> 인증: 필요

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "title": "어린 시절 이야기",
    "theme": "childhood",
    "status": "active",
    "created_at": "2026-04-01T12:00:00Z"
  }
]
```

---

### `GET /sessions/{session_id}` — 세션 상세 조회

> 인증: 필요

**응답 — 200**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "title": "어린 시절 이야기",
  "theme": "childhood",
  "status": "active",
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

## 4. 음성 인터뷰 (Voice)

### `POST /voice/turn` — 음성 한 턴 처리

> 인증: 필요  
> Content-Type: `multipart/form-data`

**요청**
| 필드 | 타입 | 설명 |
|------|------|------|
| `session_id` | UUID (Form) | 인터뷰 세션 ID |
| `audio_file` | File | 음성 파일 (wav/m4a) |

**응답 — 200**
```json
{
  "user_text": "어렸을 때 골목에서 많이 놀았어요",
  "assistant_text": "골목에서의 추억이 특별하셨군요. 어떤 놀이를 주로 하셨나요?",
  "audio_url": "https://storage.example.com/tts/abc123.mp3"
}
```

**처리 흐름**: 음성 → STT(Whisper) → AI 응답(GPT) → TTS → 응답

---

## 5. 대화 기록 (Messages)

### `GET /messages` — 세션 대화 기록 조회

> 인증: 필요

**쿼리 파라미터**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `session_id` | UUID | O | 조회할 세션 ID |

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "role": "user",
    "content": "어렸을 때 골목에서 많이 놀았어요",
    "created_at": "2026-04-01T12:00:00Z"
  },
  {
    "id": "uuid",
    "role": "assistant",
    "content": "골목에서의 추억이 특별하셨군요...",
    "created_at": "2026-04-01T12:00:05Z"
  }
]
```

---

## 6. 챕터 (Chapters)

### `POST /chapters/generate` — 챕터 자동 생성

> 인증: 필요

**요청**
```json
{
  "session_id": "uuid",
  "chapter_type": "childhood"
}
```

`chapter_type` 옵션: `"childhood"`, `"youth"`, `"career"`, `"love"`, `"reflection"`

**응답 — 201**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "session_id": "uuid",
  "title": "동네 골목의 추억",
  "content": "동네 골목에서 뛰어놀던 그 시절이 가장 행복했다...",
  "chapter_type": "childhood",
  "version_no": 1,
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `GET /chapters` — 내 챕터 목록 조회

> 인증: 필요

**쿼리 파라미터**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `session_id` | UUID | X | 특정 세션의 챕터만 필터링 |

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "title": "동네 골목의 추억",
    "chapter_type": "childhood",
    "version_no": 1,
    "created_at": "2026-04-01T12:00:00Z"
  }
]
```

---

### `GET /chapters/{chapter_id}` — 챕터 상세 조회

> 인증: 필요

**응답 — 200**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "session_id": "uuid",
  "title": "동네 골목의 추억",
  "content": "동네 골목에서 뛰어놀던 그 시절이 가장 행복했다...",
  "chapter_type": "childhood",
  "version_no": 1,
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `PUT /chapters/{chapter_id}` — 챕터 수정

> 인증: 필요

**요청**
```json
{
  "title": "수정된 제목",
  "content": "수정된 내용..."
}
```

**응답 — 200**: 수정된 챕터 전체 정보 반환 (상세 조회와 동일한 형식)

---

## 7. 책 (Book)

### `POST /book/compile` — 챕터들을 책으로 편집

> 인증: 필요

**요청**
```json
{
  "chapter_ids": ["uuid1", "uuid2", "uuid3"],
  "title": "나의 첫 번째 이야기"
}
```

**응답 — 201**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "title": "나의 첫 번째 이야기",
  "subtitle": "따뜻한 기억들로 엮은 한 편의 자서전",
  "chapters": [
    { "id": "uuid1", "title": "어린 시절", "content": "..." },
    { "id": "uuid2", "title": "첫 직장", "content": "..." },
    { "id": "uuid3", "title": "대학 시절", "content": "..." }
  ],
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `GET /book` — 내 책 목록 조회

> 인증: 필요

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "title": "나의 첫 번째 이야기",
    "subtitle": "따뜻한 기억들로 엮은 한 편의 자서전",
    "created_at": "2026-04-01T12:00:00Z"
  }
]
```

---

### `GET /book/{book_id}` — 책 상세 조회

> 인증: 필요

**응답 — 200**: 책 편집 응답과 동일한 형식 (챕터 내용 포함)

---

## 8. 기억 (Memory)

### `GET /memory` — 사용자 기억 정보 조회

> 인증: 필요

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "session_id": "uuid",
    "memory_type": "people",
    "content": "할머니 - 김순자",
    "created_at": "2026-04-01T12:00:00Z"
  },
  {
    "id": "uuid",
    "session_id": "uuid",
    "memory_type": "places",
    "content": "부산 해운대",
    "created_at": "2026-04-01T12:00:00Z"
  }
]
```

`memory_type` 종류: `"people"`, `"places"`, `"dates"`, `"events"`, `"emotions"`

---

## 9. 피드 (Feed)

### `GET /feed` — 피드 목록 조회

> 인증: 필요

**쿼리 파라미터**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `limit` | int | 20 | 가져올 개수 |
| `offset` | int | 0 | 건너뛸 개수 |
| `q` | string | 없음 | 제목, 미리보기, 작성자 이름 검색어 |

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "user_id": "uuid",
    "author_name": "김철수",
    "book_id": "uuid",
    "title": "나의 첫 번째 이야기",
    "preview": "동네 골목에서 뛰어놀던 그 시절이...",
    "like_count": 12,
    "comment_count": 3,
    "liked_by_me": false,
    "created_at": "2026-04-01T12:00:00Z"
  }
]
```

---

### `POST /feed` — 책을 피드에 게시

> 인증: 필요  
> 게시 전 자동으로 안전 검사 실행 (safe=false이면 400 에러)

**요청**
```json
{
  "book_id": "uuid",
  "title": "나의 첫 번째 이야기",
  "preview": "동네 골목에서 뛰어놀던 그 시절이..."
}
```

**응답 — 201**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "book_id": "uuid",
  "title": "나의 첫 번째 이야기",
  "preview": "동네 골목에서 뛰어놀던 그 시절이...",
  "like_count": 0,
  "created_at": "2026-04-01T12:00:00Z"
}
```

**에러**
| 코드 | 상황 |
|------|------|
| 400 | 안전 검사 실패: `{ "detail": "부적절한 내용이 포함되어 있습니다: {이유}" }` |

---

### `GET /feed/{post_id}` — 피드 상세 조회

> 인증: 필요

**응답 — 200**
```json
{
  "id": "uuid",
  "user_id": "uuid",
  "author_name": "김철수",
  "book_id": "uuid",
  "title": "나의 첫 번째 이야기",
  "preview": "동네 골목에서 뛰어놀던 그 시절이...",
  "like_count": 12,
  "comment_count": 3,
  "liked_by_me": true,
  "book": {
    "title": "나의 첫 번째 이야기",
    "subtitle": "따뜻한 기억들로 엮은 한 편의 자서전",
    "chapters": [
      { "title": "어린 시절", "content": "..." }
    ]
  },
  "created_at": "2026-04-01T12:00:00Z"
}
```

---

### `POST /feed/{post_id}/read` — 읽음 처리

> 인증: 필요

**응답 — 204 No Content**

---

## 10. 댓글 (Comments)

### `GET /feed/{post_id}/comments` — 댓글 목록 조회

> 인증: 필요

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "post_id": "uuid",
    "user_id": "uuid",
    "author_name": "이영희",
    "content": "정말 감동적인 이야기네요!",
    "created_at": "2026-04-01T13:00:00Z"
  },
  {
    "id": "uuid",
    "post_id": "uuid",
    "user_id": "uuid",
    "author_name": "박지민",
    "content": "다음 챕터도 기대됩니다",
    "created_at": "2026-04-01T13:30:00Z"
  }
]
```

---

### `POST /feed/{post_id}/comments` — 댓글 작성

> 인증: 필요  
> 게시물 작성자에게 알림 자동 생성 (본인 글에 본인이 댓글 달면 제외)

**요청**
```json
{
  "content": "정말 감동적인 이야기네요!"
}
```

**응답 — 201**
```json
{
  "id": "uuid",
  "post_id": "uuid",
  "user_id": "uuid",
  "author_name": "이영희",
  "content": "정말 감동적인 이야기네요!",
  "created_at": "2026-04-01T13:00:00Z"
}
```

---

### `DELETE /comments/{comment_id}` — 댓글 삭제

> 인증: 필요 (본인 댓글만 삭제 가능)

**응답 — 204 No Content**

**에러**
| 코드 | 상황 |
|------|------|
| 403 | 본인이 작성한 댓글이 아닙니다 |
| 404 | 댓글을 찾을 수 없습니다 |

---

## 11. 좋아요 (Like)

### `POST /feed/{post_id}/like` — 좋아요 토글

> 인증: 필요  
> 좋아요 시 게시물 작성자에게 알림 자동 생성 (본인 글이면 제외, 취소 시 알림 없음)

**응답 — 200**
```json
{
  "liked": true,
  "like_count": 13
}
```

---

## 12. 알림 (Notifications)

### `GET /notifications` — 알림 목록 조회

> 인증: 필요

**쿼리 파라미터**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `limit` | int | 30 | 가져올 개수 |
| `offset` | int | 0 | 건너뛸 개수 |

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "type": "comment",
    "actor_name": "이영희",
    "post_id": "uuid",
    "message": "회원님의 글에 댓글을 남겼습니다",
    "comment_preview": "정말 감동적인 이야기네요!",
    "is_read": false,
    "created_at": "2026-04-01T13:00:00Z"
  },
  {
    "id": "uuid",
    "type": "like",
    "actor_name": "박지민",
    "post_id": "uuid",
    "message": "회원님의 글을 좋아합니다",
    "comment_preview": null,
    "is_read": true,
    "created_at": "2026-04-01T12:30:00Z"
  }
]
```

`type` 종류: `"comment"` (댓글), `"like"` (좋아요)

---

### `GET /notifications/unread-count` — 읽지 않은 알림 수

> 인증: 필요

**응답 — 200**
```json
{
  "count": 3
}
```

---

### `PUT /notifications/{notification_id}/read` — 알림 읽음 처리

> 인증: 필요

**응답 — 200**
```json
{
  "message": "읽음 처리 완료"
}
```

---

## 13. 채팅 (Chat)

### `GET /chat` — 채팅 상대 목록

> 인증: 필요

**응답 — 200**
```json
[
  {
    "user_id": "uuid",
    "user_name": "김철수",
    "last_message": "안녕하세요! 글 잘 읽었어요",
    "last_message_at": "2026-04-01T15:42:00Z",
    "unread_count": 2
  }
]
```

---

### `GET /chat/{other_user_id}/messages` — 채팅 메시지 목록

> 인증: 필요  
> 호출 시 상대방 메시지 자동 읽음 처리

**쿼리 파라미터**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `limit` | int | 50 | 가져올 개수 |

**응답 — 200**
```json
[
  {
    "id": "uuid",
    "sender_id": "uuid",
    "receiver_id": "uuid",
    "content": "안녕하세요! 글 잘 읽었어요",
    "is_read": true,
    "created_at": "2026-04-01T15:42:00Z"
  }
]
```

---

### `POST /chat/{other_user_id}/messages` — 채팅 메시지 전송

> 인증: 필요

**요청**
```json
{
  "content": "감사합니다! 다음 챕터도 기대해주세요"
}
```

**응답 — 201**
```json
{
  "id": "uuid",
  "sender_id": "uuid",
  "receiver_id": "uuid",
  "content": "감사합니다! 다음 챕터도 기대해주세요",
  "is_read": false,
  "created_at": "2026-04-01T15:43:00Z"
}
```

---

## 14. 안전 검사 (Safety)

### `POST /safety/check` — 콘텐츠 안전 검사

> 인증: 필요

**요청**
```json
{
  "content": "검사할 텍스트 내용"
}
```

**응답 — 200**
```json
{
  "safe": true,
  "reason": "문제가 없습니다"
}
```

```json
{
  "safe": false,
  "reason": "혐오 표현이 포함되어 있습니다"
}
```

---

## 15. 헬스체크 (Health)

### `GET /health` — 서버 상태 확인

> 인증: 불필요

**응답 — 200**
```json
{
  "status": "ok"
}
```

---

## API별 담당 역할 요약

| 도메인 | API 경로 | 담당 |
|--------|---------|------|
| 인증 | `/auth/*` | 역할 C |
| 프로필 | `/users/me` | 역할 C |
| 세션 | `/sessions/*` | 역할 C |
| 음성 | `/voice/*` | 역할 C |
| 대화 기록 | `/messages` | 역할 C |
| 챕터 | `/chapters/*` | 역할 D |
| 책 | `/book/*` | 역할 D |
| 기억 | `/memory` | 역할 D |
| 피드 | `/feed/*` | 역할 E |
| 댓글 | `/feed/*/comments`, `/comments/*` | 역할 E |
| 알림 | `/notifications/*` | 역할 E |
| 채팅 | `/chat/*` | 역할 E |
| 안전 검사 | `/safety/*` | 역할 E |
| 헬스체크 | `/health` | 역할 C |
