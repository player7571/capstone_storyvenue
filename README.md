# StoryVenue

> 음성 인터뷰를 통해 자서전을 AI로 자동 생성하는 앱

## 프로젝트 구조

```
capstone_storyvenue/
├── app/                    # Android 앱 (Kotlin · Jetpack Compose)
├── server/                 # 백엔드 서버 (Python · FastAPI)
│   └── app/
│       ├── api/routes/     # API 엔드포인트
│       ├── api/schemas/    # 요청/응답 스키마
│       ├── core/           # 설정
│       ├── db/             # Supabase 클라이언트
│       └── services/       # 비즈니스 로직
├── docs/                   # 문서
│   └── team/               # 역할별 가이드
└── docker-compose.yml      # 서버 실행용
```

## 시스템 아키텍처

```
[Android 앱]  ──HTTP/REST──▶  [FastAPI :8000]  ──DB/Auth──▶  [Supabase]
      │                              │                        PostgreSQL
      └──Auth 직접──────────────────▶│                        JWT 발급
                                     └──AI API──────────────▶ [OpenAI]
                                                              STT/TTS/GPT
```

## 기술 스택

| 구분 | 기술 |
|------|------|
| Android | Kotlin · Jetpack Compose · OkHttp |
| 서버 | Python · FastAPI · Uvicorn |
| 데이터베이스 | Supabase (PostgreSQL + Auth) |
| AI | OpenAI GPT-4.1-mini · gpt-4o-transcribe · gpt-4o-mini-tts |
| 배포 | Docker · Docker Compose |

## 시작하기

### 1. 준비물

- Docker Desktop
- Android Studio

### 2. 환경 변수 설정

팀장에게 `server/.env` 파일을 전달받으세요.

### 3. 서버 실행

```bash
docker compose up -d --build
curl http://127.0.0.1:8000/health
# {"status":"ok"} 가 나오면 성공
```

### 4. 앱 실행

1. Android Studio로 `app/` 폴더 열기
2. 에뮬레이터 생성 (Pixel 6 · Android 14)
3. Run 버튼 클릭

### 5. 서버 주소

| 환경 | 주소 |
|------|------|
| 에뮬레이터 | `http://10.0.2.2:8000` |
| 실기기 | `http://<내 PC IP>:8000` |

PC IP 확인: `ipconfig getifaddr en0` (Mac) / `ipconfig` (Windows)

### 6. 자주 쓰는 명령어

```bash
docker compose up -d --build   # 서버 켜기
docker compose ps              # 서버 상태
docker compose logs -f         # 서버 로그
docker compose down            # 서버 끄기
```

## 팀 역할

| 역할 | 담당 | 가이드 |
|------|------|--------|
| UI A | 로그인·홈·음성 화면 | [ROLE_A](docs/team/ROLE_A_Android_UI_1.md) |
| UI B | 피드·채팅·책 화면 | [ROLE_B](docs/team/ROLE_B_Android_UI_2.md) |
| BE 인증·음성 | JWT·세션·STT/TTS | [ROLE_C](docs/team/ROLE_C_Backend_Auth_Voice.md) |
| BE AI·챕터 | 챕터 생성·책 편집 | [ROLE_D](docs/team/ROLE_D_Backend_AI_Chapter.md) |
| BE 피드·인프라 | 피드·채팅·Docker | [ROLE_E](docs/team/ROLE_E_Backend_Feed_Infra.md) |

## 깃 규칙

[GIT_CONVENTION.md](docs/team/GIT_CONVENTION.md) 참고

## 라이선스

Private — 팀 내부 프로젝트
// fix init setup