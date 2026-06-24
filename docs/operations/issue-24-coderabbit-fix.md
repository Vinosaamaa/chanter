# Issue #24 — CodeRabbit fix log

Date: 2026-06-21  
PR: #46 (`feature/24-enforce-saas-plan-limits`)

## Findings addressed

| Severity | File | Action |
|----------|------|--------|
| Minor | `JdbcAiUsageMetricsRepository.java` | Collapsed total + handoff counts into one aggregate query with `COUNT(*) FILTER` |
| Critical | `AiQuotaEnforcementService.java`, `StudyAssistantAnswerPersistenceService.java` | Quota check runs under a PostgreSQL advisory transaction lock immediately before audit insert in the same `@Transactional` boundary as `saveAnswer` |
| Critical | `AiQuotaEnforcementService.java` | `pg_advisory_xact_lock` uses `.query(Long.class).single()` so the lock SQL actually executes |
| Major | `JdbcSaasPlanRepository.java`, `SaasPlanService.java` | Replaced separate owner check + update with atomic `updatePlanTierIfOwner` |
| Minor | `App.tsx` | Sync `studyServer.planTier` after successful SaaS Plan PATCH (fixed in `a05602e`) |
| Minor | `UpdateSaasPlanRequest.java` | Documented `TODO(#auth)` on caller-supplied `ownerUserId` |

## Deferred

| Severity | File | Reason |
|----------|------|--------|
| Critical | `UpdateSaasPlanRequest.ownerUserId` | `TODO(#auth)` until #30 — same demo harness pattern as #16–#23 |
| Minor | H2 test profile advisory lock | `pg_advisory_xact_lock` is PostgreSQL-only; smoke tests remain single-threaded on H2 |

## Verification

```bash
mvn -pl community-service,agent-service,analytics-service verify
npm run lint && npm run build
```

Browser demo (verified 2026-06-21): SaaS Plan upgrade STARTER → PRO; dashboard quota meter updates; assistant invoke blocked at limit (HTTP 429).
