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

## Environment Variables

루트 `.env.example`의 값을 사용합니다. 실제 secret 값은 커밋하지 않습니다.

- `BACKEND_PORT`
- `BACKEND_CORS_ALLOWED_ORIGINS`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
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
- `OAUTH_KAKAO_CLIENT_ID`
- `OAUTH_KAKAO_CLIENT_SECRET`
- `OAUTH_NAVER_CLIENT_ID`
- `OAUTH_NAVER_CLIENT_SECRET`
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
- property, favorite, notice controller/DTO/service skeleton
- Springdoc OpenAPI 설정
- MyBatis mapper 위치 설정

아직 mock/skeleton인 부분:

- MyBatis mapper query의 DB 통합 검증
- OAuth2 client registration과 provider secret 주입
- refresh token reuse 감지 시 session family revocation SQL의 실제 MySQL 통합 검증
- 현재 로그인 사용자 주입과 favorite ownership 검증
- model-server feature mapping의 실제 DB/거래 데이터 기반 보강
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
- OAuth2 success handler는 local user 생성/조회 책임을 `LocalOAuth2UserProvisioningService`로 분리하고, 신규 사용자는 항상 `USER`로만 생성합니다.
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

### OAuth Callback URLs

Provider console에는 Spring Boot 기본 callback 경로를 등록합니다.

- Google: `http://localhost:8080/login/oauth2/code/google`
- Kakao: `http://localhost:8080/login/oauth2/code/kakao`
- Naver: `http://localhost:8080/login/oauth2/code/naver`

OAuth 성공 후 backend는 refresh cookie를 설정하고 `FRONTEND_PUBLIC_BASE_URL/login/callback`으로 redirect합니다. access token, refresh token, provider token은 redirect URL에 넣지 않습니다.

### Remaining Auth Handoff

- Backend API Agent: `AuthUserPrincipal`을 favorite ownership, valuation, SHAP, notice mutation 작성자/수정자 기록에 연결하세요.
- Backend API Agent: `refresh_sessions` mapper query와 recursive session family revocation SQL을 실제 MySQL에서 검증하세요.
- Backend API Agent: OAuth2 client registration은 실제 secret 없이 환경 설정 문서 또는 profile 설정으로 연결하세요.
- Frontend / Map Agent: `/login/callback` 진입 후 `POST /api/v1/auth/refresh`를 credentials 포함으로 호출하고, access token은 메모리에만 보관하세요.
- Frontend / Map Agent: logout 후 in-memory access token을 지우고 `/api/v1/auth/logout` 응답 body나 URL에서 token을 찾지 마세요.
- QA / Review Agent: redirect URL, localStorage, sessionStorage, log, committed files에 token/secret이 남지 않는지 확인하세요.
- QA / Review Agent: anonymous, `USER`, `ADMIN` 권한 matrix와 refresh cookie flags를 local/prod-like 설정에서 검증하세요.

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
- `refresh_sessions`
- `favorite_apartments`
- `favorite_areas`
- `notices`
- `apartment_price_predictions`
- `apartment_shap_values`

지도 bounds 검색을 위해 `properties(latitude, longitude)`와 `properties(property_type, latitude, longitude)` 인덱스를 포함했습니다. favorite 중복 방지를 위해 `favorite_apartments(user_id, property_id)`와 `favorite_areas(user_id, normalized_key)` unique key를 포함했습니다.
