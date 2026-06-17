# Phase 1 Contract Handoff Notes

## Scope

These notes summarize how the current contract changes affect Phase 1 backend, frontend, auth, and AI work. They are not production code requirements by themselves; implementation agents should use the referenced contract documents as source of truth.

## Backend API Agent Impact

- Defer DB-backed favorites implementation until the Auth / Security Agent finishes the email/password plus social-account-linking redesign.
- Implement property search DTOs using `swLat`, `swLng`, `neLat`, `neLng`, `propertyTypes`, `transactionTypes`, and `zoomLevel`.
- Use comma-separated query parsing for list filters.
- Support administrative filters: `sido`, `sigungu`, `legalDong`, `complexName`, and general `keyword`.
- Keep property detail separate from public valuation and public SHAP routes.
- Implement favorite apartment and favorite area endpoints from `docs/contracts/favorites-api.md`.
- Implement public notice read and `ADMIN` notice mutation endpoints from `docs/contracts/notices-api.md`.
- Return shared errors from `docs/contracts/error-response.md`.

## Frontend / Map Agent Impact

- Add `/login`, `/signup`, and `/signup/social` before building more auth-gated flows.
- Treat social OAuth as signup/link assistance, not automatic account creation.
- Keep `/login/callback` for already-linked social login completion.
- Send map query parameters using `zoomLevel`, plural `propertyTypes`, and plural `transactionTypes`.
- Use `/api/v1/properties/search` for list/sidebar search and include `centerLat`/`centerLng` when distance-prioritized ordering is needed.
- Treat property detail, valuation, and SHAP as separate API calls.
- Use Korean UI text for empty states, validation errors, favorite actions, notice actions, and AI unsupported states.
- Do not call FastAPI model-server endpoints directly.

## Auth / Security Agent Impact

- Replace automatic OAuth user upsert with email/password users plus `user_social_accounts` and pending social sessions.
- Implement email/password signup and login before continuing favorites ownership work.
- OAuth callback should log in only when the provider identity is already linked. Otherwise it should create a pending social session and redirect to `/signup/social`.
- Existing-account social linking must require the Jiber account owner to authenticate with email/password first. Matching email alone must never link accounts.
- Follow the confirmed token policy in `docs/contracts/auth-flow.md`:
  - refresh token is an HttpOnly cookie with server-side revocation state
  - access token is short-lived, returned in JSON, and stored in frontend memory only
  - logout revokes the current refresh session and clears the refresh cookie
  - first `ADMIN` is granted only by seed, migration, or controlled operational script
- Protect all `/api/v1/favorites/**` endpoints with `USER` or `ADMIN`.
- Protect `/api/v1/properties/{propertyId}/valuation` and `/api/v1/properties/{propertyId}/shap` with `USER` or `ADMIN`.
- Protect all `/api/v1/admin/notices/**` endpoints with `ADMIN`.
- Ensure permission failures use `AUTH_REQUIRED` or `ACCESS_DENIED`.

## AI / Data Integration Agent Impact

- Keep FastAPI routes internal under `/internal/v1`.
- Implement apartment-only valuation and SHAP behavior from `docs/contracts/model-server.md`.
- Coordinate feature names and missing-data reason codes with Backend API Agent before DTO implementation.
- Do not expand AI support beyond apartments without Architecture / Design approval.

## QA / Review Agent Impact

- Verify email signup, email login, social signup, existing-account social link, already-linked social login, refresh, logout, and protected route behavior.
- Verify request parameter naming drift: `zoom` and `propertyType` should not be used in Phase 1 contracts.
- Verify anonymous access for map search, filter search, property detail, and public notice reads.
- Verify authenticated access for favorites.
- Verify `ADMIN`-only access for notice mutations.
- Verify non-apartment valuation and SHAP return `VALUATION_UNSUPPORTED_PROPERTY_TYPE`.
- Verify frontend-facing messages remain natural Korean and avoid investment advice.

## Blockers To Track

- Actual OAuth credentials and JWT secret values are unresolved.
- Actual first `ADMIN` account identity is unresolved, though the provisioning method is decided.
- Kakao Maps API key and allowed domains are unresolved.
- Target auth migration from provider-owned `users` to email users plus social account links is unresolved.
- MySQL schema and indexes for social account links, pending social sessions, favorite uniqueness, and notices are unresolved.
- Model feature set and model artifact availability are unresolved.
