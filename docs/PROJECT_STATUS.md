# Jiber_Web Project Status Handoff

기준일: 2026-06-22

이 문서는 다른 PC에서 `Jiber_Web` 프로젝트를 이어받기 위한 현재 상태 요약입니다. 실제 secret, API key, DB password, token, cookie 값은 이 문서에 기록하지 않습니다.

## 1. 현재 저장소 상태

- 저장소 이름: `Jiber_Web`
- 기본 브랜치: `main`
- 원격: `origin/main`
- 기능 기준 최신 주요 커밋: `2667f36 feat: connect area favorites in frontend`
- 이 문서 추가 후에는 `git log --oneline -1`로 최신 handoff 커밋을 확인합니다.
- clean worktree 확인:

```bash
git status -sb
git status --short
```

`git status -sb`에서 `main...origin/main` divergence가 없고, `git status --short` 출력이 비어 있으면 새 PC로 넘기기 좋은 상태입니다.

## 2. 전체 구조

```text
Jiber_Web/
  backend/        Spring Boot, MyBatis, Security, OAuth2/JWT, property/favorite/publicdata API
  frontend/       Vue 3, Vite, Vue Router, Pinia, Axios, Kakao Maps UI
  model-server/   FastAPI model-server skeleton
  db/             MySQL schema, migrations, seed data
  docs/contracts/ API, auth, error, property, model-server contracts
  docs/           architecture, API, model, security, QA, status documents
  scripts/        local smoke and preflight scripts
  .agents/        role-specific agent ownership and operating rules
```

### Backend

- Spring Boot API 서버입니다.
- MyBatis mapper 기반으로 property, favorites, auth, public data persistence를 연결합니다.
- Spring Security, OAuth2 Login, JWT access token, HttpOnly refresh cookie flow를 사용합니다.
- 주요 영역:
  - email/password signup/login
  - Google/Kakao/Naver OAuth authorization start and callback
  - pending social signup/link
  - property map/search/detail
  - apartment favorites and area favorites
  - notices/admin skeleton
  - public data raw staging, Kakao REST geocoding cache, canonical upsert
  - model-server internal client skeleton

### Frontend

- Vue 3 + Vite SPA입니다.
- Vue Router, Pinia, Axios를 사용합니다.
- Kakao Maps JavaScript SDK로 지도 화면을 렌더링합니다.
- access token은 memory-only로 유지하며, refresh는 backend HttpOnly cookie flow를 따릅니다.
- 사용자에게 보이는 화면 문구는 자연스러운 한국어로 작성합니다.

### Model Server

- FastAPI skeleton입니다.
- 현재는 contract와 health/prediction/explanation endpoint 형태를 고정하는 단계입니다.
- 실제 회귀 모델, feature mapping, SHAP 계산, Q&A chatbot 구현은 사용자가 담당합니다.
- 실제 model artifact는 repository에 커밋하지 않습니다.

### DB

- MySQL 기준입니다.
- fresh schema와 migration/seed 파일은 `db/` 아래에 있습니다.
- 기존 DB에는 migration 순서와 preflight를 확인한 뒤 적용해야 합니다.

## 3. 완료된 주요 작업

- Monorepo skeleton과 `.agents` role ownership 정리
- Phase 0/0.5 contract 문서 정렬
- Docker MySQL local setup 문서화
- DB schema and migrations:
  - `001_phase1_schema.sql`
  - `002_public_data_import.sql`
  - `003_seed_sample_properties.sql`
  - `004_auth_account_social_link.sql`
  - `005_property_transaction_source_unique.sql`
- Auth schema preflight script:
  - `scripts/check-auth-schema.sh`
  - `users.password_hash`
  - `uk_users_email`
  - `user_social_accounts`
  - `oauth_pending_social_sessions`
- Email/password signup and login:
  - `POST /api/v1/auth/signup`
  - `POST /api/v1/auth/login`
  - normalized email validation
  - duplicate email `EMAIL_ALREADY_EXISTS`
  - invalid email `VALIDATION_FAILED`
  - login failure `INVALID_CREDENTIALS`
  - BCrypt password hash
- OAuth2 start/callback skeleton:
  - Google/Kakao/Naver provider registration
  - safe OAuth start response
  - missing provider and disabled provider tests
- Pending social signup/link flow:
  - linked subject: refresh cookie 발급 후 `/login/callback` redirect
  - unlinked subject: pending social session 생성 후 `/signup/social` redirect
  - pending raw token은 cookie에만 저장
  - DB에는 SHA-256 hash만 저장
  - matching email만으로 자동 link 금지
  - existing email/password account link는 password 재인증 필요
- Public route refresh restore:
  - `/map` reload 후 refresh cookie 기반 auth bootstrap
  - `/login/callback`은 bootstrap restore skip
  - guest-only route는 bootstrap 이후 `/map` redirect
  - protected route guard 기존 동작 유지
- Property API:
  - map bounds search
  - keyword/filter search
  - detail API
  - imported canonical property detail
  - valuation/SHAP public API 경로는 backend가 model-server internal client를 통해 호출하는 경계 유지
- Frontend map:
  - Kakao Maps JavaScript SDK loader
  - Kakao key present/absent fallback
  - `/map` 단지명 또는 지역 검색
  - bounds search 복귀
  - marker/list/detail navigation
  - 거래유형 기본값 매매/전세/월세 전체 선택
  - 상세 화면 최근 거래유형/최근 거래 건수 표시
- Public data import:
  - raw staging
  - Kakao REST geocoding cache
  - canonical properties/property_transactions upsert
  - raw `source_key` to `property_transactions.source_transaction_id` 보존
  - `source_transaction_id VARCHAR(500)`
  - `(source_system, source_transaction_id)` unique key
  - live import `--limit 10` smoke passed 기록 있음
- Favorites:
  - apartment favorites backend ownership
  - apartment favorites frontend add/list/delete
  - area favorites backend ownership
  - area favorites frontend save/list/delete/map restore
  - anonymous favorite 접근은 `AUTH_REQUIRED`
  - USER/ADMIN 모두 자기 favorite만 조회/추가/삭제
- Model-server:
  - FastAPI skeleton
  - Spring Boot internal client skeleton
  - frontend가 model-server나 `/internal/v1`을 직접 호출하지 않는 경계 유지

## 4. 현재 로컬 DB와 데이터 상태

현재 개발 PC 기준으로 기록된 상태:

- Docker container: `jiber-mysql`
- Docker published port 기록: `3307->3306`
- root `.env`의 `DB_PORT`는 Docker published port와 맞아야 합니다.
- auth/schema preflight는 통과 상태로 정리되었습니다.
- local DB에는 `004`와 `005` migration이 적용된 상태로 smoke가 진행되었습니다.
- public-data live import `--limit 10` smoke가 통과했으며 raw/canonical history가 일부 들어간 상태입니다.

다른 PC에서는 이 데이터가 없을 수 있습니다. fresh DB에서는 다음 순서로 준비합니다.

```text
001_phase1_schema.sql
002_public_data_import.sql
003_seed_sample_properties.sql
004_auth_account_social_link.sql
005_property_transaction_source_unique.sql
```

`003_seed_sample_properties.sql`만으로도 기본 map/search/detail smoke는 가능합니다. 실제 공공데이터 import는 `PUBLIC_DATA_SERVICE_KEY`, `KAKAO_REST_API_KEY`, DB 접속 정보가 준비된 뒤 small limit부터 실행합니다.

## 5. 새 PC 시작 절차

### 5.1 저장소 준비

```bash
git clone <repository-url> Jiber_Web
cd Jiber_Web
git status -sb
git log --oneline -5
```

이미 clone한 PC라면:

```bash
git checkout main
git pull origin main
git status -sb
```

### 5.2 환경 변수 준비

```bash
cp .env.example .env
```

`.env`에는 실제 값을 로컬에서만 입력합니다. 값은 채팅, 문서, commit, shell log에 남기지 않습니다.

Kakao key는 두 종류가 다릅니다.

- `VITE_KAKAO_MAP_APP_KEY`: frontend 지도 렌더링용 Kakao JavaScript key
- `KAKAO_REST_API_KEY`: backend/public-data 지번 주소 geocoding용 Kakao REST API key

Vite는 root `.env`를 자동으로 읽지 않습니다. frontend runtime key는 `frontend/.env` 또는 실행 환경 변수로 별도 주입해야 합니다.

### 5.3 Docker MySQL 준비

```bash
docker compose up -d mysql
docker ps --filter name=jiber-mysql --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Docker published port가 예를 들어 `3307->3306`이면 `.env`의 `DB_PORT`도 `3307`이어야 합니다.

### 5.4 DB schema preflight

```bash
scripts/check-auth-schema.sh
```

기존 DB에서 실패하면 missing 항목을 먼저 확인합니다. 004/005 migration은 기존 데이터 중복 preflight가 있으므로 운영성 데이터에는 backup/snapshot 후 적용합니다.

### 5.5 Backend 검증

```bash
cd backend
mvn test
```

### 5.6 Frontend 검증

```bash
cd frontend
npm install
npm run test -- --run
npm run build
```

### 5.7 Model-server 검증

```bash
cd model-server
python -m venv .venv
.venv/bin/python -m pip install -e ".[test]"
.venv/bin/python -m pytest -q
.venv/bin/python -c "from app.main import app; print(app.title)"
```

이미 `.venv`가 준비되어 있으면 설치 단계는 생략할 수 있습니다.

### 5.8 Public data import dry-run

```bash
scripts/import-public-data.sh --dry-run --limit 1
```

dry-run은 외부 API 호출과 DB write 없이 전제조건을 확인하는 용도입니다. live smoke는 key와 DB 상태가 준비된 뒤 `--limit 10` 이하부터 실행합니다.

## 6. 필요한 환경 변수

값은 이 문서에 쓰지 않습니다. 변수 이름과 용도만 기록합니다.

| 변수 | 필요 영역 | 용도 |
| --- | --- | --- |
| `DB_HOST` | backend/scripts | MySQL host |
| `DB_PORT` | backend/scripts | Docker published port와 일치해야 하는 MySQL port |
| `DB_NAME` | backend/scripts | application database name |
| `DB_USER` | backend/scripts | application DB user |
| `DB_PASSWORD` | backend/scripts | application DB password |
| `DB_ROOT_PASSWORD` | Docker/local setup | MySQL root password |
| `JWT_SECRET` | backend | access token signing secret |
| `FRONTEND_PUBLIC_BASE_URL` | backend OAuth/cors | frontend public base URL |
| `OAUTH_GOOGLE_CLIENT_ID` | backend OAuth | Google OAuth client id |
| `OAUTH_GOOGLE_CLIENT_SECRET` | backend OAuth | Google OAuth client secret |
| `OAUTH_KAKAO_CLIENT_ID` | backend OAuth | Kakao OAuth client id |
| `OAUTH_KAKAO_CLIENT_SECRET` | backend OAuth | Kakao OAuth client secret |
| `OAUTH_NAVER_CLIENT_ID` | backend OAuth | Naver OAuth client id |
| `OAUTH_NAVER_CLIENT_SECRET` | backend OAuth | Naver OAuth client secret |
| `PUBLIC_DATA_SERVICE_KEY` | public-data import | 공공데이터포털 service key |
| `KAKAO_REST_API_KEY` | public-data import | Kakao REST geocoding key |
| `VITE_KAKAO_MAP_APP_KEY` | frontend | Kakao Maps JavaScript key |
| `MODEL_SERVER_BASE_URL` | backend | FastAPI model-server internal base URL |
| `MODEL_SERVER_INTERNAL_TOKEN` | backend/model-server | backend to model-server internal auth token |

## 7. 검증된 smoke 목록

현재까지 기록된 smoke와 regression 범위:

- backend `mvn test`
- frontend `npm run test -- --run`
- frontend `npm run build`
- model-server pytest
- auth signup/login/logout/refresh
- OAuth authorization start safe response
- no-env/missing-provider/configured-provider OAuth registration behavior
- pending social signup/link unit and controller paths
- public route reload auth restore
- Kakao key present/absent map rendering path
- map keyword search and bounds reset
- imported sample `1912` search/detail
- public data dry-run
- public data live import `--limit 10`
- canonical upsert MySQL smoke
- apartment favorite add/list/delete
- area favorite add/list/delete/map restore
- `/favorites` auth guard
- mobile 390px layout smoke

새 PC에서는 이 목록을 한 번에 모두 재현하기보다 환경 복구 후 backend, frontend, model-server, dry-run 순서로 확인합니다. live import와 OAuth provider E2E는 key와 callback 설정을 마친 뒤 수행합니다.

## 8. 남은 작업

### AI / Model

- 실제 AI model implementation은 사용자가 담당합니다.
- Hedonic price regression model 학습/저장/로드
- feature mapping and preprocessing contract 확정
- SHAP values 계산과 explanation response 실데이터 연결
- Q&A chatbot contract/API 설계와 구현
- model artifact 배치 방식 결정
- model-server와 backend internal client의 실제 token 운영 정책 검증

### Data

- public data import scope 확대
- larger live import batch 안정화
- `PublicDataClientException=1` sanitized classification 개선
- imported data quality checks
- `005` source duplicate preflight 운영 절차 문서화 보강

### Auth / Security

- 실제 Google/Kakao/Naver provider OAuth pending signup/link E2E
- provider callback URL 운영 환경 검증
- refresh token reuse detection MySQL integration smoke 확대
- production secret rotation/runbook 보강

### Backend

- notices/admin mutation 완성
- notices author/modifier display 정책 보강
- property search performance index 검토
- map clustering 또는 pagination contract 검토
- CI/Testcontainers 도입

### Frontend

- notices/admin UI 완성
- large data map performance and clustering
- auth.ts Vite chunk warning 추적
- broader mobile smoke
- empty/error/loading 한국어 문구 최종 다듬기

### Deploy / Ops

- production compose or deployment config
- CI pipeline
- environment-specific CORS/OAuth redirect config
- backup/restore runbook

## 9. 주의사항

- `.env`, `frontend/.env`, API key, OAuth client secret, JWT secret, DB password, token, cookie 값은 절대 commit하지 않습니다.
- DB password를 command line argument로 직접 넘기지 않습니다.
- live public-data import는 quota와 DB write가 있으므로 처음에는 작은 `--limit`으로 실행합니다.
- 기존 DB에 004/005 migration 적용 전 backup/snapshot을 남깁니다.
- 005 migration 전 `(source_system, source_transaction_id)` duplicate preflight를 확인합니다.
- Kakao JavaScript key와 Kakao REST API key는 용도가 다릅니다.
- root `.env`는 Vite가 자동으로 읽지 않습니다.
- 실제 AI model artifact, `.pkl`, `.joblib`, raw public API response dump는 repository에 넣지 않습니다.
- frontend는 model-server, `/internal/v1`, Kakao REST geocoding endpoint를 직접 호출하지 않습니다.
- 투자 조언, 매수/매도 판단, 수익 보장 표현은 제품 범위 밖입니다.

## 10. 다음 추천 작업

1. 새 PC에서 `origin/main` 최신 상태를 pull하고 `.env`를 로컬에만 구성합니다.
2. Docker MySQL published port와 `DB_PORT` 정합성을 확인합니다.
3. `scripts/check-auth-schema.sh`, backend tests, frontend tests/build, model-server pytest를 순서대로 실행합니다.
4. seed 기반 map/search/detail smoke를 먼저 확인합니다.
5. public-data dry-run 후 key와 quota를 확인하고 small live import를 재시도합니다.
6. notices/admin 구현을 마무리합니다.
7. 사용자가 실제 AI model, SHAP, Q&A chatbot 구현 방향을 확정하면 model-server contract에 맞춰 연결합니다.
