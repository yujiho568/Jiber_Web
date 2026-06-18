# Jiber Backend

Spring Boot 기반 Jiber Web API 서버입니다. Phase 1에서는 API/DTO/security/error-response 골격과 MySQL schema 초안을 제공합니다.

## Build Tool

Maven을 사용합니다.

- `pom.xml` 하나로 Spring Boot 의존성 버전 관리를 명확하게 남길 수 있습니다.
- 현재 저장소에는 Gradle wrapper가 없고 로컬에는 Maven이 설치되어 있어 Phase 1 검증을 바로 실행할 수 있습니다.
- Java 17 이상 기준으로 컴파일하도록 `java.version=17`을 설정했습니다.

## Local Run

```bash
cd backend
mvn spring-boot:run
```

기본 포트는 `8080`이며 `BACKEND_PORT`로 변경할 수 있습니다.

## Test

```bash
cd backend
mvn test
```

## Auth E2E Smoke Schema Preflight

이메일/비밀번호 signup/login, pending social signup/link, OAuth callback smoke는 최신 auth schema를 전제로 합니다. 오래된 local Docker volume이 `db/004_auth_account_social_link.sql` 적용 전이면 `POST /api/v1/auth/signup`가 `users.password_hash` 누락 등으로 500을 반환할 수 있습니다.

Auth UX smoke 전에 root에서 읽기 전용 preflight를 실행합니다. 이 script는 `.env`를 읽을 수 있지만 DB password, connection string, email, provider subject, token 값은 출력하지 않고 `information_schema`만 조회합니다. DDL이나 migration은 실행하지 않습니다.

```bash
scripts/check-auth-schema.sh
```

확인 항목:

- `users.password_hash` column
- `users.email unique` index, 현재 migration 기준 이름은 `uk_users_email`
- `user_social_accounts` table
- `oauth_pending_social_sessions` table

실패하면 auth UX smoke를 중단하고 `004 migration 미적용` 또는 부분 적용 상태로 봅니다. 기존 DB를 즉시 수정하지 말고 먼저 backup 또는 Docker volume snapshot을 남깁니다.

선택지:

- Fresh local DB smoke: 새 local DB 또는 새 Docker volume을 준비해 `001 -> 002 -> 003 -> 004`가 처음부터 순서대로 적용되게 합니다. 기존 volume을 임의로 삭제하지 말고, 필요한 경우 Architecture / Design Agent 또는 QA와 DB 보존 여부를 확인합니다.
- Existing local DB migration: 아래 `004 Auth Account Migration Runbook`의 duplicate legacy email preflight와 cleanup policy를 먼저 확인합니다. 004는 one-shot migration이므로 중복 정리가 끝난 뒤 `db/004_auth_account_social_link.sql`을 한 번만 적용하고, 성공 후에는 재실행하지 않습니다.

Schema preflight가 통과한 뒤 smoke 순서:

1. Backend를 실행합니다.
2. Frontend를 실행합니다.
3. dummy email/password signup을 수행하고 refresh cookie가 HttpOnly로 설정되는지만 확인합니다. 응답 token, cookie 값, raw password는 터미널이나 이슈에 붙여 넣지 않습니다.
4. logout으로 refresh cookie clear와 서버 session revoke를 확인합니다.
5. 같은 dummy 계정으로 login 후 `POST /api/v1/auth/refresh`를 credentials 포함으로 호출해 access token body 계약과 refresh cookie rotation을 확인합니다.
6. pending social signup/link smoke는 실제 provider token 값을 출력하지 않고 `/signup/social` 수동 흐름에서 pending cookie 존재와 safe error만 확인합니다.
7. Google/Kakao/Naver 실제 provider E2E는 사용자 인증이 필요한 별도 수동 단계로 분리합니다.

## Environment Variables

루트 `.env.example`의 값을 사용합니다. 실제 secret 값은 커밋하지 않습니다.

- `BACKEND_PORT`
- `BACKEND_CORS_ALLOWED_ORIGINS`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `DB_ROOT_PASSWORD`
- `PUBLIC_DATA_SERVICE_KEY`
- `KAKAO_REST_API_KEY`
- `PUBLIC_DATA_IMPORT_MONTHS`
- `PUBLIC_DATA_TARGET_REGIONS`
- `PUBLIC_DATA_IMPORT_ENABLED`
- `PUBLIC_DATA_IMPORT_DRY_RUN`
- `PUBLIC_DATA_IMPORT_LIMIT`
- `PUBLIC_DATA_IMPORT_PAGE_SIZE`
- `FRONTEND_PUBLIC_BASE_URL`
- `JWT_ISSUER`
- `JWT_SECRET`
- `JWT_ACCESS_TOKEN_TTL_SECONDS`
- `JWT_REFRESH_TOKEN_TTL_SECONDS`
- `AUTH_REFRESH_TOKEN_COOKIE_NAME`
- `AUTH_REFRESH_TOKEN_COOKIE_PATH`
- `AUTH_REFRESH_TOKEN_COOKIE_SAME_SITE`
- `AUTH_REFRESH_TOKEN_COOKIE_SECURE`
- `OAUTH_GOOGLE_CLIENT_ID`
- `OAUTH_GOOGLE_CLIENT_SECRET`
- `OAUTH_GOOGLE_REDIRECT_URI`
- `OAUTH_KAKAO_CLIENT_ID`
- `OAUTH_KAKAO_CLIENT_SECRET`
- `OAUTH_KAKAO_REDIRECT_URI`
- `OAUTH_NAVER_CLIENT_ID`
- `OAUTH_NAVER_CLIENT_SECRET`
- `OAUTH_NAVER_REDIRECT_URI`
- `MODEL_SERVER_BASE_URL`
- `MODEL_SERVER_INTERNAL_TOKEN`
- `MODEL_SERVER_CONNECT_TIMEOUT_MS`
- `MODEL_SERVER_READ_TIMEOUT_MS`
- `MODEL_VERSION`
- `MODEL_BASELINE_DATE`
- `MODEL_FEATURE_SET_VERSION`

## Phase 1 Skeleton Scope

구현된 골격:

- 공통 에러 응답: `code`, `message`, `details`, `path`, `timestamp`
- validation error 처리
- public read endpoint와 protected endpoint를 나누는 Spring Security 설정
- JWT access token 발급/검증 skeleton
- refresh token HttpOnly cookie 설정과 `refresh_sessions` MyBatis mapper skeleton
- refresh token rotation 후 재사용 감지 시 해당 session family revocation skeleton
- `GET /api/v1/auth/me`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` skeleton
- OAuth2 login success handler skeleton
- Google/Kakao/Naver OAuth2 client registration 환경 변수 연결
- property, favorite, notice controller/DTO/service skeleton
- property map/search/detail API의 canonical table 기반 MyBatis 조회 skeleton
- Springdoc OpenAPI 설정
- MyBatis mapper 위치 설정
- 공공데이터포털 아파트 실거래 import batch skeleton
- Kakao Local jibun 주소 geocoding client skeleton

아직 mock/skeleton인 부분:

- Property map/search/detail MyBatis mapper query의 실제 MySQL 통합 검증
- 실제 Google/Kakao/Naver OAuth app 등록 후 provider별 E2E 로그인 검증
- refresh token reuse 감지 시 session family revocation SQL의 실제 MySQL 통합 검증
- 현재 로그인 사용자 주입과 favorite ownership 검증
- model-server feature mapping의 실제 DB/거래 데이터 기반 보강
- 공공데이터 canonical upsert의 DB-backed matching rule
- 공지사항 작성자/수정자 기록

## Auth / Security Handoff

- `/api/v1/favorites/**`는 `USER` 또는 `ADMIN` 필요.
- `POST /api/v1/properties/{propertyId}/valuation`과 `POST /api/v1/properties/{propertyId}/shap`은 `USER` 또는 `ADMIN` 필요.
- `/api/v1/admin/notices/**`는 `ADMIN` 필요.
- `GET /api/v1/auth/me`는 anonymous 호출을 허용하며 미인증 시 `{"authenticated": false, "user": null}`을 반환합니다.
- `POST /api/v1/auth/refresh`는 refresh cookie가 있을 때만 access token JSON을 반환하고, refresh token은 응답 body에 넣지 않습니다.
- `POST /api/v1/auth/logout`은 현재 refresh cookie를 폐기하고 같은 name/path 속성으로 cookie를 삭제합니다.
- refresh token reuse가 감지되면 재사용된 session과 그 descendant/current session family를 revoke하고 `AUTH_REQUIRED`를 반환합니다.
- 현재 security skeleton은 `AUTH_REQUIRED`, `ACCESS_DENIED`를 공통 error shape로 반환합니다.
- credentialed CORS에서는 wildcard origin을 허용하지 않으며, `BACKEND_CORS_ALLOWED_ORIGINS`에 `*`가 포함되면 fail-fast 처리합니다.
- OAuth2 success handler는 linked social subject만 refresh cookie 설정 후 `FRONTEND_PUBLIC_BASE_URL/login/callback`으로 redirect합니다.
- 미연결 provider identity는 local user를 자동 생성하지 않고 pending social session과 HttpOnly pending cookie를 만든 뒤 `FRONTEND_PUBLIC_BASE_URL/signup/social`로 redirect합니다.
- matching email만으로 social account를 자동 link하지 않습니다. 기존 계정 link는 pending cookie와 email/password 재인증을 통과한 뒤에만 수행합니다.
- 최초 `ADMIN` 권한은 자동 부여하지 않습니다. 별도 seed, migration, 또는 운영 script에서 명시 user ID/email allowlist로 처리해야 합니다.

### Auth Environment Variables

루트 `.env.example`의 auth 관련 변수만 사용합니다. 실제 secret 값은 커밋하지 않습니다.

- `JWT_ISSUER`: access token `iss` claim.
- `JWT_SECRET`: access token HMAC signing secret. 비어 있고 `APP_ENV`가 `local`, `dev`, `development`, `test`이면 서버 시작마다 ephemeral in-memory key를 생성합니다. 이 로컬 key는 재시작 후 기존 token을 무효화합니다. 그 외 환경에서 비어 있으면 서버 시작을 실패시켜야 합니다.
- `JWT_ACCESS_TOKEN_TTL_SECONDS`: access token TTL. Phase 1 기본값은 `900`.
- `JWT_REFRESH_TOKEN_TTL_SECONDS`: refresh cookie와 refresh session TTL. Phase 1 기본값은 `1209600`.
- `AUTH_REFRESH_TOKEN_COOKIE_NAME`: 기본값 `JIBER_REFRESH_TOKEN`.
- `AUTH_REFRESH_TOKEN_COOKIE_PATH`: 기본값 `/api/v1/auth`.
- `AUTH_REFRESH_TOKEN_COOKIE_SAME_SITE`: 기본값 `Lax`.
- `AUTH_REFRESH_TOKEN_COOKIE_SECURE`: `APP_ENV=prod` 또는 `APP_ENV=production`에서는 `true`가 아니면 fail-fast 처리합니다. 로컬 개발에서는 명시적으로 `false` 가능.
- `BACKEND_CORS_ALLOWED_ORIGINS`: credentialed API 요청을 위해 명시 origin만 허용합니다. `*`는 허용하지 않습니다.
- `FRONTEND_PUBLIC_BASE_URL`: OAuth 성공 후 redirect base URL. 기본값 `http://localhost:5173`.
- `OAUTH_GOOGLE_CLIENT_ID`, `OAUTH_GOOGLE_CLIENT_SECRET`, `OAUTH_KAKAO_CLIENT_ID`, `OAUTH_KAKAO_CLIENT_SECRET`, `OAUTH_NAVER_CLIENT_ID`, `OAUTH_NAVER_CLIENT_SECRET`: 실제 OAuth app 등록 후 환경으로만 주입합니다.
- `OAUTH_GOOGLE_REDIRECT_URI`, `OAUTH_KAKAO_REDIRECT_URI`, `OAUTH_NAVER_REDIRECT_URI`: provider console에 등록한 backend callback URL과 일치해야 합니다. 비워 두면 로컬 callback 기본값을 사용합니다.

### Local OAuth Setup

OAuth client id/secret이 모두 비어 있으면 local/dev/test app context는 정상 기동하지만 OAuth 시작 endpoint는 등록되지 않습니다. provider 일부만 설정하면 설정된 provider만 `/oauth2/authorization/{provider}`로 시작할 수 있습니다.

```bash
OAUTH_KAKAO_CLIENT_ID=<kakao-client-id>
OAUTH_KAKAO_CLIENT_SECRET=<kakao-client-secret>
OAUTH_KAKAO_REDIRECT_URI=http://localhost:8080/login/oauth2/code/kakao
```

Google은 Spring Security 기본 provider metadata를 사용합니다. Kakao/Naver는 backend 설정에서 authorization, token, user-info endpoint와 user-name-attribute를 명시합니다.

### OAuth Callback URLs

Provider console에는 Spring Boot 기본 callback 경로를 등록합니다.

- Google: `http://localhost:8080/login/oauth2/code/google`
- Kakao: `http://localhost:8080/login/oauth2/code/kakao`
- Naver: `http://localhost:8080/login/oauth2/code/naver`

목표 계약에서는 이미 연결된 social account의 OAuth 성공만 refresh cookie를 설정하고 `FRONTEND_PUBLIC_BASE_URL/login/callback`으로 redirect합니다. 미연결 social account는 pending social cookie를 설정하고 `FRONTEND_PUBLIC_BASE_URL/signup/social`로 redirect합니다. access token, refresh token, pending social token, provider token은 redirect URL에 넣지 않습니다.

### Remaining Auth Handoff

- Auth / Security Agent: 현재 provider-owned `users` 구조를 email/password `users`, `user_social_accounts`, `oauth_pending_social_sessions` 구조로 마이그레이션하세요.
- Auth / Security Agent: `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `GET /api/v1/auth/social/pending`, `POST /api/v1/auth/social/signup`, `POST /api/v1/auth/social/link`, `GET /api/v1/auth/social-accounts`를 구현하세요.
- Auth / Security Agent: matching email만으로 social account를 기존 user에 자동 연결하지 마세요. 기존 계정 연동은 이메일/비밀번호 인증 후 진행해야 합니다.
- Backend API Agent: `AuthUserPrincipal`을 favorite ownership, valuation, SHAP, notice mutation 작성자/수정자 기록에 연결하세요.
- Backend API Agent: `refresh_sessions` mapper query와 recursive session family revocation SQL을 실제 MySQL에서 검증하세요.
- Frontend / Map Agent: `/login`, `/signup`, `/signup/social` route와 이메일/비밀번호 form, social signup/link UX를 구현하세요.
- Frontend / Map Agent: 이미 연결된 social login의 `/login/callback` 진입 후 `POST /api/v1/auth/refresh`를 credentials 포함으로 호출하고, access token은 메모리에만 보관하세요.
- Frontend / Map Agent: 미연결 social callback의 `/signup/social` 진입 후 `GET /api/v1/auth/social/pending`을 호출해 신규 가입 또는 기존 계정 연동을 안내하세요.
- Frontend / Map Agent: logout 후 in-memory access token을 지우고 `/api/v1/auth/logout` 응답 body나 URL에서 token을 찾지 마세요.
- QA / Review Agent: redirect URL, localStorage, sessionStorage, log, committed files에 token/secret이 남지 않는지 확인하세요.
- QA / Review Agent: email signup/login, social signup, existing-account social link, already-linked social login, anonymous, `USER`, `ADMIN` 권한 matrix와 refresh/pending cookie flags를 local/prod-like 설정에서 검증하세요.

## AI / Model Server Handoff

- 프론트엔드는 model-server를 직접 호출하지 않습니다.
- Spring Boot만 내부적으로 `MODEL_SERVER_BASE_URL`의 `/internal/v1/**`를 호출해야 합니다.
- `PropertyValuationClient` 구현체는 `ModelServerPropertyValuationClient`입니다.
- HTTP client는 Spring Framework 6.1의 blocking `RestClient`를 사용합니다. 현재 backend는 MVC/blocking stack이므로 WebFlux 의존성을 추가하지 않고 `spring-boot-starter-web` 안에서 해결할 수 있기 때문입니다.
- valuation은 `POST /internal/v1/valuation/apartments`, SHAP은 `POST /internal/v1/shap/apartments`를 호출합니다.
- `MODEL_SERVER_INTERNAL_TOKEN` 또는 `jiber.model-server.internal-token` 값이 있으면 `Authorization: Bearer <token>` header를 추가합니다. token 값은 로그에 남기지 않습니다.
- `MODEL_SERVER_CONNECT_TIMEOUT_MS`, `MODEL_SERVER_READ_TIMEOUT_MS`로 연결/응답 timeout을 조정할 수 있습니다. 기본값은 각각 `2000`, `5000`입니다.
- model-server `supported=false`, `reason=INSUFFICIENT_DATA`, `missingFeatures` 응답은 public API에서 `VALUATION_INSUFFICIENT_DATA`로 변환합니다.
- model-server 연결 실패, 5xx, timeout, 잘못된 internal auth 등 RestClient 예외는 `MODEL_SERVER_UNAVAILABLE`로 변환합니다.
- 비아파트 valuation/shap은 `PropertyAiEligibilityService`에서 `VALUATION_UNSUPPORTED_PROPERTY_TYPE`으로 처리하는 경로를 유지해야 합니다.

### Phase 1 Feature Mapping Skeleton

현재 DB query가 없으므로 `ModelServerApartmentFeatureMapper`는 public request와 고정 skeleton 값을 조합합니다.

- public request에서 전달: `propertyId`, `asOfDate`, `exclusiveAreaM2`, `floor`
- `asOfDate`에서 계산: `dealYear`, `dealMonth`
- Phase 1 skeleton default: `sido=서울특별시`, `sigungu=강남구`, `legalDong=예시동`, `builtYear=2010`, `distanceToStationM=420`

다음 Backend/Data 작업에서 `properties`, `property_transactions`, 교통/인프라 데이터 기반으로 위 feature를 채워야 합니다. feature 이름은 `docs/contracts/model-server.md`와 `model-server/app/schemas/apartment.py`의 camelCase 필드를 유지합니다.

## Database

schema 초안은 `../db/001_phase1_schema.sql`입니다.

- `properties`
- `property_transactions`
- `users`
- `user_social_accounts` (target auth redesign)
- `oauth_pending_social_sessions` (target auth redesign)
- `refresh_sessions`
- `favorite_apartments`
- `favorite_areas`
- `notices`
- `apartment_price_predictions`
- `apartment_shap_values`

지도 bounds 검색을 위해 `properties(latitude, longitude)`와 `properties(property_type, latitude, longitude)` 인덱스를 포함했습니다. favorite 중복 방지를 위해 `favorite_apartments(user_id, property_id)`와 `favorite_areas(user_id, normalized_key)` unique key를 포함했습니다.

추가 public data import schema 초안은 `../db/002_public_data_import.sql`입니다.

- `public_data_import_runs`: batch 실행 로그와 count.
- `public_data_import_errors`: 월/지역/API 단위 실패 기록.
- `public_data_raw_apartment_transactions`: 공공데이터 원천 거래 staging. `source_key` unique key로 중복 저장을 방지합니다.
- `public_data_geocoding_cache`: `sido + sigungu + legalDong + jibun` 주소 조합별 Kakao geocoding 상태와 좌표 cache.

로컬 개발에서는 Docker MySQL 또는 로컬 MySQL 중 편한 방식을 사용할 수 있습니다. 운영에서는 Docker MySQL 고정이 아니라 managed DB 또는 운영 표준 MySQL 중 선택해야 합니다.

### 004 Auth Account Migration Runbook

`../db/004_auth_account_social_link.sql`은 provider-owned legacy `users`를 email/password account와 `user_social_accounts`로 분리하는 versioned one-shot migration입니다. 성공한 파일은 재실행하지 않습니다. 성공 후 재실행하면 `password_hash` column 또는 `uk_users_email` key가 이미 존재해 실패할 수 있습니다.

Auth E2E smoke 전에는 `scripts/check-auth-schema.sh`를 먼저 실행해 `users.password_hash`, `users.email unique`, `user_social_accounts`, `oauth_pending_social_sessions`가 모두 있는지 확인합니다.

004는 DDL 전에 duplicate legacy email preflight를 수행합니다. 같은 normalized email이 여러 legacy OAuth user에 있으면 자동 merge하지 않고 `JIBER_AUTH_MIGRATION_DUPLICATE_EMAIL`로 중단합니다. 이 실패는 DDL 전에 발생하므로 cleanup 후 004를 다시 실행할 수 있습니다.

실행 전 duplicate legacy email 진단:

```sql
SELECT
    SHA2(LOWER(TRIM(email)), 256) AS email_sha256,
    COUNT(*) AS legacy_user_count,
    GROUP_CONCAT(user_id ORDER BY user_id) AS user_ids,
    GROUP_CONCAT(DISTINCT oauth_provider ORDER BY oauth_provider SEPARATOR ',') AS providers
FROM users
WHERE email IS NOT NULL
  AND TRIM(email) <> ''
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;
```

이 진단은 email 원문과 provider subject 전체값을 출력하지 않습니다. `email_sha256`, user id, provider 종류만 보고 중복 그룹을 식별한 뒤 운영자가 canonical user를 결정해야 합니다.

Cleanup policy:

1. 각 duplicate legacy email 그룹에서 유지할 canonical user를 명시적으로 고릅니다. 최근 로그인, 실제 계정 소유 확인, 보존해야 할 favorites/notice 기록 등을 기준으로 결정합니다.
2. canonical user가 아닌 legacy user의 provider identity는 canonical user의 `user_social_accounts`로 옮깁니다. 004가 아직 실행되지 않은 DB라면 004의 `user_social_accounts` DDL과 같은 구조를 먼저 만들고, non-canonical user의 `oauth_provider`, `provider_user_id`, `email`, `display_name`을 canonical `user_id`로 insert한 뒤 검증합니다.
3. `refresh_sessions`는 보안상 non-canonical user의 세션을 revoke 또는 삭제하고 재로그인을 요구하는 쪽을 기본으로 합니다.
4. `favorite_apartments`, `favorite_areas`는 canonical user로 옮기되 unique 충돌이 있으면 하나만 보존합니다.
5. `notices.created_by_user_id`, `notices.updated_by_user_id`, `apartment_price_predictions.user_id`는 canonical user로 옮기거나 운영 판단에 따라 `NULL` 처리합니다.
6. FK 소유 데이터와 social account 이동을 검증한 뒤 non-canonical legacy user row를 삭제하거나 email을 제거하고 disabled 상태로 둡니다. 삭제 전에는 반드시 백업 또는 volume snapshot을 남깁니다.
7. duplicate 진단 쿼리가 0건을 반환하면 004를 다시 실행합니다.

004가 DDL 시작 이후 실패했다면 무작정 재실행하지 않습니다. DB snapshot/volume을 복원하거나, 어떤 DDL이 적용됐는지 확인한 뒤 수동 복구 SQL을 작성해야 합니다.

### Local Docker MySQL

루트 `compose.yaml`은 MySQL 8 로컬 개발용입니다. 실제 DB 비밀번호는 `.env`에만 둡니다.

```bash
cp .env.example .env
# .env에서 DB_PASSWORD, DB_ROOT_PASSWORD를 로컬 값으로 채웁니다.
docker compose up -d mysql
```

호스트의 3306 포트를 이미 다른 MySQL이 사용 중이면 `.env`의 `DB_PORT`를 예: `3307`로 바꾼 뒤 실행합니다. Spring Boot smoke test도 같은 `DB_PORT` 값으로 실행해야 Docker MySQL을 조회합니다.

처음 생성되는 Docker volume에는 `db/`의 SQL 파일이 파일명 순서대로 적용됩니다.

```text
db/001_phase1_schema.sql
db/002_public_data_import.sql
db/003_seed_sample_properties.sql
db/004_auth_account_social_link.sql
```

이미 volume이 존재하는 상태에서 seed를 다시 적용하려면 `.env`를 로드한 뒤 필요한 SQL만 직접 실행합니다.

```bash
set -a
source .env
set +a
docker compose exec -T mysql sh -c 'MYSQL_PWD="${MYSQL_PASSWORD:-}" mysql -u"${MYSQL_USER:-jiber}" "${MYSQL_DATABASE:-jiber}"' < db/003_seed_sample_properties.sql
```

`db/003_seed_sample_properties.sql`은 지도 검색, 필터 검색, 상세 조회 smoke test용 synthetic seed입니다. 실거래 원천 데이터나 투자 판단 근거로 사용하지 않습니다.

### Property API Smoke Examples

Spring Boot를 실행한 뒤 seed 데이터로 다음 public read API를 확인할 수 있습니다.

```bash
curl "http://localhost:8080/api/v1/properties/map?swLat=37.40&swLng=126.90&neLat=37.60&neLng=127.20&zoomLevel=5&propertyTypes=APARTMENT&transactionTypes=SALE"
curl "http://localhost:8080/api/v1/properties/search?sido=서울특별시&sigungu=강남구&page=0&size=10&sort=latestDealDate,desc"
curl "http://localhost:8080/api/v1/properties/1001"
```

지도 검색은 `latitude`, `longitude`가 있는 canonical `properties` row만 노출합니다. 향후 public data batch의 Kakao geocoding 성공 건만 canonical upsert 대상으로 삼는 정책을 유지해야 합니다.

## Public Data Import Batch

Phase 1 batch 범위:

- 기간: 최근 12개월. 기본값은 `PUBLIC_DATA_IMPORT_MONTHS=12`.
- 지역: `SEOUL,BUSAN`, 즉 서울특별시와 부산광역시 시군구 `LAWD_CD`.
- 거래 유형: 아파트 매매, 전세, 월세.
- API: 공공데이터포털 아파트 매매 endpoint와 아파트 전월세 endpoint.
- 지오코딩: Kakao Local address search.

환경 변수:

- `PUBLIC_DATA_SERVICE_KEY`: 공공데이터포털 service key. 실제 값은 `.env`에만 둡니다.
- `KAKAO_REST_API_KEY`: Kakao REST API key. 실제 값은 `.env`에만 둡니다.
- `PUBLIC_DATA_IMPORT_MONTHS`: 조회 개월 수. 기본값 `12`.
- `PUBLIC_DATA_TARGET_REGIONS`: `SEOUL,BUSAN` 형식.
- `PUBLIC_DATA_IMPORT_DRY_RUN`: 기본 `true`.
- `PUBLIC_DATA_IMPORT_LIMIT`: 기본 `100`.
- `PUBLIC_DATA_IMPORT_PAGE_SIZE`: 기본 `100`.

실행:

```bash
scripts/import-public-data.sh --dry-run --limit 20
scripts/import-public-data.sh --live --limit 100
```

`scripts/import-public-data.sh`는 `.env`를 읽고 Spring Boot runner를 1회 실행한 뒤 종료합니다. 기본은 dry-run입니다. dry-run은 대상 범위 확인용이며 공공데이터 API, Kakao API, DB mapper write를 호출하지 않습니다.

live 모드에서는 script와 Spring service 시작선에서 `PUBLIC_DATA_SERVICE_KEY`, `KAKAO_REST_API_KEY`를 모두 검증합니다. 누락되면 import run row 생성이나 외부 호출 전에 중단하며, key 값은 출력하지 않습니다.

### LAWD Code List

서울/부산 시군구 코드는 `backend/src/main/resources/publicdata/lawd-codes-seoul-busan.csv`에 둡니다. 이 목록은 Phase 1 skeleton용 정적 resource이며, 실제 운영 전에는 행정표준코드관리시스템 또는 공공데이터포털 기준으로 최신 여부를 재검증해야 합니다.

### Jibun Geocoding

주소 조합은 다음 순서입니다.

```text
sido + sigungu + legalDong + jibun
```

예: `서울특별시 강남구 역삼동 12-3`

정규화된 key는 `서울특별시|강남구|역삼동|12-3` 형태로 저장합니다. Kakao geocoding 성공 시 `latitude`, `longitude`를 `public_data_geocoding_cache`에 저장합니다. 실패 시 `ZERO_RESULT` 또는 `ERROR`와 실패 reason을 저장합니다. 좌표가 없는 거래는 canonical `properties` / `property_transactions` 반영 대상에서 제외할 수 있게 `geocoding_status`를 분리했습니다.

### Canonical Upsert Policy

Phase 1 Java 코드는 raw staging 저장과 geocoding cache 저장까지 구현합니다. 좌표가 확보된 거래만 canonical 반영 후보가 되며, 실제 `properties` / `property_transactions` upsert는 `CanonicalApartmentUpsertService` skeleton으로 분리했습니다.

다음 Backend/Data 작업에서 확인할 내용:

- 같은 단지 판별 기준: `sido`, `sigungu`, `legalDong`, `jibun`, `apartmentName`, 좌표 반경.
- 공공데이터 거래별 source id 안정성.
- 전세/월세 금액 단위 검증.
- canonical upsert transaction boundary와 idempotency.
- 오피스텔/연립다세대 endpoint와 DTO 확장.
