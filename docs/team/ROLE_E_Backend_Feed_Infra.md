# 역할 E — 백엔드: 피드 · 댓글 · 채팅 · 알림 · 인프라 관리

> **담당자**: \_\_\_\_\_\_\_\_  
> **브랜치 접두사**: `feature/be-infra/`  
> **핵심 기술**: Python · FastAPI · Supabase · Docker

---

## 내 역할 한 줄 요약

사용자들이 서로 연결되는 **피드(게시판), 댓글, 채팅, 알림** 기능을 서버에서 처리하고,  
팀 전체가 쓰는 **서버 환경(Docker)을 관리**하는 역할입니다.  
팀원 5명 모두의 컴퓨터에서 서버가 잘 돌아가게 만드는 것도 이 역할의 책임입니다.

---

## 내가 만들 API 목록

| API | 설명 |
|-----|------|
| `GET /feed` | 피드 목록 조회 (최신순) |
| `POST /feed` | 책을 피드에 게시 |
| `GET /feed/{post_id}` | 피드 상세 조회 |
| `POST /feed/{post_id}/like` | 좋아요 토글 |
| `POST /feed/{post_id}/read` | 읽음 처리 |
| `GET /feed/{post_id}/comments` | 댓글 목록 조회 **(신규)** |
| `POST /feed/{post_id}/comments` | 댓글 작성 **(신규)** |
| `DELETE /comments/{comment_id}` | 댓글 삭제 **(신규)** |
| `GET /notifications` | 알림 목록 조회 **(신규)** |
| `PUT /notifications/{id}/read` | 알림 읽음 처리 **(신규)** |
| `GET /notifications/unread-count` | 읽지 않은 알림 수 **(신규)** |
| `GET /chat` | 채팅 상대 목록 |
| `GET /chat/{user_id}/messages` | 채팅 메시지 목록 |
| `POST /chat/{user_id}/messages` | 채팅 메시지 전송 |
| `POST /safety/check` | 콘텐츠 안전 검사 |

---

## 개발 환경 세팅 (처음 1회만)

역할 C, D와 같은 서버 프로젝트를 공유합니다.

### 1. Docker Desktop 설치 (필수!)
- 구글에서 "Docker Desktop 다운로드" 검색 후 설치
- 설치 후 Docker Desktop 실행 → "Engine running" 확인

### 2. Python 가상환경 활성화
```bash
cd <프로젝트 폴더>/server
source venv/bin/activate        # Mac
venv\Scripts\activate           # Windows
```

### 3. 서버 실행 확인 (두 가지 방법)

**방법 1 — Python 직접 실행 (개발용)**
```bash
uvicorn app.main:app --reload --port 8000
```

**방법 2 — Docker로 실행 (배포용)**
```bash
cd <프로젝트 루트>
docker compose up -d --build
```

> **역할 C, D와 같은 서버 프로젝트를 공유합니다.**  
> 작업 전에 항상 `git pull origin dev`로 최신 코드를 받아오세요.

---

## 단계별 할 일

### STEP 1 — Docker 환경 세팅 (최우선!)

팀원 모두가 Docker로 서버를 실행할 수 있어야 합니다. 이게 제일 먼저입니다.

**Codex에 붙여넣을 프롬프트:**
```
FastAPI 프로젝트를 Docker로 실행하기 위한 파일을 만들어줘.

필요한 파일:
1. server/Dockerfile
   - Python 3.11 slim 이미지 사용
   - pip install -r requirements.txt
   - uvicorn으로 포트 8000에서 실행

2. server/requirements.txt
   - fastapi, uvicorn, supabase, openai, python-multipart, pydantic-settings, python-dotenv

3. docker-compose.yml (프로젝트 루트에)
   - 서비스 이름: storyvenue-api
   - .env 파일을 env_file로 연결
   - 포트: 8000:8000
   - restart: unless-stopped
```

**완료 확인:**
```bash
docker compose up -d --build
curl http://127.0.0.1:8000/health
# {"status":"ok"} 나오면 성공
```

**커밋:**
```bash
git commit -m "chore(infra): Docker 및 docker-compose 설정 추가"
```

---

### STEP 2 — 팀원 환경 세팅 문서 작성

**Codex에 붙여넣을 프롬프트:**
```
팀원들이 처음 프로젝트를 받았을 때 서버를 실행하는 방법을
쉬운 한국어로 README.md에 정리해줘.

포함할 내용:
1. 준비물 (Docker Desktop, Android Studio)
2. 서버 켜는 법 (docker compose up -d --build)
3. 서버 확인법 (curl http://127.0.0.1:8000/health)
4. 에뮬레이터에서 앱 연결하는 법 (http://10.0.2.2:8000)
5. 실기기에서 앱 연결하는 법 (로컬 IP 사용)
6. 자주 쓰는 Docker 명령어
7. 자주 생기는 문제와 해결법
```

**커밋:**
```bash
git commit -m "docs: 팀원 서버 실행 가이드 README 작성"
```

---

### STEP 3 — 피드 API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI feed.py에 아래 API를 만들어줘.
Supabase의 feed_posts 테이블을 사용해줘.
테이블 컬럼: id(uuid), user_id(uuid), book_id(uuid), title(text), preview(text), like_count(int), created_at

GET /feed (인증 필요)
- 쿼리 파라미터: limit(기본 20), offset(기본 0)
- 전체 피드 목록 최신순 반환
- 각 게시물에 작성자 이름도 포함해줘

POST /feed (인증 필요)
- 요청: { "book_id": UUID, "title": string, "preview": string }
- 새 피드 게시물 생성

GET /feed/{post_id} (인증 필요)
- 피드 상세 반환 (책 내용 포함)

POST /feed/{post_id}/like (인증 필요)
- 좋아요 토글: 이미 좋아요면 취소, 아니면 추가
- like_count 업데이트
- 반환: { "liked": bool, "like_count": int }

POST /feed/{post_id}/read (인증 필요)
- 읽음 처리 (204 No Content 반환)
```

**커밋:**
```bash
git commit -m "feat(feed): 피드 CRUD 및 좋아요 API 구현"
```

---

### STEP 4 — 채팅 API 만들기

**Codex에 붙여넣을 프롬프트:**
```
FastAPI chat.py에 아래 API를 만들어줘.
Supabase의 chat_messages 테이블을 사용해줘.
테이블 컬럼: id(uuid), sender_id(uuid), receiver_id(uuid), content(text), is_read(bool), created_at

GET /chat (인증 필요)
- 나와 대화한 사람 목록 반환
- 각 상대방의 마지막 메시지와 안 읽은 메시지 수 포함

GET /chat/{other_user_id}/messages (인증 필요)
- 특정 사람과의 채팅 기록 반환 (시간순)
- 쿼리 파라미터: limit(기본 50)

POST /chat/{other_user_id}/messages (인증 필요)
- 요청: { "content": string }
- 메시지 전송 후 저장된 메시지 반환

읽음 처리:
- GET /chat/{other_user_id}/messages 호출 시 자동으로 읽음 처리
```

**커밋:**
```bash
git commit -m "feat(chat): 채팅 메시지 API 구현"
```

---

### STEP 5 — 안전 검사 API 만들기

피드에 올라가는 내용이 부적절하지 않은지 AI로 검사합니다.

**Codex에 붙여넣을 프롬프트:**
```
FastAPI safety.py에 콘텐츠 안전 검사 API를 만들어줘.

POST /safety/check (인증 필요)
- 요청: { "content": string }
- 처리: OpenAI gpt-4.1-mini로 아래 판단
  시스템 프롬프트: "주어진 텍스트가 혐오표현, 폭력, 성인 콘텐츠를 포함하는지 판단하세요.
  반드시 JSON으로 반환: {'safe': true/false, 'reason': '이유'}"
- 반환: { "safe": bool, "reason": string }
- safe가 false면 피드 게시를 막는 용도로 사용

피드 게시(POST /feed)할 때 자동으로 안전 검사를 먼저 실행하고,
safe가 false면 400 에러와 함께 이유를 반환해줘.
```

**커밋:**
```bash
git commit -m "feat(safety): 콘텐츠 안전 검사 API 구현 및 피드 연동"
```

---

### STEP 6 — 댓글 API 만들기 (신규)

**Codex에 붙여넣을 프롬프트:**
```
FastAPI comments.py에 아래 API를 만들어줘.
Supabase의 feed_comments 테이블을 사용해줘.
테이블 컬럼: id(uuid), post_id(uuid), user_id(uuid), content(text), created_at

GET /feed/{post_id}/comments (인증 필요)
- 해당 게시물의 댓글 목록 반환 (시간순)
- 각 댓글에 작성자 이름도 포함해줘

POST /feed/{post_id}/comments (인증 필요)
- 요청: { "content": string }
- 댓글 저장 후 반환
- 댓글 작성 시 게시물 작성자에게 알림 자동 생성 (본인 글에 본인이 댓글 달면 알림 제외)

DELETE /comments/{comment_id} (인증 필요)
- 본인이 작성한 댓글만 삭제 가능
- 다른 사람 댓글이면 403 에러
```

**커밋:**
```bash
git commit -m "feat(comment): 댓글 CRUD API 구현"
```

---

### STEP 7 — 알림 API 만들기 (신규)

**Codex에 붙여넣을 프롬프트:**
```
FastAPI notifications.py에 아래 API를 만들어줘.
Supabase의 notifications 테이블을 사용해줘.
테이블 컬럼: id(uuid), user_id(uuid), type(text), actor_id(uuid), post_id(uuid), comment_id(uuid nullable), message(text), is_read(bool), created_at

알림 type 종류: "comment" (댓글), "like" (좋아요)

GET /notifications (인증 필요)
- 내 알림 목록 반환 (최신순)
- 쿼리 파라미터: limit(기본 30), offset(기본 0)
- 각 알림에 actor(알림 발생시킨 사람)의 이름도 포함해줘
- 반환: [{ "id", "type", "actor_name", "post_id", "message", "is_read", "created_at" }]

GET /notifications/unread-count (인증 필요)
- 읽지 않은 알림 수 반환
- 반환: { "count": int }

PUT /notifications/{notification_id}/read (인증 필요)
- 해당 알림을 읽음 처리
- 반환: { "message": "읽음 처리 완료" }

좋아요(POST /feed/{post_id}/like) API에도 알림 생성 로직을 추가해줘:
- 좋아요를 누를 때 게시물 작성자에게 알림 생성 (본인 글에 본인이 좋아요하면 알림 제외)
- 좋아요 취소 시에는 알림 생성하지 않음
```

**커밋:**
```bash
git commit -m "feat(notification): 알림 API 구현 및 댓글/좋아요 알림 연동"
```

---

### STEP 8 — 서버 배포 (EC2 또는 로컬 공유)

팀원들이 실기기로 테스트할 수 있도록 서버를 외부에서 접근 가능하게 만듭니다.

**방법 A — 로컬 IP 공유 (간단)**  
같은 와이파이 네트워크라면 내 컴퓨터 IP를 팀원들에게 알려주면 됩니다.
```bash
# Mac에서 내 IP 확인
ipconfig getifaddr en0
# 예: 192.168.0.15
# 팀원들은 http://192.168.0.15:8000 으로 접속
```

**방법 B — ngrok으로 임시 공개 URL 만들기**
```bash
# ngrok 설치 후
ngrok http 8000
# https://xxxx.ngrok.io 같은 주소가 생성됨
# 이 주소를 팀원들에게 공유
```

**커밋:**
```bash
git commit -m "docs(infra): 서버 공유 방법 문서 추가"
```

---

### STEP 9 — 모니터링 (서버 로그 확인)

팀원들이 앱을 쓸 때 서버에서 에러가 나면 이 역할이 가장 먼저 확인합니다.

```bash
# Docker로 실행 중일 때 실시간 로그 보기
docker compose logs -f storyvenue-api

# Python으로 실행 중일 때는 터미널에 바로 출력됨
```

에러가 나면:
1. 에러 메시지를 복사
2. Codex에 "이 에러가 왜 나는지, 어떻게 고치는지 알려줘" 붙여넣기

---

## 이 역할의 완료 기준

- [ ] Docker로 서버가 실행된다
- [ ] 팀원 5명 모두 서버에 접속할 수 있다
- [ ] 피드에 게시물을 올리고 볼 수 있다
- [ ] 좋아요 기능이 작동한다
- [ ] 피드 상세에서 댓글을 작성하고 조회할 수 있다 **(신규)**
- [ ] 댓글/좋아요 시 게시물 작성자에게 알림이 생성된다 **(신규)**
- [ ] 알림 목록을 조회하고 읽음 처리할 수 있다 **(신규)**
- [ ] 채팅 메시지를 주고받을 수 있다
- [ ] 부적절한 내용은 피드에 올라가지 않는다

---

## Supabase 테이블 설계 (팀장과 공유)

```sql
-- 피드 테이블
CREATE TABLE feed_posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  book_id UUID REFERENCES book_versions(id),
  title TEXT,
  preview TEXT,
  like_count INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 좋아요 테이블
CREATE TABLE feed_likes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  post_id UUID REFERENCES feed_posts(id),
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE(user_id, post_id)
);

-- 댓글 테이블 (신규)
CREATE TABLE feed_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id UUID REFERENCES feed_posts(id) ON DELETE CASCADE,
  user_id UUID REFERENCES auth.users(id),
  content TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 알림 테이블 (신규)
CREATE TABLE notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES auth.users(id),
  type TEXT CHECK (type IN ('comment', 'like')),
  actor_id UUID REFERENCES auth.users(id),
  post_id UUID REFERENCES feed_posts(id),
  comment_id UUID REFERENCES feed_comments(id),
  message TEXT,
  is_read BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- 채팅 테이블
CREATE TABLE chat_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sender_id UUID REFERENCES auth.users(id),
  receiver_id UUID REFERENCES auth.users(id),
  content TEXT,
  is_read BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

---

## 참고 규칙

- 깃 커밋/브랜치 규칙: [GIT_CONVENTION.md](./GIT_CONVENTION.md)
- API 명세: `docs/API.md`
- 서버 실행: `docker compose up -d --build`
- 서버 로그: `docker compose logs -f storyvenue-api`
- API 문서: `http://127.0.0.1:8000/docs`
