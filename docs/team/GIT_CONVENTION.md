# 깃 커밋 & 브랜치 규칙

> 이 규칙은 5명 전원이 따라야 합니다.  
> 규칙을 지키면 나중에 코드가 뒤섞여도 누가 뭘 했는지 바로 알 수 있어요.

---

## 브랜치 구조

```
main              ← 완성된 코드만 올라옴 (팀장 관리)
├── dev           ← 팀원들이 작업을 합치는 곳
│   ├── feature/ui-a/로그인화면
│   ├── feature/ui-b/피드화면
│   ├── feature/be-auth/로그인API
│   ├── feature/be-ai/챕터생성
│   └── feature/be-infra/피드API
```

### 브랜치 이름 규칙

```
feature/<내역할>/<작업내용>
```

| 역할 | 브랜치 접두사 |
|------|--------------|
| UI 담당 A | `feature/ui-a/` |
| UI 담당 B | `feature/ui-b/` |
| 백엔드 음성·인증 | `feature/be-auth/` |
| 백엔드 AI·챕터 | `feature/be-ai/` |
| 백엔드 피드·인프라 | `feature/be-infra/` |

**예시**
```bash
git checkout -b feature/ui-a/login-screen
git checkout -b feature/be-ai/chapter-generation
```

---

## 커밋 메시지 규칙

### 형식

```
<타입>(<범위>): <한 줄 설명>
```

### 타입 목록

| 타입 | 언제 쓰나 | 예시 |
|------|-----------|------|
| `feat` | 새 기능을 만들었을 때 | `feat(auth): 로그인 화면 구현` |
| `fix` | 버그를 고쳤을 때 | `fix(voice): 마이크 권한 오류 수정` |
| `ui` | 화면 디자인만 바꿨을 때 | `ui(feed): 카드 색상 변경` |
| `docs` | 문서(md 파일)를 수정했을 때 | `docs: README 업데이트` |
| `chore` | 설정, 패키지 등 기타 작업 | `chore: 의존성 추가` |
| `refactor` | 기능 변경 없이 코드를 정리했을 때 | `refactor(chapter): 서비스 분리` |

### 범위(scope) 목록

```
auth      로그인/회원가입
session   인터뷰 세션
voice     음성 녹음/재생
chapter   챕터 생성
book      책 편집/저장
feed      피드
chat      채팅
memory    기억 추출
safety    안전 검사
infra     Docker/서버 설정
```

### 좋은 커밋 예시

```bash
feat(auth): 회원가입 API 연동
fix(voice): 음성 파일 업로드 실패 오류 수정
ui(home): 홈 화면 버튼 크기 조정
feat(chapter): GPT-4.1-mini 챕터 생성 연동
docs: 팀 역할 분담 문서 추가
chore: FastAPI 초기 프로젝트 구조 생성
```

### 나쁜 커밋 예시 (이렇게 하지 마세요)

```bash
수정함           ← 뭘 수정했는지 모름
asdf             ← 의미 없음
일단 올림        ← 안 됨
```

---

## PR(Pull Request) 규칙

1. `dev` 브랜치로만 PR을 보냅니다 (`main`에 직접 올리지 않음)
2. PR 제목은 커밋 규칙과 동일하게 작성
3. PR 설명에 **무엇을 만들었는지** 한 줄 이상 적기
4. 팀장이 확인 후 merge

---

## 처음 작업 시작할 때 순서

```bash
# 1. 최신 dev 브랜치를 받아오기
git checkout dev
git pull origin dev

# 2. 내 작업 브랜치 만들기
git checkout -b feature/ui-a/login-screen

# 3. 작업하기 (파일 수정)

# 4. 변경 내용 저장
git add .
git commit -m "feat(auth): 로그인 화면 기본 레이아웃 구현"

# 5. 내 브랜치를 원격에 올리기
git push origin feature/ui-a/login-screen

# 6. GitHub에서 PR 생성
```

---

## 자주 쓰는 깃 명령어 (복사해서 쓰세요)

| 상황 | 명령어 |
|------|--------|
| 현재 상태 확인 | `git status` |
| 변경 내용 저장 | `git add . && git commit -m "메시지"` |
| 원격에 올리기 | `git push origin 브랜치이름` |
| 최신 코드 받기 | `git pull origin dev` |
| 브랜치 목록 보기 | `git branch` |
| 브랜치 이동 | `git checkout 브랜치이름` |
