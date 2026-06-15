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
- `MODEL_VERSION`
- `MODEL_BASELINE_DATE`
- `MODEL_FEATURE_SET_VERSION`

## Phase 1 Skeleton Scope

구현된 골격:

- 공통 에러 응답: `code`, `message`, `details`, `path`, `timestamp`
- validation error 처리
- public read endpoint와 protected endpoint를 나누는 Spring Security 설정
- property, favorite, notice controller/DTO/service skeleton
- Springdoc OpenAPI 설정
- MyBatis mapper 위치 설정

아직 mock/skeleton인 부분:

- MyBatis mapper XML과 실제 DB query
- OAuth2 provider callback 처리
- JWT 발급/검증 필터
- refresh token rotation/revocation service
- 현재 로그인 사용자 주입과 favorite ownership 검증
- 실제 model-server HTTP 호출
- 공지사항 작성자/수정자 기록

## Auth / Security Handoff

- `/api/v1/favorites/**`는 `USER` 또는 `ADMIN` 필요.
- `POST /api/v1/properties/{propertyId}/valuation`과 `POST /api/v1/properties/{propertyId}/shap`은 `USER` 또는 `ADMIN` 필요.
- `/api/v1/admin/notices/**`는 `ADMIN` 필요.
- 현재 security skeleton은 `AUTH_REQUIRED`, `ACCESS_DENIED`를 공통 error shape로 반환합니다.
- 다음 단계에서 OAuth2 login, JWT access token 검증, refresh cookie rotation, user principal mapping, initial ADMIN provisioning을 연결해야 합니다.

## AI / Model Server Handoff

- 프론트엔드는 model-server를 직접 호출하지 않습니다.
- Spring Boot만 내부적으로 `MODEL_SERVER_BASE_URL`의 `/internal/v1/**`를 호출해야 합니다.
- `PropertyValuationClient`는 Phase 1 skeleton입니다. 다음 단계에서 model-server contract에 맞는 HTTP client로 교체하세요.
- 비아파트 valuation/shap은 `PropertyAiEligibilityService`에서 `VALUATION_UNSUPPORTED_PROPERTY_TYPE`으로 처리하는 경로를 유지해야 합니다.

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
