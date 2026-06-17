# Email Password And Social Account Auth Redesign

## Goal

Jiber accounts use email/password as the primary membership model. Google, Kakao, and Naver are login and signup helpers that can be linked to a Jiber account, not automatic account creators.

## Current Problem

The current implementation treats a successful OAuth callback as signup and login. It upserts a local user by provider and provider user id, assigns `USER`, creates a refresh session, and redirects to `/login/callback`.

That does not match the intended product flow. The intended flow is:

- A user can sign up and log in with email/password.
- A user can start social auth during signup.
- If social auth belongs to an existing linked account, login is easy.
- If social auth is not linked, the user chooses either new signup or linking to an existing account.
- If signup completes after social auth, the attempted social provider is linked automatically.

## Target Account Model

### `users`

`users` is the Jiber membership account.

Target fields:

- `user_id`
- `email`
- `password_hash`
- `display_name`
- `role`
- `enabled`
- `last_login_at`
- timestamps

Rules:

- `email` is unique after normalization.
- `password_hash` stores only a one-way password hash.
- Public signup always creates `USER`.
- `ADMIN` is still granted only by migration, seed, or controlled operational script.

### `user_social_accounts`

`user_social_accounts` links OAuth provider identities to a Jiber user.

Target fields:

- `social_account_id`
- `user_id`
- `oauth_provider`
- `provider_user_id`
- `provider_email`
- `provider_display_name`
- `linked_at`
- `last_login_at`
- timestamps

Rules:

- Unique key: `(oauth_provider, provider_user_id)`.
- A user may link multiple providers.
- A provider identity can belong to only one user.
- Provider access tokens are not stored in the MVP.

### `oauth_pending_social_sessions`

Pending social sessions represent a successful provider authentication that is not linked yet.

Target fields:

- `pending_social_session_id`
- `pending_token_hash`
- `oauth_provider`
- `provider_user_id`
- `provider_email`
- `provider_display_name`
- `suggested_email`
- `expires_at`
- `consumed_at`
- timestamps

Rules:

- The browser receives only a short-lived HttpOnly pending cookie.
- The server stores only a hash of the pending token.
- TTL target is 10 minutes.
- Pending social cookies are not refresh cookies and do not authenticate API requests.
- Provider authorization codes, provider access tokens, refresh tokens, and client secrets are never stored or logged.

## Target Flows

### Email Signup

1. User opens `/signup`.
2. User submits email, password, display name, and required agreements.
3. Backend validates uniqueness and password policy.
4. Backend creates a `users` row with `USER`.
5. Backend creates a refresh session and refresh cookie.
6. Frontend stores access token in memory only.

### Email Login

1. User opens `/login`.
2. User submits email and password.
3. Backend verifies password without revealing whether email or password was wrong.
4. Backend creates a refresh session and refresh cookie.
5. Frontend stores access token in memory only.

### Already Linked Social Login

1. User clicks Google/Kakao/Naver.
2. Provider redirects to backend callback.
3. Backend finds `(oauth_provider, provider_user_id)` in `user_social_accounts`.
4. Backend updates last login metadata.
5. Backend creates a refresh session and refresh cookie.
6. Backend redirects to `FRONTEND_PUBLIC_BASE_URL/login/callback` with no token in the URL.
7. Frontend calls `/api/v1/auth/refresh` with credentials and completes login.

### Unlinked Social Signup

1. User clicks Google/Kakao/Naver.
2. Provider redirects to backend callback.
3. Backend does not find a linked social account.
4. Backend creates a pending social session and pending cookie.
5. Backend redirects to `FRONTEND_PUBLIC_BASE_URL/signup/social` with no token in the URL.
6. Frontend calls `GET /api/v1/auth/social/pending`.
7. If no Jiber account uses the provider email, frontend shows social signup completion.
8. User sets password and confirms display name.
9. Backend creates `users`, links `user_social_accounts`, consumes pending session, creates refresh session, and clears pending cookie.
10. Frontend completes login through the standard memory-only token flow.

### Existing Account Social Link

1. User starts social auth for an unlinked provider.
2. Backend creates a pending social session.
3. If provider email matches an existing Jiber account, frontend tells the user to log in with email/password to connect that social account.
4. User logs in with email/password.
5. Frontend calls `POST /api/v1/auth/social/link`.
6. Backend verifies the authenticated user owns the target account, links the pending provider, consumes pending session, and clears pending cookie.
7. Future provider login uses the already linked social login flow.

The backend must not link a social identity to an existing account based only on matching email. Email match may guide the UX, but the existing account owner must authenticate first.

## API Contract Changes

New public API endpoints under `/api/v1/auth`:

- `POST /signup`
- `POST /login`
- `GET /social/pending`
- `POST /social/signup`

New authenticated API endpoints:

- `POST /social/link`
- `GET /social-accounts`

Existing endpoints stay:

- `GET /me`
- `POST /refresh`
- `POST /logout`

OAuth start endpoints stay outside `/api/v1`:

- `GET /oauth2/authorization/google`
- `GET /oauth2/authorization/kakao`
- `GET /oauth2/authorization/naver`

## Frontend Route Changes

New public frontend routes:

- `/login`
- `/signup`
- `/signup/social`

Optional authenticated route:

- `/account/social-connections`

Existing `/login/callback` remains only for already-linked social login completion.

## Error Contract Changes

New error codes:

- `INVALID_CREDENTIALS`: email/password login failed without saying which field was wrong.
- `EMAIL_ALREADY_EXISTS`: signup email already belongs to a Jiber account.
- `SOCIAL_PENDING_NOT_FOUND`: missing, expired, consumed, or invalid pending social session.
- `SOCIAL_ACCOUNT_ALREADY_LINKED`: provider account is already linked to a Jiber account.

## Security Decisions

- Password hashing uses Spring Security `PasswordEncoder`; BCrypt is acceptable for MVP.
- Passwords, provider tokens, refresh tokens, pending social tokens, JWT signing material, and OAuth secrets must never be logged.
- Social account linking requires either an already authenticated user or new signup completion.
- Matching email alone must not link a social account.
- Access tokens remain JSON response body values and frontend memory-only.
- Refresh tokens remain HttpOnly cookies with server-side hash persistence and rotation.
- Pending social tokens use a separate HttpOnly cookie and server-side hash persistence.
- Production refresh and pending cookies must be `Secure=true`.

## Implementation Sequencing

1. Update contracts and handoff docs.
2. Change backend schema and mappers from provider-owned users to email users plus social account links.
3. Add email/password signup and login endpoints.
4. Change OAuth success handling to branch between already-linked login and pending social signup/link.
5. Add frontend login, signup, and social signup/link screens.
6. Re-run QA for email signup/login, social signup, existing account linking, linked social login, refresh, logout, and token non-exposure.

## Out Of Scope For This Redesign

- Email verification.
- Password reset.
- Social unlink safety rules.
- All-device logout.
- MFA.
- Provider token storage.
