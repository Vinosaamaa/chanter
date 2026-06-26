# Issue #52 CodeRabbit Fix Log

PR: [#69](https://github.com/Vinosaamaa/chanter/pull/69)

## Pass 1 (`56b2dae`)

| Comment | Fix |
|---------|-----|
| Learner list filtered in memory | Sender-scoped DB query `findByChannelIdAndSenderUserIdAndStatus` |
| `HttpCourseChannelAccessClient` missing caller identity | `AuthHeaders.USER_ID` header on outbound access check |
| `#questions` invoked realtime hook | Split `ChannelConversation` — questions channels use `QuestionsChannelGate` only |
| Nested `role="button"` on cards | Removed; explicit **View context** buttons |
| `selectedAnswer` not scoped to channel | Panel ignores answers from other channels |
| `isLoadingHistory` eslint / effect churn | Derive from `loadedHistoryKey` vs `historyRequestKey` |
| Idempotency key regenerated every post | Stable key per draft body per post attempt |

## Pass 2 (`0636bc0`)

| Comment | Fix |
|---------|-----|
| Learner-scoped reads from request-supplied UUID | Controller uses `@RequestAttribute(USER_ID)` from gateway JWT for GET/POST/PATCH |
| Agent inter-service clients missing headers | `HttpSupportQuestionClient`, channel access, grant-candidates clients send `X-User-Id` |
| `UpdateSupportQuestionStatusRequest.actorUserId` dead field | Removed; actor from JWT attribute |
| Smoke tests | `AuthHeaders.USER_ID` on support-question MockMvc calls |

## Pass 3 (`2cf2fdb`)

| Comment | Fix |
|---------|-----|
| Dead `senderUserId` on `CreateSupportQuestionRequest` | Removed from DTO; frontend + smoke tests + dev demo POST body updated |
| Dead `uri.contains("/messages?")` in filter | Removed — `getRequestURI()` never includes query string |

## Pass 4 (browser test fixes)

| Issue | Fix |
|-------|-----|
| Dev demo install-preview 403 | Use owner as `courseInstructorUserId` — course creator is instructor, not separate instructor persona |
| Dev demo JWT persona mismatch | Extend `demo-fetch` resolver for `instructorUserId` / `actorUserId` query params |
| Agent install-preview 502 | `HttpCourseResourceAccessClient` sends `X-User-Id` to community (was query-only after #30 auth) |

## Deferred

| Comment | Reason |
|---------|--------|
| Security thread on `SupportQuestionService` viewer ID | Addressed in pass 2 via JWT-derived controller attribute; thread was stale after push |

## Verification

```bash
cd backend && mvn -pl message-service test -Dtest=SupportQuestionSmokeTest,TaQueueSmokeTest,ApprovedFaqSmokeTest
cd frontend && npm run lint && npm run build
gh pr checks 69   # backend, frontend, CodeRabbit pass
```
