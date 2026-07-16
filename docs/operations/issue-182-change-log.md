# Issue #182 change log — agent-service gateway identity headers (SEC-02)

**Branch:** `cursor/182-agent-gateway-identity-1b60`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-02

## Summary

Study Assistant install/preview/presence and AI usage metrics no longer accept caller-supplied `instructorUserId` / `viewerUserId`. The actor is taken from gateway-injected `X-User-Id`, enforced by a new `AuthenticatedUserFilter` on agent-service (public `/api/v1/**` only; internal routes unchanged).

## Changes

| Area | Change |
|------|--------|
| `AuthenticatedUserFilter` | New filter (notification-service pattern) |
| `StudyAssistantController` / `AiUsageMetricsController` | `@RequestHeader(AuthHeaders.USER_ID)` |
| `InstallStudyAssistantRequest` | Dropped `instructorUserId` body field |
| Analytics `HttpAgentServiceClient` | Sends `X-User-Id` instead of query param |
| Frontend study-assistant + questions APIs | Stop sending identity query/body fields |
| Seed script / README | Updated to header-based identity |
| Smoke tests | Headers required; missing header → 401; spoofed query ignored |

## Tests

```bash
cd backend && mvn -B -pl agent-service,analytics-service -am test
cd frontend && npm test -- --run src/features/study-assistant/study-assistant-api.test.ts
```
