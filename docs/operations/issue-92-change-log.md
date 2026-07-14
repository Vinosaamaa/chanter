# Issue #92 - change log

**Branch:** `feature/92-operational-questions-teaching`
**Commit:** `feat(support): #92 operationalize questions and teaching`

## Goal

Replace fixture/local-only behavior in the v2 Questions workspace and Teaching dashboard with durable Support Question, AI answer, TA Queue, Approved FAQ, Office Hours, and Instructor Dashboard contracts.

## What changed

### Durable human support threads

- Added `support_question_replies` with a question foreign key, staff author, body, timestamp, and ordered lookup index.
- Added principal-derived reply create/list endpoints.
- Restricted replies to Course instructors/TAs and reads to the question author or support staff.
- Added `HUMAN_ANSWERED`, `RESOLVED`, `CANCELLED`, and `DUPLICATE` states plus staff-only terminal moderation.
- Status transitions use compare-and-set repository updates so concurrent replies/moderation return `409` instead of overwriting newer state.

```java
@PostMapping("/{channelId}/support-questions/{supportQuestionId}/replies")
public ResponseEntity<SupportQuestionReplyResponse> postReply(
        @PathVariable UUID channelId,
        @PathVariable UUID supportQuestionId,
        @RequestAttribute(AuthRequestAttributes.USER_ID) UUID authorUserId,
        @Valid @RequestBody CreateSupportQuestionReplyRequest request
) {
    SupportQuestionReply reply = supportQuestionService.postReply(
            channelId, supportQuestionId, authorUserId, request.body()
    );
    // Build the created response and Location header.
}
```

### Principal-derived TA Queue and FAQ operations

- Removed learner, TA, approver, and viewer identity fields from public mutation bodies.
- TA Queue, FAQ, Support Question, AI answer, and Instructor Dashboard controllers now use the authenticated gateway principal.
- Added missing TA Queue and FAQ paths to Message Service authentication filtering.
- Queue resolution atomically closes the associated Support Question as `RESOLVED`.
- Queue insertion verifies that the question channel and Cohort belong to the same Course.
- Direct question moderation closes any active Queue item in the same transaction.
- Instructor Dashboard message metrics ignore body identity and authorize only the authenticated principal.

```java
boolean questionUpdated = supportQuestionRepository.updateStatus(
        existing.supportQuestionId(),
        supportQuestion.status(),
        SupportQuestionStatus.RESOLVED
);
if (!questionUpdated) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question status has changed");
}
```

### Persisted AI answers and resilient grounding

- AI invocation derives the learner from `X-User-Id`; the client no longer chooses the actor.
- Added authenticated `GET .../assistant-answer` so refreshes and later staff replies do not erase the persisted answer or citations.
- Answer reads are limited to the question author or Course support staff and verify channel identity.
- A missing, forbidden, or temporarily unavailable granted resource is skipped; the assistant returns a durable low-confidence handoff instead of propagating a gateway `502`.

```java
} catch (ResponseStatusException exception) {
    if (exception.getStatusCode() == HttpStatus.NOT_FOUND
            || exception.getStatusCode() == HttpStatus.FORBIDDEN
            || exception.getStatusCode() == HttpStatus.BAD_GATEWAY) {
        continue;
    }
    throw exception;
}
```

### Real Teaching identities and deep links

- Instructor Dashboard responses now include real Course, Cohort, and questions-channel IDs with per-Course support metrics.
- Dashboard aggregation skips server-visible Courses outside a partial instructor's teaching scope instead of failing the entire page.
- Cohort summaries include Queue counts, so `View queues` opens the Cohort that actually has waiting work.
- Teaching derives every card and shortcut from live dashboard and Office Hours data.
- Questions, Resources, Office Hours, and People links preserve the selected real Cohort; no `course-demo` route or fixture count remains.
- Office Hours Join uses the real live session delivered by issue #135.

```java
public record TeachingCohortResponse(
        UUID cohortId,
        String name,
        int openTaQueueItems
) {
}
```

### Operational v2 Questions workspace

- Lists durable questions with Open, Answered, and Mine filters and real public profile names.
- Learners can post, invoke AI, inspect persisted citations, and route low-confidence answers to the TA Queue.
- Instructors/TAs can pick up queue items, post durable replies, resolve/cancel/duplicate questions, and approve FAQs.
- Filter and thread selection remain aligned when a mutation moves a question between Open and Answered.
- Resolving a TA Queue item refreshes the adjacent Support Question state.
- Thread changes clear thread-specific drafts, and late mutation responses cannot erase a newer draft.
- Late reply history reads merge by reply ID and cannot overwrite a reply posted while the request was in flight.
- Cohort changes hide stale Queue items until the new Cohort request completes.
- Staff Queue actions select the linked question before pickup or resolution.
- FAQ citations are identified by source metadata instead of citation order, and FAQ candidates expose selected state.
- Citation `resource` and Teaching `session` query parameters now select and highlight the exact linked Resource or scheduled Office Hours session.
- Mark helpful remains explicitly disabled and deferred to #100.

```ts
const activeFilter = selected && filter !== 'mine' &&
    !requestedQuestions.some((question) => question.id === selected.id)
  ? (OPEN_STATUSES.includes(selected.status) ? 'open' : 'answered')
  : filter
```

## TDD coverage

Red/green cycles cover:

- instructor/TA-only durable replies and author/staff reads;
- terminal moderation, closed-question reply rejection, and status races;
- authenticated TA Queue/FAQ actors and queue-to-question resolution;
- cross-Course Queue rejection, direct-moderation Queue closure, and authenticated dashboard identity;
- persisted answer authorization and answer reload after resolution;
- unavailable resource fallback to a low-confidence handoff;
- partial-instructor dashboard scope, per-Cohort Queue metrics, and exact Cohort/session deep links;
- Questions durable rendering, teaching replies, filter/thread alignment, Queue selection, draft races, stale reply reads, and Queue-resolution refresh.

## Live browser verification

The full product stack ran through Gateway with PostgreSQL, Redis, Redpanda, MinIO, LiveKit, backend services, and Vite.

| View / action | Result |
|---|---|
| Teaching at 1440x900 | Real server/course metrics and exact deep link; no horizontal overflow |
| Questions at 1440x900 | Real three-pane instructor view, durable replies, FAQ and TA panels |
| Teaching at 390x844 | Single-column cards and internal vertical scroll; no horizontal overflow |
| Questions at 390x844 | Stacked list/thread/tools; no horizontal overflow |
| Learner grounded question | Persisted AI answer displayed a real `Homework Help Guide` citation and resource deep link |
| Learner low-confidence question | `AI_LOW_CONFIDENCE` answer remained selected and was added to the TA Queue |
| Instructor support loop | Pick up, durable staff reply, queue resolve, and final durable `RESOLVED` state passed |
| Post-review restart | Questions and Teaching reloaded against rebuilt services; no `403`, stale Queue item, or horizontal overflow |
| Citation deep link | Opened `Homework Help Guide` by its real resource ID and highlighted the matching row; no overflow |

The desktop browser plugin could not initialize and macOS UI automation was locked, so the visible pass used a separate local headless Chrome instance over the Chrome DevTools Protocol. Details are in `issue-92-debug-log.md`.

## Architecture and docs

- Updated `System Design.md` because #92 establishes durable Support Question/reply ownership, terminal state transitions, answer ownership, and TA Queue resolution semantics.
- Updated `HANDOFF.md`, `plan.md`, and `agent-workflow.md` to record #135 merged, #92 locally complete, and #136 next after the PR gate.

## Verification

```text
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
cd frontend && npm run lint
cd frontend && npm run test
cd frontend && npm run build
git diff --check
make product-health
```

Final local results before commit:

- Full Java reactor: all 11 modules passed on Java 21; no Surefire failures or errors.
- Frontend: 40 test files and 135 tests passed.
- Frontend lint and production build passed; the existing informational large-chunk warning remains.
- Desktop and mobile responsive browser checks passed.
- Signed-in learner/instructor support loop passed through the live Gateway and PostgreSQL.
- Residual cross-service AI invocation/status races remain assigned to the streaming and audit-trail work in #100; #92 does not introduce a distributed transaction.
