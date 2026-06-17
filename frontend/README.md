# Jiber Frontend

Vue 3 기반 Phase 1 프론트엔드 skeleton입니다. 실제 Kakao Maps API key가 없어도 실행되며, 사용자에게 보이는 문구는 한국어를 기본으로 합니다.

## 실행 방법

```bash
cd frontend
npm install
npm run dev
```

검증 명령:

```bash
npm run test -- --run
npm run typecheck
npm run build
```

## 환경 변수

`frontend/.env.example`을 참고해 `frontend/.env`를 만듭니다.

```bash
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_KAKAO_MAP_APP_KEY=
```

- `VITE_API_BASE_URL`: Spring Boot public API base URL입니다. 프론트엔드는 `/api/v1/**`만 호출합니다.
- `VITE_KAKAO_MAP_APP_KEY`: Kakao Maps JavaScript app key입니다. 비어 있으면 지도 fallback 안내를 보여줍니다.

Vite는 frontend 앱 실행 시점의 env만 주입합니다. 루트 `.env`를 그대로 복사하지 말고, `frontend/.env`를 만들거나 실행 환경에서 `VITE_API_BASE_URL`, `VITE_KAKAO_MAP_APP_KEY`를 process env로 주입해 주세요.

## Auth Token 저장 정책

- access token은 Pinia auth store의 메모리 상태에만 저장합니다.
- access token, refresh token, OAuth provider token을 `localStorage`나 `sessionStorage`에 저장하지 않습니다.
- `/login/callback`은 이미 연결된 social account 로그인 완료용입니다. URL에서 token을 읽지 않고 `POST /api/v1/auth/refresh`를 `withCredentials: true`로 호출합니다.
- `/signup/social`은 미연결 social account의 가입/연동 완료용입니다. URL에서 pending token을 읽지 않고 `GET /api/v1/auth/social/pending`을 credentials 포함으로 호출해야 합니다.
- refresh 성공 후 `GET /api/v1/auth/me`를 호출해 현재 사용자를 확인합니다.
- logout은 `POST /api/v1/auth/logout`을 `withCredentials: true`로 호출한 뒤 메모리 token을 제거합니다.

## 현재 Skeleton 범위

- Vue Router route: `/`, `/map`, `/properties/:propertyId`, `/favorites`, `/notices`, `/admin`, `/login/callback`
- Pinia memory-only auth store와 `USER`, `ADMIN` route guard
- Axios API client와 공통 error response 타입
- property, favorites, notices, auth API module skeleton
- Kakao Maps loader, 실제 지도 인스턴스, bounds 기반 검색, marker 렌더링, key 누락 fallback
- ECharts 거래 차트와 SHAP 요인 차트 placeholder

## 아직 Skeleton인 부분

- Kakao marker clustering, marker 커스텀 오버레이, 지도 검색 성능 최적화
- 상세 화면의 실제 valuation/shap 입력 폼 세분화
- 이메일/비밀번호 `/login`, `/signup`, social signup/link `/signup/social` 화면
- 즐겨찾기 추가/삭제 버튼의 실제 화면 연결
- 공지사항 상세 화면과 관리자 수정/삭제 화면
- 디자인 시스템, 접근성 회귀 테스트, 브라우저 스크린샷 검증

## Backend/Auth/AI Handoff

- Backend API Agent: map search는 `swLat`, `swLng`, `neLat`, `neLng`, `propertyTypes`, `transactionTypes`, `zoomLevel` 파라미터를 기대합니다. `zoom` 또는 `propertyType` 단수 파라미터는 사용하지 않습니다.
- Auth / Security Agent: refresh/logout은 credential 포함 요청을 사용합니다. access token은 JSON 응답으로 받아 메모리에만 보관합니다.
- Auth / Security Agent: social OAuth는 자동 회원 생성이 아니라 email/password account signup/link flow와 연결되어야 합니다.
- Frontend / Map Agent: auth redesign 후 `/login`, `/signup`, `/signup/social`을 추가하고 social provider buttons는 해당 auth 화면에서 자연스럽게 제공하세요.
- AI / Data Integration Agent: valuation/shap은 Spring Boot `/api/v1/properties/{propertyId}/...`만 호출합니다. 비아파트 fallback 문구는 “아파트 단지에 한해 제공되는 기능입니다.” 계열로 처리합니다.
