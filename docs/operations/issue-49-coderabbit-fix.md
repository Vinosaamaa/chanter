# Issue #49 + #30 CodeRabbit Fix Log

PR: [#66](https://github.com/Vinosaamaa/chanter/pull/66)

| Finding | Fix |
|---------|-----|
| Static JWT secret fallback in yml / `.env.example` | Require `CHANTER_JWT_SECRET` (no default); empty placeholder in `.env.example` |
| `/me` missing `Authorization` returns 400 | Optional header + explicit 401; `AuthExceptionHandler` for invalid JWT |
| Registration race / non-transactional writes | `@Transactional` register; `DataIntegrityViolationException` → 409 |
| Refresh token replay race | `consumeActiveUserIdByTokenHash` with `SELECT … FOR UPDATE` + revoke in one transaction |
| Email `toLowerCase()` locale | `Locale.ROOT` normalization |
| Refresh tokens FK | `REFERENCES auth_users ON DELETE CASCADE` in migration |
| Auth smoke test coverage gaps | Reject rotated token reuse; logout revokes refresh; `/me` without auth → 401 |
| JWT TTL validation | Reject non-positive access token TTL in `JwtTokenService` |
| Gateway public path prefix bypass | Exact match for fixed auth routes; prefix only for `/actuator/` |
| Gateway trusts client `X-User-Id` | Strip inbound header before setting JWT-derived value |
| Course `instructorUserId` in body | Creator becomes instructor via authenticated principal |
| Study-assistant grant `userId` query param | Principal from `@RequestAttribute`; tests drop legacy param |
| `apiFetch` refresh recursion | `skipAuthRefresh` for auth endpoints; module-level `configureApiAuth` |
| Logout refresh race | `skipAuthRefresh` on logout; read refresh token at revoke time |
| Persisted refresh token in `localStorage` | `partialize` excludes `refreshToken` from persistence |
| Sign-in page with existing session | Redirect to `/app` when `accessToken` present |
| Demo auth hard-coded origin | `getApiBase()` for register/login bootstrap |
| Demo fetch `Content-Type` on `FormData` | Only default JSON content type for string bodies |
| JWT TTL / key length validation | Reject non-positive TTL and secrets shorter than 256 bits |
| Gateway strips spoofed `X-User-Id` | `RemoveRequestHeader` default filter + JWT filter overwrite |
| Providers auth bootstrap timing | Synchronous `api-auth.ts` import before router mount |

## Verification

```bash
cd backend && mvn -pl auth-service,community-service,gateway-service -am verify
cd frontend && npm run build
```
