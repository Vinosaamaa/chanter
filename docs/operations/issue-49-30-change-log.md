# Issues #49 + #30 Change Log

Issues:

- [#49 Auth UI](https://github.com/Vinosaamaa/chanter/issues/49)
- [#30 Wire Auth Service Principal](https://github.com/Vinosaamaa/chanter/issues/30)

## Summary

Shipped JWT-backed auth sessions (register, login, refresh, logout, `/me`), gateway enforcement on `/api/v1/**`, and community-service principal wiring for study servers, courses, enrollments, voice presence, and SaaS plan updates. Production frontend now has sign-in/register, persisted session, protected `/app`, and Bearer token API calls with refresh-on-401. The `/dev/demo` harness bootstraps four JWT demo personas and attaches tokens automatically.

## Backend

| Area | Change |
|------|--------|
| `backend/common` | `JwtTokenService`, `AuthHeaders`, `AuthRequestAttributes`, `InvalidJwtException` |
| `backend/auth-service` | Flyway `auth_users` + `auth_refresh_tokens`; register/login/refresh/logout/me; BCrypt passwords |
| `backend/gateway-service` | `JwtAuthenticationWebFilter` — JWT on `/api/v1/**` except public auth paths; forwards `X-User-Id` |
| `backend/community-service` | `AuthenticatedUserFilter`; controllers #12–#14 + SaaS plan use `@RequestAttribute` principal |

### Principal retrofit scope (#30)

**Done:** study server create/view, course create, cohort enroll, course channel access, voice join/leave/list, SaaS plan patch.

**Deferred (still caller-supplied ids):** office hours, message/media/agent/analytics query params — gateway still requires JWT.

## Frontend

| Path | Purpose |
|------|---------|
| `features/auth/` | Sign-in/register page, `auth-api`, `ProtectedRoute`, types |
| `stores/auth-store.ts` | Persisted access/refresh tokens + user profile |
| `lib/api-client.ts` | Bearer header + refresh-on-401 via `configureApiAuth` |
| `features/dev-demo/demo-auth.ts` | Bootstrap four fixed demo personas via register/login |
| `features/dev-demo/demo-fetch.ts` | JWT-aware fetch interceptor for legacy demo harness |

## Config

- `CHANTER_JWT_SECRET` — **required** for auth-service and gateway (no default; set in local `.env`)
- `VITE_API_BASE` or `VITE_API_BASE_URL` — optional frontend API base (Vite dev proxy works with empty base)

## Verification

```bash
cd backend && mvn -pl auth-service,community-service,gateway-service -am verify
cd frontend && npm run build
```

Manual:

1. Start stack (gateway + auth + community at minimum).
2. Visit `/sign-in` → register → lands on `/app` with session.
3. Visit `/dev/demo` → personas bootstrap → create study server/course flows work through gateway JWT.

## Follow-ups

- Retrofit remaining community/message controllers to principal-only (#30 remainder).
- #50 Study Server app shell (next in agent workflow).
