# Email Password And Social Account Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace automatic OAuth signup with email/password membership plus explicit social account signup/linking.

**Architecture:** `users` becomes the Jiber account table. OAuth provider identities move to `user_social_accounts`. Unlinked provider callbacks create short-lived pending social sessions that drive `/signup/social` or existing-account linking.

**Tech Stack:** Spring Boot Security, MyBatis, MySQL, Vue 3, Pinia, Axios, Vitest, Maven.

---

## Task 1: Contract And Schema Alignment

**Owner:** Architecture / Design Agent

**Files:**

- Modify: `docs/contracts/auth-flow.md`
- Modify: `docs/contracts/error-response.md`
- Modify: `docs/contracts/phase1-handoff.md`
- Modify: `backend/README.md`
- Modify: `frontend/README.md`
- Later implementation modifies: `db/001_phase1_schema.sql` or adds `db/004_auth_account_social_link.sql`

- [x] Confirm contract language no longer says OAuth callback creates users automatically.
- [x] Add email/password signup and login API contracts.
- [x] Add pending social signup/link API contracts.
- [x] Add target DB table descriptions for `users`, `user_social_accounts`, and `oauth_pending_social_sessions`.
- [x] Update handoff order so favorites DB work waits until auth redesign is implemented.
- [x] Verify docs with `rg -n "creates or updates the local user|신규 사용자는 항상|OAuth 성공 후 backend는 refresh cookie" docs backend/README.md frontend/README.md`.

## Task 2: Backend Schema And Mapper Migration

**Owner:** Auth / Security Agent

**Files:**

- Modify or create migration under `db/`
- Modify: `backend/src/main/java/com/jiber/backend/auth/AuthUserRecord.java`
- Modify: `backend/src/main/java/com/jiber/backend/auth/AuthUserMapper.java`
- Modify: `backend/src/main/resources/mapper/AuthUserMapper.xml`
- Create: `backend/src/main/java/com/jiber/backend/auth/SocialAccountRecord.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/SocialAccountMapper.java`
- Create: `backend/src/main/resources/mapper/SocialAccountMapper.xml`
- Create: `backend/src/main/java/com/jiber/backend/auth/PendingSocialSessionRecord.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/PendingSocialSessionMapper.java`
- Create: `backend/src/main/resources/mapper/PendingSocialSessionMapper.xml`
- Test: `backend/src/test/java/com/jiber/backend/auth/AuthAccountMapperMyBatisTest.java`

- [ ] Add schema for email users, social account links, and pending social sessions.
- [ ] Store password hashes, not raw passwords.
- [ ] Add unique constraints for normalized email and `(oauth_provider, provider_user_id)`.
- [ ] Use MyBatis constructor mappings for Java records.
- [ ] Add mapper tests for user create/find, social link create/find, pending session create/consume.
- [ ] Run `cd backend && mvn test`.

## Task 3: Email Signup And Login

**Owner:** Auth / Security Agent

**Files:**

- Modify: `backend/src/main/java/com/jiber/backend/auth/AuthController.java`
- Modify: `backend/src/main/java/com/jiber/backend/auth/AuthService.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/EmailSignupRequest.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/EmailLoginRequest.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/PasswordPolicy.java`
- Modify: `backend/src/main/java/com/jiber/backend/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/jiber/backend/auth/AuthControllerTest.java`
- Test: `backend/src/test/java/com/jiber/backend/security/SecurityRulesTest.java`

- [ ] Add `POST /api/v1/auth/signup`.
- [ ] Add `POST /api/v1/auth/login`.
- [ ] Use `PasswordEncoder` for hashing and verification.
- [ ] Return access token JSON and set refresh cookie on signup/login success.
- [ ] Return `EMAIL_ALREADY_EXISTS` for duplicate signup.
- [ ] Return `INVALID_CREDENTIALS` for login failure.
- [ ] Keep access token out of cookies and keep refresh token out of response body.
- [ ] Run `cd backend && mvn test`.

## Task 4: OAuth Callback Branching And Pending Social Sessions

**Owner:** Auth / Security Agent

**Files:**

- Modify: `backend/src/main/java/com/jiber/backend/auth/OAuth2LoginSuccessHandler.java`
- Replace or split: `backend/src/main/java/com/jiber/backend/auth/LocalOAuth2UserProvisioningService.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/SocialLoginService.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/PendingSocialCookieService.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/SocialPendingResponse.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/SocialSignupRequest.java`
- Create: `backend/src/main/java/com/jiber/backend/auth/SocialLinkResponse.java`
- Test: `backend/src/test/java/com/jiber/backend/auth/OAuth2LoginSuccessHandlerTest.java`
- Test: `backend/src/test/java/com/jiber/backend/auth/SocialLoginServiceTest.java`

- [ ] If social account is already linked, issue refresh cookie and redirect to `/login/callback`.
- [ ] If social account is not linked, create pending social session and redirect to `/signup/social`.
- [ ] Add `GET /api/v1/auth/social/pending`.
- [ ] Add `POST /api/v1/auth/social/signup`.
- [ ] Add `POST /api/v1/auth/social/link`.
- [ ] Ensure matching email alone never links an account.
- [ ] Consume pending session after successful signup/link.
- [ ] Clear pending cookie after successful signup/link.
- [ ] Run `cd backend && mvn test`.

## Task 5: Frontend Login And Signup Screens

**Owner:** Frontend / Map Agent

**Files:**

- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/api/auth.ts`
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/components/AppHeader.vue`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/SignupView.vue`
- Create: `frontend/src/views/SocialSignupView.vue`
- Optional create: `frontend/src/views/SocialConnectionsView.vue`
- Test: `frontend/src/stores/__tests__/auth.store.test.ts`
- Test: `frontend/src/components/__tests__/AppHeader.test.ts`
- Test: `frontend/src/views/__tests__/LoginView.test.ts`
- Test: `frontend/src/views/__tests__/SignupView.test.ts`

- [ ] Add `/login`, `/signup`, and `/signup/social`.
- [ ] Move social buttons from header into login/signup surfaces or keep a compact header entry that links to `/login`.
- [ ] Add email/password signup and login forms.
- [ ] Add social signup completion form using `GET /auth/social/pending`.
- [ ] Add existing-account link flow after email/password login.
- [ ] Preserve memory-only access token behavior.
- [ ] Run `cd frontend && npm run test -- --run`.
- [ ] Run `cd frontend && npm run build`.

## Task 6: QA Matrix

**Owner:** QA / Review Agent

**Files:**

- Read all changed auth backend/frontend files.
- Read `docs/contracts/auth-flow.md`.

- [ ] Verify email signup creates `USER`, sets refresh cookie, and stores access token only in memory.
- [ ] Verify email login success and invalid credential failure.
- [ ] Verify unlinked social auth redirects to `/signup/social` and does not create a user automatically.
- [ ] Verify social signup creates user, links provider, logs in, and consumes pending session.
- [ ] Verify existing email account social link requires email/password login first.
- [ ] Verify already linked social auth logs in directly.
- [ ] Verify logout clears refresh cookie and memory token.
- [ ] Verify localStorage/sessionStorage have no tokens.
- [ ] Verify no token or secret appears in URLs, logs, or committed files.
- [ ] Verify `ADMIN` is never granted by public signup, login, or social linking.
