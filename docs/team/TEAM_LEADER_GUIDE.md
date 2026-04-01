# 팀장 가이드 — StoryVenue 프로젝트 총괄

> 이 문서는 팀장(본인)이 참고하는 전체 관리 가이드입니다.

---

## 팀 구성 및 역할 배정

| 파일 | 역할 | 담당자 | 핵심 책임 |
|------|------|--------|-----------|
| [ROLE_A](./ROLE_A_Android_UI_1.md) | Android UI A | \_\_\_\_ | 로그인 · 홈 · 음성 화면 |
| [ROLE_B](./ROLE_B_Android_UI_2.md) | Android UI B | \_\_\_\_ | 피드 · 채팅 · 책 화면 |
| [ROLE_C](./ROLE_C_Backend_Auth_Voice.md) | 백엔드 음성·인증 | \_\_\_\_ | JWT · 세션 · STT/TTS 파이프라인 |
| [ROLE_D](./ROLE_D_Backend_AI_Chapter.md) | 백엔드 AI·챕터 | \_\_\_\_ | 챕터 생성 · 책 편집 · 기억 추출 |
| [ROLE_E](./ROLE_E_Backend_Feed_Infra.md) | 백엔드 피드·인프라 | \_\_\_\_ | 피드 · 채팅 · Docker 관리 |

---

## 새 리포지토리 초기 세팅 (팀장이 먼저 해야 할 일)

### 1. GitHub에서 새 리포지토리 생성
- GitHub 접속 → New repository
- 이름: `storyvenue` (또는 팀이 정한 이름)
- Private 선택
- README 없이 생성 (나중에 직접 추가)

### 2. 브랜치 구조 초기화
```bash
git clone <새 리포 주소>
cd storyvenue

# main 브랜치에 초기 구조만 커밋
git commit --allow-empty -m "chore: 프로젝트 초기화"
git push origin main

# dev 브랜치 생성
git checkout -b dev
git push origin dev
```

### 3. 브랜치 보호 설정 (GitHub 설정)
- Settings → Branches → Add rule
- Branch name: `main`
- Require a pull request before merging ✅
- Require approvals: 1 ✅

### 4. 팀원 초대
- Settings → Collaborators → Add people
- 팀원 5명 GitHub 아이디 추가

### 5. Supabase 프로젝트 생성 및 테이블 만들기
- [supabase.com](https://supabase.com) 접속 → New project
- 아래 SQL을 Supabase SQL Editor에서 실행:

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

### 6. 팀원에게 전달할 .env 파일 만들기
```
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJxxx...
SUPABASE_SERVICE_ROLE_KEY=eyJxxx...
OPENAI_API_KEY=sk-xxx...
```
이 파일은 **절대 GitHub에 올리지 말고** 카카오톡이나 디스코드로 직접 전달

### 7. .gitignore 파일 만들기
```
# 환경 변수 (절대 올리면 안 됨)
.env
server/.env
*.env

# Python
__pycache__/
*.pyc
venv/
.venv/

# Android
*.iml
.gradle/
local.properties
.idea/
*.keystore
build/
app/build/

# Mac
.DS_Store
```

---

## 개발 일정 추천

| 주차 | 목표 |
|------|------|
| **1주차** | 환경 세팅 완료, 각자 STEP 1~2 완료 |
| **2주차** | 백엔드 핵심 API 완성 (역할 C, D, E의 STEP 3~4) |
| **3주차** | 프론트-백엔드 연동 (API 연결) |
| **4주차** | 통합 테스트 + 버그 수정 + 발표 준비 |

---

## 팀장이 주기적으로 확인할 것

### 매일
- GitHub에서 PR이 왔는지 확인
- PR 내용 검토 후 merge 또는 수정 요청

### 주 1~2회
- 팀원 전체 모여서 진행 상황 공유 (15~30분)
- 막힌 부분 같이 해결

### PR 리뷰 방법
```
코드를 완전히 이해 못해도 괜찮아요.
아래만 확인하면 됩니다:
1. 파일이 올바른 위치에 있는가?
2. 커밋 메시지가 규칙에 맞는가?
3. 같이 테스트해봤을 때 기능이 작동하는가?
```

---

## 긴급 상황 대처

### 서버가 갑자기 안 될 때
```bash
docker compose down
docker compose up -d --build
docker compose logs -f storyvenue-api
```

### 누가 잘못된 코드를 main에 올렸을 때
```bash
# 이전 커밋으로 되돌리기
git revert <잘못된 커밋 해시>
git push origin main
```

### 팀원이 git이 꼬였을 때
```bash
# 가장 안전한 방법: 해당 팀원의 브랜치를 삭제하고 다시 받기
git checkout dev
git pull origin dev
git checkout -b feature/역할/작업명
```

---

## 참고 문서

- [GIT_CONVENTION.md](./GIT_CONVENTION.md) — 전체 깃 규칙
- [역할 A](./ROLE_A_Android_UI_1.md) · [역할 B](./ROLE_B_Android_UI_2.md) · [역할 C](./ROLE_C_Backend_Auth_Voice.md) · [역할 D](./ROLE_D_Backend_AI_Chapter.md) · [역할 E](./ROLE_E_Backend_Feed_Infra.md)
- 아키텍처 다이어그램: [docs/architecture.svg](../architecture.svg)
