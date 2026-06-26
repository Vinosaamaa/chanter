# Issue #52 Change Log

Issue: [#52 Production `#questions` UX With AI Context Panel](https://github.com/Vinosaamaa/chanter/issues/52)

PR: [#69](https://github.com/Vinosaamaa/chanter/pull/69)

## Summary

Shipped the production `#questions` course-channel experience: Support Question timeline, **Ask AI**, grounded-answer citation cards in the right context panel, low-confidence **Add to TA Queue**, and HTTP 429 quota copy. Generic course channels keep the #51 live-chat path; `#questions` uses a dedicated conversation + hook so realtime subscribe is not invoked on the support-question workflow.

## Backend

### message-service

| Area | Change |
|------|--------|
| `SupportQuestionController` | GET list/get, POST, PATCH status derive caller from `@RequestAttribute(USER_ID)` (gateway JWT), not query/body |
| `CreateSupportQuestionRequest` | Body is `body` + `idempotencyKey` only |
| `SupportQuestionService` | Instructors see full unanswered list; learners see sender-scoped unanswered list via `findByChannelIdAndSenderUserIdAndStatus` |
| `AuthenticatedUserFilter` | Applies to `/support-questions` paths |
| `HttpCourseChannelAccessClient` | Sends `X-User-Id` on inter-service access checks |
| `HttpCohortTaQueueAccessClient` | Same header propagation for TA queue handoff |

### agent-service

| Client | Change |
|--------|--------|
| `HttpSupportQuestionClient` | `X-User-Id` on support-question GET/PATCH to message-service |
| `HttpSupportQuestionChannelAccessClient` | Header-based channel access |
| `HttpStudyAssistantGrantCandidatesClient` | Header-based grant-candidate + viewer-scope calls |

Smoke tests updated for JWT header identity on support-question routes.

## Frontend

| Path | Purpose |
|------|---------|
| `features/questions/questions-api.ts` | Support questions, assistant answer, TA queue, presence APIs |
| `features/questions/support-question-types.ts` | Shared types |
| `features/shell/hooks/use-questions-channel.ts` | History load, post, Ask AI, TA queue, quota handling |
| `features/shell/components/QuestionsChannelConversation.tsx` | `#questions` timeline UI |
| `features/shell/components/QuestionsContextPanel.tsx` | Right **AI context** panel (install status + citations) |
| `features/shell/context/questions-panel-context.tsx` | Selected answer + server id for panel |
| `features/shell/components/ChannelConversation.tsx` | Routes `#questions` to `QuestionsChannelGate`; live chat unchanged |
| `features/shell/shell-routes.ts` | `isQuestionsChannel`, `findCourseChannelContext` |
| `features/shell/layouts/AppShellLayout.tsx` | Wires `ShellContextPanel` |
| `features/dev-demo/DevDemoApp.tsx` | Owner is course instructor in harness (`courseInstructorUserId`); study-assistant preview/install and instructor APIs use owner JWT |
| `features/dev-demo/demo-fetch.ts` | Resolve demo persona from `instructorUserId` and related query params |

### media-service

| Area | Change |
|------|--------|
| `HttpCourseResourceAccessClient` | Send `X-User-Id` on community resource-access calls (fixes agent install-preview 502) |

## Verification

```bash
(cd backend && mvn -pl community-service,message-service,agent-service -am test \
  -Dtest=SupportQuestionSmokeTest,TaQueueSmokeTest,ApprovedFaqSmokeTest,GroundedSupportQuestionSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false)
(cd frontend && npm run lint && npm run build)
```

Manual (Chrome):

1. `make infra-up` + `backend-auth`, `backend-community`, `backend-message`, `backend-agent`, `backend-media`, `backend-gateway`, `frontend-dev`
2. `http://localhost:5173/dev/demo` → create study server + course → **Enroll Learner**
3. **Open app shell as Learner** → **My courses** → `# questions`
4. Post a question; right panel shows Study Assistant install status
5. **Ask AI** on own question (404 expected until assistant installed via dev demo owner flow)
6. Owner flow: **Preview install** → **Confirm install** on `/dev/demo` (requires `media-service`)
7. Optional: upload AI-approved resource → grounded answer + citations + TA queue on low confidence

## Follow-ups

- #53 — course resources context panel
- #54 — support operations UI
- Display names instead of raw user ids in question timeline
