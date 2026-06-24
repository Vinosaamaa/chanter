# Issue #19 — CodeRabbit fix log

Date: 2026-06-23  
PR: #37

## Findings addressed

| Severity | File | Action |
|----------|------|--------|
| Critical | `KeywordGroundingEngine.java` | Skip null/blank `textContent`; guard `scoreTerms`/`excerpt` against null |
| Major | `GroundedSupportQuestionService.java` | Idempotent retry via `findBySupportQuestionId`; reconcile status on existing answer; duplicate-key safe `saveAnswer` |
| Major | `StudyAssistantAnswerRepository.java` | Removed redundant `sourceCount` param; derive from `answer.sources().size()` |
| Minor | `GroundedSupportQuestionService.java` | `fileName.toLowerCase(Locale.ROOT)` |
| Trivial | `GroundedSupportQuestionController.java` | Document status string coupling to message-service enum |
| Trivial | `HttpSupportQuestionClient.java` | Pass `actorUserId` UUID directly in request body |

## Deferred

| Severity | File | Reason |
|----------|------|--------|
| Trivial | `V2__create_study_assistant_answer_tables.sql` | No list-by-channel/user query paths in #19 MVP yet |
| Trivial | `KeywordGroundingEngine.java` | Short-term whitelist / min length tuning deferred to FAQ/LLM slices |
| — | Auth query params | `TODO(#auth)` deferred to #30 per project policy |

## Verification

```bash
mvn -pl community-service,message-service,agent-service verify
npm run lint && npm run build
```
