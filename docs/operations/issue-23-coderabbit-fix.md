# Issue #23 — CodeRabbit fix log

Date: 2026-06-24  
PR: #44

## Findings addressed

| Severity | File | Action |
|----------|------|--------|
| Critical | `AiUsageMetricsRepository.java` | Added missing `AiUsageMetrics` import (commit `114062e`) |
| Major | `HttpAgentServiceClient.java`, `HttpCommunityServiceClient.java`, `HttpMessageServiceClient.java` | Wired `setReadTimeout` on all analytics HTTP clients (commit `114062e`) |
| Major | `AgentServiceClientProperties.java`, `CommunityServiceClientProperties.java`, `MessageServiceClientProperties.java` | `@Validated` + `@NotBlank` / `@Positive` constraints |
| Major | `HttpAgentServiceClient.java`, `HttpCommunityServiceClient.java`, `HttpMessageServiceClient.java` | Map 4xx/5xx/network failures to gateway-class errors |
| Major | `InstructorDashboardMetricsService.java` | `allMatch` authorization for channels, cohorts, and courses |
| Minor | `InstructorDashboardControllerTest.java` | Assert full dashboard response contract |
| Minor | `App.tsx` | Clear stale dashboard metrics before reload |
| Minor | `issue-23-change-log.md` | Added PR and merge-tracking metadata |

## Deferred

| Severity | File | Reason |
|----------|------|--------|
| Major/Critical | `InstructorDashboardController.java` (analytics), `InstructorDashboardController.java` (community) | Caller `viewerUserId` / `userId` from query params is `TODO(#auth)` until #30 — same pattern as #16–#22 |

## Verification

```bash
mvn -pl analytics-service,community-service,message-service,agent-service verify
npm run lint && npm run build
```
