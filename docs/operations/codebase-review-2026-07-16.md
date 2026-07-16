# Chanter Codebase Review — 2026-07-16

**Reviewer:** Cursor cloud agent (thorough read-only review)
**Commit base:** `main` (public beta, slices through #104 merged)
**Scope:** Full repository — backend (`backend/**`, ~42k LOC Java), frontend (`frontend/src/**`, ~34k LOC TS/TSX), infrastructure (`infra/**`), CI (`.github/workflows/**`), root `Makefile`, and `scripts/**`.

This document records **security findings, correctness bugs, and improvement recommendations** found during a codebase-wide review. It changes **no application code** — it is a report only. Every High/Critical item below was manually verified against the cited source. Line numbers reflect the state of `main` at review time and may drift as code changes.

> **Nuance on the trust model (read this first).** The gateway (`gateway-service`) validates the JWT and, on **every** path, strips any client-supplied `X-User-Id` header before re-injecting the authenticated one. So requests **that traverse the gateway cannot forge identity**. Several findings below are exploitable only if a downstream service port (e.g. `:8087`, `:8082`, `:8085`) is reachable **directly**, bypassing the gateway. In a hardened deployment where only the gateway is exposed, those drop in severity to defense-in-depth. They are still worth fixing because the services currently have **no second layer** of identity verification and dev compose publishes all ports on `0.0.0.0`.

---

## 1. Findings summary

| ID | Severity | Area | Title |
|----|----------|------|-------|
| SEC-01 | High | realtime / all services | Services trust `X-User-Id` with no gateway-only enforcement; realtime handshake trusts it before JWT |
| SEC-02 | High | agent-service | AI Study Assistant endpoints take the acting user from a client-supplied param/body (impersonation) |
| SEC-03 | High | community-service | Internal DM-call LiveKit token endpoint has no service-token check and no participant validation |
| SEC-04 | High | infra / config | Usable secrets shipped as working defaults; validation only checks length |
| SEC-05 | High | auth-service | Google OAuth links/provisions by email without checking `email_verified` (account takeover) |
| SEC-06 | High | frontend | Refresh token persisted in `localStorage` (XSS → renewable account takeover) |
| SEC-07 | Medium | gateway (also a bug) | Password-reset / email-verify / OAuth paths not in gateway allow-list → 401 through gateway |
| SEC-08 | Medium | auth-service | Rate limiter keys on the gateway IP → one global bucket (weak brute-force defense + DoS lockout) |
| SEC-09 | Medium | auth-service | OAuth flow has no `state`/PKCE → login CSRF |
| SEC-10 | Medium | frontend | `/dev/demo` harness + hardcoded password shipped in production bundle |
| SEC-11 | Medium | frontend / realtime | Access token sent as a WebSocket URL query parameter (token in logs) |
| SEC-12 | Medium | infra | Ports on `0.0.0.0`; Redis unauthenticated; MinIO/LiveKit default creds; LiveKit `--dev` |
| SEC-13 | Medium | CI | No explicit least-privilege `permissions:` for `GITHUB_TOKEN` |
| SEC-14 | Medium | infra | Unpinned/mutable base image tags (non-reproducible builds) |
| SEC-15 | Low | auth-service | Account enumeration via `409` on register |
| SEC-16 | Low | auth-service | Login timing side-channel enables user enumeration |
| SEC-17 | Low | media-service | No content-type allowlist on upload |
| SEC-18 | Low | gateway | `/actuator/info` reachable unauthenticated (build/git metadata) |
| SEC-19 | Low | gateway | CORS origins hardcoded to localhost; `allowedHeaders: "*"` |
| SEC-20 | Low | auth-service | `AuthRateLimiter` map grows unbounded |
| SEC-21 | Low | auth/notification | Internal-token compare accepts empty token if misconfigured empty |
| SEC-22 | Low | frontend | Server-provided OAuth URL rendered into `href` without scheme validation |
| BUG-01 | Medium | frontend | WebSocket reconnect reuses an expired token; no refresh on WS failure → realtime silently stalls |
| BUG-02 | — | — | Same as SEC-07 (functional impact of the gateway allow-list gap) |
| BUG-03 | Low | community-service | Cohort learner search does not escape LIKE wildcards (over-broad matching) |
| BUG-04 | Low/Info | community-service | Any member can post in default `announcements` channels (no per-channel write role) |

Plus a set of **non-security improvements** (§5).

---

## 2. What the codebase does well

Worth stating explicitly so remediation doesn't regress these:

- **JWT handling is solid.** `common/.../auth/JwtTokenService.java` enforces a ≥256-bit secret, positive TTL, uses a `MACVerifier` (so `alg=none` / RS256 confusion is rejected), and verifies signature, expiry, and subject.
- **Gateway strips spoofed identity** on every path (`JwtAuthenticationGlobalFilter.java:60-61,89-94`) and strips `access_token` from the query before proxying downstream.
- **Passwords** use `BCryptPasswordEncoder`; refresh/email/reset tokens are random 256-bit values stored **only as SHA-256 hashes**; password reset revokes all refresh tokens.
- **SQL is uniformly parameterized** across services (including dynamic `IN (...)` lists) — no SQL injection found. Media object keys are resource UUIDs (no path traversal). Agent LLM/embedding base URLs are config-only (no SSRF).
- **Internal service endpoints** (notification, agent, auth directory) use constant-time `MessageDigest.isEqual` for the internal token — except the DM-call one (SEC-03).
- **`.gitignore` correctly excludes** `.env`, `*.pem`, `*.key`, `secrets/`; `.env` is confirmed untracked. Actuator exposure is limited to `health,info` with `show-details: when_authorized`. CI has no `pull_request_target` and no `github.event` interpolation into `run:`.
- **Frontend has no `dangerouslySetInnerHTML`, `eval`, or `new Function`** anywhere in `src`. Query cache is cleared on user switch; refresh is single-flighted.

---

## 3. Security findings (detail)

### SEC-01 — Downstream services trust `X-User-Id`; realtime handshake trusts it before JWT — High
- **Where:** `backend/realtime-service/.../websocket/RealtimeWebSocketHandler.java:240-260` (header trusted before the JWT fallback); `infra/docker-compose.yml` publishes `8087:8087`. Same header-trust pattern (no JWT second layer) in `community-service`, `media-service`, `notification-service`, `search-service` `.../web/AuthenticatedUserFilter.java`.
- **Risk:** `authenticate()` returns `UUID.fromString(X-User-Id)` whenever the header is present, only falling back to JWT if it is absent. There is no JWT-validating filter for `/api/v1/realtime/**`. If port 8087 (or any service port) is reachable without going through the gateway, a client can send `X-User-Id: <any-uuid>` and act as any user. Through the gateway this is safe (header is overwritten); the exposure is the missing second layer + published ports.
- **Fix:** In realtime, require and validate a JWT for `/api/v1/realtime/**` and remove the header-trust branch (or only trust `X-User-Id` when the request provably came from the gateway via mTLS / a shared internal token). Apply the same gateway-only enforcement to the other services, and do not publish service ports outside the internal network.

### SEC-02 — agent-service takes the acting user from client input (impersonation) — High
- **Where:** `backend/agent-service/.../api/StudyAssistantController.java:32` (`@RequestParam UUID instructorUserId`), `:46` (`request.instructorUserId()`), `:64` (`@RequestParam UUID viewerUserId`); also `.../api/AiUsageMetricsController.java:26`. agent-service has **no** `AuthenticatedUserFilter`.
- **Risk:** The acting principal is caller-supplied, then used for `community-service` grant authorization. Any authenticated user who knows a target UUID (instructor/owner UUIDs appear in cohort rosters) can install/configure the AI Study Assistant, preview a server's resource/grant structure, and read AI usage metrics **as that user**. The sibling `GroundedSupportQuestionController.java:35,84` correctly uses `@RequestHeader(AuthHeaders.USER_ID)`, confirming this is a bug, not a design choice.
- **Fix:** Read the actor from `@RequestHeader(AuthHeaders.USER_ID)`, drop the `instructorUserId`/`viewerUserId` params/body fields, and add an `AuthenticatedUserFilter` to agent-service so identity can never be caller-supplied.

### SEC-03 — Internal DM-call token endpoint: no service-token check, no participant validation — High
- **Where:** `backend/community-service/.../api/InternalDmCallController.java:22-28`; `AuthenticatedUserFilter.java` only guards `/api/v1/` (this is `/internal/v1/`); token minted by `LiveKitTokenIssuer.issueForDmCall`.
- **Risk:** Unlike the other internal controllers (notification / resource-ingestion / assistant-tools), this one performs **no `X-Chanter-Internal-Service-Token` check**, and it is outside the `AuthenticatedUserFilter` path. It mints a LiveKit publish+subscribe token for room `dm-call-<callId>` from the client-supplied `X-User-Id` and `callId`, with no check that the user is a participant of that call. Anyone reaching community-service directly can mint tokens to join/eavesdrop/inject audio into arbitrary private DM calls.
- **Fix:** Enforce the internal service token (constant-time compare, like the other internal controllers) and/or validate that `participantUserId` is an active participant of `callId` before issuing the token.

### SEC-04 — Usable secrets shipped as working defaults; length-only validation — High
- **Where:** `infra/docker-compose.yml:92` (`CHANTER_JWT_SECRET:-chanter-local-dev-jwt-secret-32bytes!!`); `.env.example:54,57` (real 32+ char JWT secret and internal-service token); `scripts/product/lib.sh:88-107` auto-copies `.env.example`→`.env` and only checks `-lt 32`; `Makefile:20-28` checks length only.
- **Risk:** The committed defaults are 32+ chars, so every guard passes with **public, in-git** values. `product_load_env` silently creates `.env` from the example if missing, so a naive non-local bring-up can run with a JWT signing key and internal-service token that anyone can read from the repo → JWT forgery for any `userId` and internal-call impersonation. The app YAMLs correctly use `${CHANTER_JWT_SECRET}` with no default (fail-fast); compose and the example undermine that.
- **Fix:** Ship empty placeholders in `.env.example` (e.g. `CHANTER_JWT_SECRET=`), remove the `:-...` fallback in compose, and add validation that **rejects the known default values** (not just length). Require explicit opt-in before auto-creating `.env`.

### SEC-05 — Google OAuth matches accounts by email without `email_verified` — High
- **Where:** `backend/auth-service/.../application/OAuthAuthService.java:114-162` (userinfo parsed for `sub`/`email`/`name`; `provisionGoogleUser` links to an existing account by `findByEmail` and sets `emailVerified=true`).
- **Risk:** The OIDC `email_verified` claim is never checked. An attacker with a Google account whose unverified email matches a victim's Chanter email could log in as the victim (and the code even flips the account to verified).
- **Fix:** Reject/branch unless the userinfo response has `email_verified == true` before matching/provisioning by email.

### SEC-06 — Refresh token persisted in `localStorage` — High
- **Where:** `frontend/src/stores/auth-store.ts:14-42` — `persist` (defaults to `localStorage`, key `chanter-auth`) with `partialize` including both `accessToken` and `refreshToken`.
- **Risk:** Any script in the origin (a future XSS, a compromised npm dependency, a malicious extension) can read the long-lived refresh token and mint access tokens indefinitely — renewable account takeover that survives tab close.
- **Fix:** Do not persist the refresh token in `localStorage`. Prefer issuing it as an `HttpOnly; Secure; SameSite` cookie and keep only the short-lived access token in memory. If cookies aren't feasible, keep the refresh token in memory only.

### SEC-07 — Gateway allow-list omits public auth flows (also BUG-02) — Medium
- **Where:** `gateway-service/.../security/JwtAuthenticationGlobalFilter.java:26-32` (`PUBLIC_AUTH_PATHS` = health/register/login/refresh/logout only). `auth-service/.../api/AuthController.java` also exposes `/forgot-password` (:100), `/reset-password` (:112), `/verify-email` (:122), `/oauth/providers` (:132), `/oauth/{provider}/start` (:140), `/oauth/google/callback` (:146).
- **Risk:** Those flows are unauthenticated by nature, but the gateway requires a JWT for any `/api/v1/**` not in the allow-list → it returns **401** for password reset, email verification, and OAuth start/callback when routed through the gateway (which the frontend `/api` proxy does). This is both a hardening gap and a **functional bug** for those features.
- **Fix:** Add the public auth sub-paths to the gateway allow-list (prefix match on the known-public `/api/v1/auth/...` paths) while keeping `/me` and `/profiles/query` protected.

### SEC-08 — Auth rate limiter keys on the gateway IP — Medium
- **Where:** `auth-service/.../api/AuthController.java:180-183` (`request.getRemoteAddr()`); `AuthRateLimiter.java:31-43`; no `server.forward-headers-strategy` in auth-service config.
- **Risk:** Behind the gateway, `getRemoteAddr()` is the gateway for all clients, so the "per-IP" limit is one shared global bucket per action: it neither throttles a single attacker against one account nor prevents one client from locking out login/register for everyone (DoS).
- **Fix:** Set `server.forward-headers-strategy: framework`/`native` and derive the client IP from `X-Forwarded-For`, or rate-limit at the gateway; consider keying login on email+IP.

### SEC-09 — OAuth flow lacks `state`/PKCE (login CSRF) — Medium
- **Where:** `auth-service/.../application/OAuthAuthService.java:69-133`.
- **Risk:** The authorization URL has no anti-CSRF `state`, and the callback validates none → OAuth login-CSRF (forcing a victim to complete login with an attacker's code).
- **Fix:** Generate a random `state` (and PKCE `code_verifier`), bind to the session, validate on callback.

### SEC-10 — `/dev/demo` harness + hardcoded creds ship in production — Medium
- **Where:** `frontend/src/app/router.tsx:190` (`/dev/demo`, no `import.meta.env.DEV` guard); `frontend/src/features/dev-demo/demo-auth.ts:17` (`DEMO_PASSWORD = 'chanter-dev-demo'`), auto login/register of `owner`/`instructor`/`learner` personas.
- **Risk:** The route and module are bundled unconditionally. In production, `/dev/demo` attempts to log in/register the demo personas with the hardcoded password; if those accounts exist in prod, anyone gets a live "Owner" session, and either way an internal backdoor + credentials ship to users.
- **Fix:** Gate the route and the entire `dev-demo` import behind `import.meta.env.DEV` (tree-shaken from prod) and never provision the demo accounts in production.

### SEC-11 — Access token in WebSocket URL query parameter — Medium
- **Where:** `frontend/src/features/realtime/realtime-client.ts:93-94`; `frontend/src/features/friends/social-realtime-client.ts:165`; consumed by `realtime-service`.
- **Risk:** Tokens in URLs land in proxy/gateway access logs, APM traces, and browser history even over `wss:`. The gateway strips `access_token` before proxying downstream (good), but it still appears client→gateway.
- **Fix:** Authenticate the socket via the `Sec-WebSocket-Protocol` header or an auth frame sent right after `onopen`; keep access-token TTL short (already 15m) and ensure the gateway doesn't log query strings.

### SEC-12 — Infra exposure & default credentials — Medium
- **Where:** `infra/docker-compose.yml` — ports published on `0.0.0.0` (`:10,:24,:56,:72-73,:117`); Redis has no `requirepass` (`:20-24`); MinIO `chanter/chantersecret` (`:69-70`); LiveKit `--dev` + `devkey:secret` (`:113-115`).
- **Risk:** On a shared/routable dev network these reach off-box; combined with default creds → data exposure and, for Redis, historically RCE via `CONFIG SET`.
- **Fix:** Bind to `127.0.0.1` for local dev; add `--requirepass` to Redis; require explicit (non-default) MinIO/LiveKit credentials outside local dev; drop `--dev` for shared envs.

### SEC-13 — CI lacks least-privilege token permissions — Medium
- **Where:** `.github/workflows/ci.yml` (no top-level or job `permissions:`).
- **Risk:** The workflow token inherits the repo default (potentially read/write `contents`). Jobs build untrusted PR code, so an exploited build step runs with broader scope than needed. No secrets are used, which limits blast radius.
- **Fix:** Add `permissions: contents: read` at the top level; grant more only per-job where required.

### SEC-14 — Unpinned/mutable image tags — Medium
- **Where:** `infra/docker-compose.yml:2` (`postgres:16-alpine`), `:21` (`redis:7-alpine`); `infra/docker/realtime-service/Dockerfile:1,17` (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`). (Redpanda, MinIO, LiveKit are properly pinned.)
- **Risk:** Floating tags → non-reproducible builds and silent pull of a regressed/compromised upstream.
- **Fix:** Pin to specific patch versions and ideally digests; add Renovate/Dependabot.

### Low / Info
- **SEC-15 — Register account enumeration:** `AuthSessionService.java:59-61,73-78` returns `409 "Email is already registered"`, enabling enumeration (undercuts the neutral password-reset response). Consider a neutral response + out-of-band email.
- **SEC-16 — Login timing side-channel:** `AuthSessionService.java:88-99` skips bcrypt for unknown users; compare against a dummy hash to equalize timing.
- **SEC-17 — Media upload content-type:** `media-service/.../CourseResourceService.java:71-74` accepts any/empty content type. Downloads use `Content-Disposition: attachment` (XSS mitigated), but add an allowlist and guard `MediaType.parseMediaType` against invalid stored values.
- **SEC-18 — Actuator `info` public:** `JwtAuthenticationGlobalFilter.java:82-84` treats `/actuator/**` as public; `info` can leak build/git metadata. Restrict to internal networks.
- **SEC-19 — CORS:** `gateway-service/.../application.yml` hardcodes localhost origins (not env-driven — staging/prod need code changes) and wildcards `allowedHeaders`. `allowCredentials` is false and auth is bearer-based, so the wildcard is acceptable; make origins env-driven.
- **SEC-20 — Rate-limiter memory:** `AuthRateLimiter.java:21` never evicts entries; use a bounded/expiring cache.
- **SEC-21 — Empty internal token:** `InternalUserDirectoryController.java:56-63`, `InternalNotificationController.java:59-66` — constant-time compare is correct, but an explicitly-empty configured token would match an empty presented token. Validate non-blank + min length at startup.
- **SEC-22 — OAuth `href` scheme:** `frontend/src/features/auth/pages/SignInPage.tsx:137` renders `authorizationUrl` into `href` without validating the scheme; validate `https:`/`http:` before rendering.

---

## 4. Correctness bugs (detail)

### BUG-01 — Realtime silently stalls when the access token expires — Medium
- **Where:** `frontend/src/features/realtime/realtime-client.ts:93-95,141-151`; `social-realtime-client.ts:153-168`; consumer `use-channel-conversation.ts:146-197`.
- **Risk:** The client captures `accessToken` at construction and reuses it on every reconnect. HTTP-triggered refreshes recreate the client (effect dep), but an idle session with only a socket active can have its token expire; the reconnect loop then retries forever with the expired token and no path triggers a refresh → realtime delivery stalls silently until an unrelated HTTP 401 refreshes.
- **Fix:** On WS auth-failure/close, trigger the shared `refreshSession()` and reconnect with the new token, or read the token from the store at each `openSocket()` rather than caching it.

### BUG-02 — Password reset / email verify / OAuth return 401 through the gateway
Functional consequence of **SEC-07**; see that entry for the fix.

### BUG-03 — Cohort learner search doesn't escape LIKE wildcards — Low
- **Where:** `community-service/.../infra/JdbcCourseRepository.java:499` (`"%" + learnerSearch + "%"`). Not injection (bound param), but user `%`/`_` act as wildcards, unlike sibling queries that call an `escapeLikePattern` helper. Reuse that helper.

### BUG-04 — Any member can post in default `announcements` channels — Low/Info
- **Where:** `community-service/.../CourseService.java:826-831` and `StudyServerService.java:381-386` return `canPost=true` for every member. May be intentional (Discord-like); if announcements should be instructor-only, gate `canPostMessages` on role for those channels.

---

## 5. Improvement recommendations (non-blocking)

1. **Second identity layer in services (defense-in-depth).** Even if the gateway is the only exposed surface, have services reject requests that didn't come through it (shared internal token or mTLS), so a single network mistake isn't full impersonation. Ties off SEC-01/02/03.
2. **Secret management.** Empty placeholders in `.env.example`; reject known-default secret values at startup; document rotation for MinIO/LiveKit/Redis. Ties off SEC-04/12.
3. **Frontend auth hardening.** Move refresh token out of `localStorage`; gate `/dev/demo` to dev builds; validate OAuth `href` scheme. Ties off SEC-06/10/22.
4. **CI/supply chain.** Add least-privilege `permissions:`; pin image tags/digests; add Dependabot/Renovate for Maven, npm, and Docker. Ties off SEC-13/14.
5. **Test coverage for security-critical paths.** No test covers `ProtectedRoute` (add: unauthenticated `/app/*` → `/sign-in`, and cache-clear on user switch). Add backend tests asserting that authz endpoints reject a caller acting as another user (would have caught SEC-02). Add a gateway routing test that the public auth paths return non-401 unauthenticated (would have caught SEC-07).
6. **Container hardening.** Add `deploy.resources.limits`/`mem_limit`; run upstream containers as non-root where supported (the custom realtime image already does).
7. **Ops hygiene.** Don't echo the demo password in `scripts/seed-workable-product-demo.sh:239`; pass values into inline Python via `argv`/env rather than string interpolation.

---

## 6. Recommended remediation order

1. **SEC-04** (secrets shipped as defaults) — cheapest, highest leverage; blocks JWT/internal-token forgery if any non-local bring-up happens.
2. **SEC-02 / SEC-03** (agent-service impersonation, DM-call token minting) — concrete broken-access-control, self-contained fixes in one controller each.
3. **SEC-06** (refresh token in `localStorage`) + **SEC-10** (`/dev/demo` in prod) — frontend account-takeover surface.
4. **SEC-05** (OAuth `email_verified`) + **SEC-07/BUG-02** (gateway allow-list) — required before OAuth/password-reset are relied on in production.
5. **SEC-01** (service-level identity enforcement) — larger cross-service change; do as a hardening epic.
6. Remaining Medium/Low items as normal backlog, batched by area (infra SEC-12/14, CI SEC-13, auth SEC-08/09/15/16).

Each fix should be its **own issue → branch → PR** per `docs/operations/agent-workflow.md`, with a regression test where practical (§5.5).

---

## 7. GitHub tracking (2026-07-16)

- **Epic:** [#180](https://github.com/Vinosaamaa/chanter/issues/180) — Codebase hardening (2026-07-16 review)
- **Project board:** [Codebase Hardening #7](https://github.com/users/Vinosaamaa/projects/7)
- **Issue breakdown:** [`docs/issues/codebase-hardening-issue-breakdown.md`](../issues/codebase-hardening-issue-breakdown.md)
- **Findings PR:** [#179](https://github.com/Vinosaamaa/chanter/pull/179) (docs-only; open)
- **Child slices:** #181–#205 (one issue per finding in §1; SEC-07 and BUG-02 share #187)
