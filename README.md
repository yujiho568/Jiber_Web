# Jiber Web

지도 기반 부동산 탐색, 실거래 데이터 분석, 아파트 적정가 추정, SHAP 기반 가격 설명을 제공하는 부동산 거래 정보 웹 플랫폼입니다.

이 프로젝트는 사용자가 실거래 정보와 가격 설명을 이해하도록 돕는 데이터 서비스입니다. 투자 조언, 매수/매도 판단, 수익률 보장, 특정 부동산 추천은 범위 밖입니다.

## 제품 언어 원칙

- 사용자에게 보이는 웹 UI 문구는 자연스러운 한국어로 작성합니다.
- 화면 문구, 메뉴, 버튼, 라벨, 안내문, 에러 메시지, 빈 상태 문구는 한국어를 기본으로 합니다.
- 기술 식별자, 코드, API 경로, 설정 키는 영어를 유지할 수 있습니다.

## 기술 스택

- Frontend: Vue 3, Vite, Vue Router, Pinia, Axios, Kakao Maps API, ECharts
- Backend: Spring Boot, MySQL, MyBatis, Spring Security, OAuth2 Login, JWT, Springdoc OpenAPI, Bean Validation
- AI: FastAPI, Hedonic Price Model, SHAP 기반 XAI
- Data: MySQL schema, seed/import scripts, model feature mapping

## 디렉터리 구조

```text
backend/        Spring Boot API 서버
frontend/       Vue 3 SPA
model-server/   FastAPI 모델 서버
db/             MySQL 스키마, seed, migration 예정 영역
docs/
  architecture/ 시스템 설계 문서
  contracts/    서비스 간 계약 문서
  api/          API 문서와 예시
  model/        모델/데이터 문서
  security/     인증/인가 문서
  qa/           검증 계획과 리뷰 문서
.agents/        프로젝트 로컬 Codex 에이전트 정의
```

## 로컬 실행 방식

Phase 1 로컬 개발 흐름은 다음 순서입니다.

1. `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`, `DB_ROOT_PASSWORD`를 로컬 값으로 채웁니다.
2. `docker compose up -d mysql`로 MySQL 8을 실행합니다.
3. 처음 생성되는 Docker volume에는 `db/001_phase1_schema.sql`, `db/002_public_data_import.sql`, `db/003_seed_sample_properties.sql`, `db/004_auth_account_social_link.sql`, `db/005_property_transaction_source_unique.sql`이 순서대로 적용됩니다.
4. auth UX smoke 전에는 `scripts/check-auth-schema.sh`를 실행해 오래된 local DB volume에 004 auth migration이 빠져 있지 않은지 확인합니다. 이 preflight는 schema만 읽고 secret 값을 출력하지 않습니다.
5. DB-backed smoke test 전에는 Docker published port와 `.env`의 `DB_PORT`가 일치하는지 확인합니다. 자세한 MySQL smoke 예시는 `backend/README.md`를 참고합니다.
6. `backend/`에서 Spring Boot API 서버를 실행합니다.
7. `model-server/`에서 FastAPI 모델 서버를 실행합니다.
8. `frontend/`에서 Vite 개발 서버를 실행합니다.
9. 프론트엔드는 Spring Boot API의 `/api/v1/**`만 호출하고, Spring Boot가 내부적으로 모델 서버를 호출합니다.

공공데이터포털 실거래 import는 backend batch runner로 실행합니다. 현재 seed 데이터는 map/search/detail API smoke test용 synthetic 데이터이며, live public data import와 Kakao geocoding은 별도 실행 단계입니다. 로컬 개발에서는 Docker MySQL 또는 로컬 MySQL을 사용할 수 있고, 운영에서는 Docker MySQL 또는 managed DB 중 운영 기준에 맞게 선택합니다. 실제 public data service key와 Kakao REST API key는 `.env`에만 둡니다.

실제 API 키, OAuth client secret, JWT secret, DB 비밀번호는 저장소에 커밋하지 않습니다. 필요한 환경 변수 이름은 루트 `.env.example`에만 정의합니다.

## MVP 범위

- 랜딩페이지
- 지도 기반 부동산 검색
- 필터 검색
- 부동산 상세 기본 정보와 최근 실거래 정보
- 소셜 로그인
- 즐겨찾기
- 공지사항과 관리자 권한
- 아파트 적정가 추정
- SHAP 기반 가격 설명 시각화

MVP에서 제외하는 범위:

- 투자 조언, 매수/매도 판단, 수익률 보장
- 실시간 매물 중개 또는 계약 기능
- 아파트 외 부동산의 AI 가격 추정
- 실제 secret, API key, OAuth client secret의 저장 또는 추정
