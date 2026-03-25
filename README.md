# Team3Bot - 단일 입력 기반 팀 커뮤니케이션 통합 봇

분산된 커뮤니케이션 채널(카카오톡/슬랙/노션)로 인한 정보 누락 문제를 해결하는 봇입니다.

## 기획 요약

- **Slack**을 단일 입력 창구로 사용
- **Notion**을 공식 기록 저장소로 활용
- `/announce`, `/meeting` 명령어만으로 공지와 회의 페이지 생성
- **대상**에 따라 해당 Slack 채널(3팀-server, 3팀-design 등)에만 공지 (노이즈 최소화)
- **노션 DB 주기 스캔** (한 번에 처리)
  - **새 회의 페이지** → Slack에 “회의가 잡혔다” 알림 (페이지 `created_time`이 최근 N시간 이내 & 미래 일정)
  - **리마인더** → 1일 전 / 5시간 전 / 10분 전
  - **회의록 요약** → (A) 회의 **종료 시각 + 30분** 후 또는 (B) 노션 **회의록 상태 = DONE** 이면 1회, 슬랙에 요약 전송 (`OPENAI_API_KEY` 없으면 본문 앞부분만)

## 명령어 사용법

### `/meeting` - 회의 페이지 생성

```
/meeting [제목] | [대상] | [서기] | [참여자1, 참여자2] | [아젠다]
```

- **대상**: Server, Design, Web, 전체 (해당 Slack 채널에 공지)
- **날짜**: 입력 안 하면 로컬 현재 시각 사용
- **참여자**: 쉼표로 구분

**예시:**
```
/meeting 서버 파트 회의 | Server | 김철수 | 이영희, 박민수 | 1. 인프라 점검 2. 배포 일정
```

### `/announce` - 공지 페이지 생성

```
/announce [제목]
[내용]
```

### `/meeting-summary` (또는 `/회의요약`) - 회의록 수동 요약

자동 요약은 **한 번만** 가고, 다시 돌리거나 빨리 올리고 싶을 때 사용합니다.

```
/meeting-summary 스프린트
/meeting-summary https://www.notion.so/...
```

- **제목 키워드**: 회의 DB에서 `제목` 속성에 포함 검색 → 1건이면 바로 요약, 여러 건이면 목록만 보여 줌 (링크 복사 후 다시 실행)
- **노션 링크 또는 페이지 ID**: 해당 페이지 본문을 읽어 요약 후 **명령을 친 채널**에 올림

Slack 앱에 Slash Command로 `/meeting-summary` 등록 (한글 명령은 워크스페이스에서 허용될 때 `/회의요약`도 같은 URL로 추가)

## 설정

### 1. .env 파일 (Git 미포함)

프로젝트 루트에 `.env` 파일을 만들고 환경 변수를 설정하세요. 앱 시작 시 자동으로 로드됩니다.

```bash
cp .env.example .env
# .env 편집하여 실제 값 입력
```

`.env`는 `.gitignore`에 포함되어 Git에 올라가지 않습니다.

### 알려줘야 하는 값 (체크리스트)

| 항목 | 설명 |
|------|------|
| `SLACK_BOT_TOKEN` | Slack 앱 → OAuth → Bot User OAuth Token (`xoxb-`) |
| `SLACK_SIGNING_SECRET` | Slack 앱 → Basic Information → Signing Secret |
| `SLACK_CHANNEL_MAPPING_*` | **대상**(Server, Design, Web, 전체, Test…)마다 **Slack 채널 ID** (`C…`) |
| `NOTION_API_TOKEN` | Notion Integration → Internal Integration Secret |
| `NOTION_MEETING_DATABASE_ID` | 회의 **데이터베이스** URL의 ID (하이픈 포함 32자 UUID) |
| `NOTION_PARENT_PAGE_ID` | `/announce`용 **부모 페이지** ID (`/meeting` DB만 쓰면 생략 가능) |
| `OPENAI_API_KEY` | (선택) 회의록 **AI 요약**. 없으면 노션 본문 앞부분만 슬랙에 표시 |

**노션 쪽:** 해당 DB·부모 페이지에 Integration을 **연결**(Connections)해야 API가 읽고/쓸 수 있습니다. 요약은 페이지 **블록(본문)** 을 읽습니다.

**알림 조건:** `대상`이 비어 있거나 매핑에 없으면 Slack 알림은 가지 않습니다. `상태`가 `미정`, `취소 및 변경`이면 신규 알림·리마인더·요약 모두 제외(기본값, `REMINDER_SKIP_STATUSES`로 변경 가능).

**중복 주의:** `/meeting`으로 페이지를 만들면 슬랙에 이미 공지가 가므로, 노션 스캔의 **「새 회의」** 알림과 겹칠 수 있습니다. 노션에서만 일정을 올릴 팀이면 유지하고, 슬랙 명령만 쓰면 `NOTION_NOTIFY_CREATION_ENABLED=false` 로 끄세요.

### 2. Slack 채널 매핑

`.env`에 대상 → 채널 ID 매핑:

```
SLACK_CHANNEL_MAPPING_SERVER=C0ALX730S0L
SLACK_CHANNEL_MAPPING_DESIGN=C0ALX6DKRN0
SLACK_CHANNEL_MAPPING_WEB=C0AM6GGNSEM
SLACK_CHANNEL_MAPPING_전체=C0ALC4NE7HV
```

### 3. Notion Database 스키마

| 속성명 | 타입 | 비고 |
|--------|------|------|
| 제목 | Title | 리마인더·`/meeting` 공통 |
| 날짜 | Date | 리마인더 필수 (시간 포함 권장) |
| 대상 | Select | Server, Design, Web, 전체 → Slack 채널 매핑 |
| 서기 | Rich text | Notion에서 People이면 Text로 변경 |
| 참여자 | Multi-select | 리마인더 메시지에 이름 나열 (멘션은 미구현) |
| 상태 | Select | 선택. `미정`, `취소 및 변경`이면 알림·요약 제외 |
| 회의록 상태 | Select | (선택) 값이 `DONE`(기본, `NOTES_SUMMARY_DONE_VALUE`로 변경)이면 **즉시** 회의록 요약 1회 전송 |
| 날짜.end | Date 범위 | (선택) 있으면 **종료 시각**으로 사용. 없으면 시작 + 60분(기본) 후 +30분에 요약 후보 |

**회의록 요약 시점 (둘 중 먼저 만족 시 1회만):** `회의록 상태` = DONE **또는** `종료(또는 시작+기본회의시간) + 30분` 경과.

### 4. Slack App 설정

- **OAuth & Permissions** Bot Token Scopes: `chat:write`, `commands`
- **Slash Commands** Request URL: `https://YOUR_DOMAIN/slack/events`  
  (`/meeting`, `/announce`, `/meeting-summary`, 필요 시 `/회의요약`)
- 채널에 봇 공지 전송하려면 해당 채널에 봇 초대 필요

## 로컬 테스트

**테스트용 채널을 따로 만들 필요는 없습니다.**

1. 기존 채널(3팀-server 등)에서 바로 테스트 가능합니다.
2. `/meeting 테스트 회의 | Server | 본인 | 본인 | 테스트` 입력 시:
   - Notion에 페이지 생성
   - **3팀-server** 채널에 공지 전송
3. **ngrok**으로 로컬 서버를 외부에 노출한 뒤, Slack App의 Slash Command Request URL에 `https://YOUR_NGROK_URL/slack/events` 설정

원한다면 `#3팀-봇테스트` 같은 전용 채널을 만들어 `.env`에 `SLACK_CHANNEL_MAPPING_TEST=Cxxxx` 형태로 추가해 사용해도 됩니다.

## 실행

```bash
./gradlew bootRun
```

## Docker

```bash
docker build -t team3bot .
docker run --env-file .env -p 8080:8080 team3bot
```

## CI/CD (딸깍 자동화)

**1) 가장 단순 — 플랫폼이 Git만 연결**

- **Railway / Render / Fly.io** 등에서 GitHub 저장소를 연결하면 `main` 푸시마다 빌드·배포가 자동으로 돌아갑니다. 별도 워크플로 없이도 “푸시 = 배포”가 됩니다.  
- 환경 변수(Slack·Notion·채널 매핑)는 각 서비스 대시보드에만 넣으면 됩니다(이미지에 시크릿 넣지 않기).

**2) GitHub Actions로 CI + 이미지 푸시**

- `.github/workflows/ci.yml`: PR/`main` 푸시 시 Gradle 빌드, `main`에는 **GHCR**(`ghcr.io/owner/repo`)로 Docker 이미지 푸시.  
- 저장소 **Settings → Actions → General**에서 Workflow 권한이 “read and write packages” 가능한지 확인.  
- 배포 서버는 `docker pull ghcr.io/...:latest` 후 `docker run` 하거나, 플랫폼이 GHCR 이미지를 배포 소스로 쓰게 설정.

**3) CD까지 Actions에서 하려면**

- **EC2**: SSH + `docker compose pull && up` (호스트·키를 `secrets`로).  
- **Fly**: `flyctl deploy` + `FLY_API_TOKEN`.  
- **Railway CLI**: `railway up` + `RAILWAY_TOKEN`.  
- 템플릿: `.github/workflows/deploy.example.yml` 참고 후 `deploy.yml`로 복사해 채우기.

정리하면, **“딸깍”만 원하면 Railway/Render에 Git 연동**이 제일 적은 설정이고, **이미지까지 GitHub에서 관리**하려면 지금 추가한 **CI + GHCR** 조합을 쓰면 됩니다.
