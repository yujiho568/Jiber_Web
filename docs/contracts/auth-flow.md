# Auth Flow Contract Draft

## Scope

The MVP supports email/password membership plus Google, Kakao, and Naver social account linking. Social providers help users sign up, link an existing account, or log in after the provider identity is linked. The frontend owns route guards and user-facing Korean login states. The backend owns password verification, provider integration, social account linking, JWT issuance, refresh token persistence, token revocation, and role checks.

User-facing auth messages in the web UI must be natural Korean. Real OAuth client secrets, JWT secrets, provider tokens, and refresh tokens must never be committed or logged.

## Roles

- `ANONYMOUS`: Public browsing only.
- `USER`: Favorites, valuation, SHAP, and authenticated user features.
- `ADMIN`: All `USER` permissions plus notice mutation and admin screens.

## Confirmed Token Policy

The Required Open Decisions are resolved for Phase 1 as follows.

| Decision | Confirmed MVP policy |
| --- | --- |
| Refresh token storage 방식 | Store refresh token only in a secure HttpOnly cookie. Store only a hashed refresh token or token identifier server-side with user ID, expiry, device/session metadata, and revoked timestamp. |
| Access token 전달 방식 | Return short-lived access tokens in JSON response bodies from backend auth endpoints. The frontend keeps the access token in memory only and sends it as `Authorization: Bearer <token>`. |
| Logout 처리 방식 | Logout invalidates the current refresh token server-side, clears the refresh cookie with matching cookie attributes, and the frontend clears the in-memory access token. MVP logout is current-session logout; all-device logout can be added later. |
| 최초 ADMIN 부여 방식 | Initial `ADMIN` is assigned only through a seed, migration, or explicit operational script. Public signup/OAuth login must never grant `ADMIN` automatically. |
| 소셜 계정 처리 방식 | OAuth callback logs in only when the provider identity is already linked. Unlinked provider identities create a short-lived pending social session so the user can complete signup or link to an existing account after email/password authentication. |

Security decisions:

- Access token TTL remains short; default target is `JWT_ACCESS_TOKEN_TTL_SECONDS=900`.
- Refresh token TTL remains longer but revocable; default target is `JWT_REFRESH_TOKEN_TTL_SECONDS=1209600`.
- Refresh tokens are rotated on successful refresh.
- Refresh token reuse after rotation should revoke the affected refresh session and return `AUTH_REQUIRED`.
- `localStorage` and `sessionStorage` must not store access tokens, refresh tokens, OAuth provider tokens, or user secrets.
- The refresh cookie must be `HttpOnly`, `SameSite=Lax`, and `Secure=true` in production. Local development may set `Secure=false` only through environment configuration.
- Pending social session cookies must be separate from refresh cookies, short-lived, HttpOnly, and server-side hashed.
- Credentialed auth endpoints must validate allowed origins and must not allow wildcard credentialed CORS.

## Frontend Routes

Public frontend routes:

- `/`
- `/map`
- `/properties/:propertyId`
- `/notices`
- `/login`
- `/signup`
- `/signup/social`
- `/login/callback`

Protected frontend routes:

- `/favorites`: `USER` or `ADMIN`
- `/admin`: `ADMIN`
- `/account/social-connections`: `USER` or `ADMIN`

## Protected Endpoint Matrix

Base path: `/api/v1`

| Surface | Endpoint or route | Required role | Notes |
| --- | --- | --- | --- |
| Landing | `/` | `ANONYMOUS` | Frontend route, no auth required. |
| Map search | `GET /properties/map` | `ANONYMOUS` | Public read API. |
| Filter search | `GET /properties/search` | `ANONYMOUS` | Public read API. |
| Property detail | `GET /properties/{propertyId}` | `ANONYMOUS` | Public read API. User-specific favorite state must be false or omitted when unauthenticated. |
| Valuation | `POST /properties/{propertyId}/valuation` | `USER` or `ADMIN` | Externally exposed Spring API, but login is required in MVP. Non-apartment fallback still returns `VALUATION_UNSUPPORTED_PROPERTY_TYPE`. |
| SHAP | `POST /properties/{propertyId}/shap` | `USER` or `ADMIN` | Externally exposed Spring API, but login is required in MVP. Non-apartment fallback still returns `VALUATION_UNSUPPORTED_PROPERTY_TYPE`. |
| Notice list | `GET /notices` | `ANONYMOUS` | Public read API. |
| Notice detail | `GET /notices/{noticeId}` | `ANONYMOUS` | Public read API. |
| Favorite apartments | `/favorites/apartments/**` | `USER` or `ADMIN` | Ownership isolation required. |
| Favorite areas | `/favorites/areas/**` | `USER` or `ADMIN` | Ownership isolation required. |
| Admin notice mutation | `/admin/notices/**` | `ADMIN` | Create, update, delete only. |
| Admin page | `/admin` | `ADMIN` | Frontend route guard plus backend API enforcement. |
| Current user | `GET /auth/me` | `ANONYMOUS` | Returns auth state; see Auth API Draft. |
| Email signup | `POST /auth/signup` | `ANONYMOUS` | Creates a Jiber email/password account and starts a session. |
| Email login | `POST /auth/login` | `ANONYMOUS` | Verifies email/password and starts a session. |
| Pending social state | `GET /auth/social/pending` | Pending social cookie | Returns provider/email/display-name hints for social signup/link UX. |
| Social signup | `POST /auth/social/signup` | Pending social cookie | Creates a Jiber account, links the pending provider, and starts a session. |
| Social link | `POST /auth/social/link` | Pending social cookie + email/password re-auth | Links the pending provider to an existing account only after the account owner verifies email/password. |
| Social account list | `GET /auth/social-accounts` | `USER` or `ADMIN` | Lists linked providers for the current account. |
| Refresh | `POST /auth/refresh` | Refresh cookie | Requires valid refresh cookie and allowed origin. |
| Logout | `POST /auth/logout` | Refresh cookie if present | Idempotent; invalidates current refresh state when available and clears cookie. |

Permission failures:

- Missing login for protected APIs returns `401 AUTH_REQUIRED`.
- Authenticated user without the required role returns `403 ACCESS_DENIED`.
- Frontend route guards should use Korean messages such as "로그인이 필요합니다." and "관리자 권한이 필요합니다."

## OAuth2 Start Endpoints

- `GET /oauth2/authorization/google`
- `GET /oauth2/authorization/kakao`
- `GET /oauth2/authorization/naver`

## Account Model

`users` is the Jiber membership account. It stores normalized email, password hash, display name, role, enabled state, and timestamps.

`user_social_accounts` links provider identities to Jiber accounts. It stores the provider, provider user id, provider email, provider display name, linked timestamp, and last login timestamp. It must have a unique key on `(oauth_provider, provider_user_id)`.

`oauth_pending_social_sessions` stores successful provider authentications that are not linked yet. The browser receives only a short-lived pending social cookie; the database stores only the hash. Pending social sessions are consumed after signup or link completion.

Provider access tokens are not stored in the MVP.

## OAuth Callback Flow

1. User clicks a social login button in the Vue app.
2. Browser navigates to the backend OAuth2 authorization endpoint.
3. Provider redirects to the backend OAuth2 callback URI.
4. Backend validates the provider response and checks `user_social_accounts`.
5. If the provider identity is already linked, backend creates a refresh session, sets the HttpOnly refresh cookie, and redirects to `FRONTEND_PUBLIC_BASE_URL/login/callback`.
6. If the provider identity is not linked, backend creates a pending social session, sets the pending social cookie, and redirects to `FRONTEND_PUBLIC_BASE_URL/signup/social`.
7. For linked login, frontend calls `POST /api/v1/auth/refresh` with credentials included.
8. For pending signup/link, frontend calls `GET /api/v1/auth/social/pending` and shows either signup completion or existing-account linking UI.
9. Backend must never link a social identity to an existing Jiber account based only on matching email. The existing account owner must authenticate with email/password before linking.
10. Backend redirects must not put access tokens, refresh tokens, pending tokens, provider tokens, or authorization codes in frontend URLs.

## Auth API Draft

### Email Signup

`POST /api/v1/auth/signup`

Draft request:

```json
{
  "email": "user@example.com",
  "password": "plain-password-from-form",
  "displayName": "사용자"
}
```

Draft response matches the refresh/login access token response. Backend also sets the refresh cookie.

Rules:

- Email is normalized before uniqueness checks.
- Password is hashed with `PasswordEncoder`; raw passwords are never stored or logged.
- Duplicate email returns `409 EMAIL_ALREADY_EXISTS`.
- Public signup always creates `USER`.
- Public signup must never grant `ADMIN`.

### Email Login

`POST /api/v1/auth/login`

Draft request:

```json
{
  "email": "user@example.com",
  "password": "plain-password-from-form"
}
```

Draft response matches the refresh access token response. Backend also sets the refresh cookie.

Rules:

- Invalid email or password returns `401 INVALID_CREDENTIALS`.
- The response must not reveal whether the email or password was wrong.
- Successful login updates last login metadata.

### Pending Social State

`GET /api/v1/auth/social/pending`

Requires a valid pending social cookie.

Draft response:

```json
{
  "provider": "NAVER",
  "email": "user@example.com",
  "displayName": "사용자",
  "matchingEmailAccountExists": true
}
```

Rules:

- Missing, expired, consumed, or invalid pending social session returns `401 SOCIAL_PENDING_NOT_FOUND`.
- This endpoint must not return provider user ids or provider tokens.
- `matchingEmailAccountExists` may guide UX but must not link accounts automatically.

### Social Signup

`POST /api/v1/auth/social/signup`

Requires a valid pending social cookie.

Draft request:

```json
{
  "email": "user@example.com",
  "password": "plain-password-from-form",
  "displayName": "사용자"
}
```

Rules:

- Creates a `users` row, links the pending provider in `user_social_accounts`, consumes the pending session, clears the pending cookie, creates a refresh session, and returns an access token response.
- Duplicate email returns `409 EMAIL_ALREADY_EXISTS`.
- Already linked provider returns `409 SOCIAL_ACCOUNT_ALREADY_LINKED`.
- Public social signup always creates `USER`.

### Social Link

`POST /api/v1/auth/social/link`

Requires a valid pending social cookie plus the existing account owner's email/password.

Draft request:

```json
{
  "email": "user@example.com",
  "password": "plain-password-from-form"
}
```

Rules:

- Verifies email/password using the same safe credential failure policy as email login.
- Links the pending provider to the verified Jiber account.
- Consumes the pending session, clears the pending cookie, creates a refresh session, sets the refresh cookie, and returns an access token response.
- Wrong email, wrong password, disabled user, or account without a password hash returns `401 INVALID_CREDENTIALS`.
- Already linked provider returns `409 SOCIAL_ACCOUNT_ALREADY_LINKED`.
- Matching email alone is not enough; the user must pass email/password verification.

### Linked Social Accounts

`GET /api/v1/auth/social-accounts`

Requires authenticated `USER` or `ADMIN`.

Draft response:

```json
{
  "items": [
    {
      "provider": "NAVER",
      "providerEmail": "user@example.com",
      "linkedAt": "2026-06-17T10:30:00+09:00",
      "lastLoginAt": "2026-06-17T11:00:00+09:00"
    }
  ]
}
```

### Current User

`GET /api/v1/auth/me`

No login is required to call this endpoint. It is safe for app bootstrap.

Authenticated response:

```json
{
  "authenticated": true,
  "user": {
    "userId": 1,
    "email": "user@example.com",
    "displayName": "사용자",
    "roles": ["USER"]
  }
}
```

Unauthenticated response:

```json
{
  "authenticated": false,
  "user": null
}
```

Rules:

- Missing access token returns `200` with `authenticated: false`.
- Expired access token should return `200` with `authenticated: false`; the frontend may then attempt refresh.
- Malformed or suspicious tokens may return `401 AUTH_REQUIRED`.

### Refresh

`POST /api/v1/auth/refresh`

Requires the refresh cookie and an allowed origin. On success, rotates refresh state and returns a new access token.

Draft response:

```json
{
  "accessToken": "jwt-access-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "userId": 1,
    "email": "user@example.com",
    "displayName": "사용자",
    "roles": ["USER"]
  }
}
```

Failure cases:

- Missing, expired, revoked, reused, or invalid refresh token returns `401 AUTH_REQUIRED`.
- The response must not include refresh token values.

### Logout

`POST /api/v1/auth/logout`

Draft request:

```json
{
  "logoutAllDevices": false
}
```

Draft response:

```json
{
  "message": "로그아웃되었습니다."
}
```

Rules:

- Default MVP behavior logs out the current refresh session only.
- `logoutAllDevices=true` may be accepted after backend support exists; until then it should return `VALIDATION_FAILED` or be ignored by documented product decision.
- Logout must clear the refresh cookie by name and path with matching cookie attributes.
- Logout should be idempotent from the frontend perspective.

## Initial ADMIN Provisioning

Initial `ADMIN` access is granted only through an operational path:

1. Create or locate a normal Jiber user.
2. Run a controlled DB seed, migration, or admin bootstrap script with an explicit email/user ID allowlist.
3. Record who granted the role and when.

Rules:

- Public email signup and social signup assign `USER` by default.
- Email domain, provider type, or display name must not automatically grant `ADMIN`.
- The bootstrap script must be disabled by default in production unless explicitly enabled for a one-time operation.
- Actual admin emails must not be committed.

## Security Guardrails

- Do not commit real OAuth client secrets.
- Do not expose provider access tokens to the frontend.
- Do not log passwords, authorization codes, pending social tokens, refresh tokens, access tokens, JWT signing material, OAuth client secrets, or DB credentials.
- Do not store tokens in browser `localStorage` or `sessionStorage`.
- Do not provide investment advice, buy/sell recommendations, or guaranteed return language from auth-gated features.

## Phase 1 Handoff Notes

Backend API Agent:

- Implement refresh session persistence with hashed token or token identifier, expiry, rotation, and revocation fields.
- Enforce `USER` or `ADMIN` on favorites, valuation, and SHAP public Spring endpoints.
- Enforce `ADMIN` on `/api/v1/admin/notices/**`.
- Return `AUTH_REQUIRED` and `ACCESS_DENIED` using `docs/contracts/error-response.md`.

Frontend / Map Agent:

- Add `/login`, `/signup`, and `/signup/social` routes before relying on favorites as the next authenticated workflow.
- After OAuth callback, call `POST /api/v1/auth/refresh` with credentials included.
- After pending social callback, call `GET /api/v1/auth/social/pending` and guide the user to either social signup completion or existing-account linking through `POST /api/v1/auth/social/link` with email/password re-authentication.
- Keep access token in memory only and attach it as `Authorization: Bearer <token>`.
- Do not persist tokens in localStorage or sessionStorage.
- Clear in-memory token on logout, refresh failure, or app auth reset.

QA / Review Agent:

- Verify no token appears in redirect URL, localStorage, sessionStorage, logs, or committed files.
- Verify refresh cookie flags and CORS/origin behavior in local and production-like config.
- Verify anonymous, `USER`, and `ADMIN` access paths from the endpoint matrix.
