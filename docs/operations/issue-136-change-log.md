# Issue #136 - change log

**Branch:** `feature/136-real-cohort-roster-enrollment-ta`
**Commit:** `feat(people): #136 operationalize cohort roster`

## Goal

Replace the fixture-driven v2 People page and UUID-based enrollment form with a truthful Cohort roster: registered-account email enrollment, pending invitations, Cohort TA roles, learner-to-TA assignments, removal, and friend-only direct-message entry.

## What changed

### Verified account directory boundary

- Added internal Auth Service lookups by normalized email and batched user ID.
- Protected both internal endpoints with a shared backend service credential and configured Community Service to send it on every request.
- Kept email out of the public profile API; the internal contract is used only for registered-account resolution and manager-only roster detail.
- Community Service batches profile reads in groups of 100 and exposes a test implementation under the test profile.
- The production HTTP client constructor is explicitly selected by Spring.

```java
@GetMapping("/internal/v1/users/by-email")
public InternalUserProfileResponse findByEmail(@RequestParam String email) {
    return InternalUserProfileResponse.from(authSessionService.requireUserByEmail(email));
}
```

### Durable enrollment, invitations, and TA assignments

- Added `assigned_ta_user_id` to Cohort enrollments and created durable Cohort invitations.
- Enrollment accepts a registered account email; the legacy user-ID field remains temporarily compatible with existing callers.
- Pending invitations resolve atomically when that registered account later enrolls or joins with an invite code.
- Study Server owners and the Course instructor can manage the Cohort roster; learners can view their scoped roster but cannot mutate it.
- Managers can add/remove Cohort TAs, assign one TA to one or many learners, remove enrollments, and cancel pending invitations.
- Removing a TA clears affected learner assignments; removing an enrollment immediately revokes Cohort access.

```java
courseService.assignTeachingAssistant(
        cohortId,
        actorUserId,
        request.learnerUserIds(),
        request.teachingAssistantUserId()
);
```

### Truthful v2 People workflow

- Rebuilt the People page around the real Cohort roster and current capabilities.
- Added loading, error, empty, search, filters, pagination, manager and learner layouts, real counts, and responsive table behavior.
- Added registered-account email enrollment, pending invitation creation/cancellation, TA add/remove, bulk/per-row assignment, and learner removal controls.
- Every roster mutation reports a visible failure; TA removal no longer leaves an unhandled rejected promise.
- Message actions are enabled only for accepted friends and deep-link to the exact friend conversation.
- Replaced the shared Course header's fixture instructor with the canonical roster instructor.
- Removed the fabricated `2 live` presence label and made the header Invite action navigate to the real People workflow.

```ts
const instructorName = roster.data?.instructor.displayName ?? 'Course instructor'

<NavLink to={`${v2CoursePath(serverId, courseId, 'people')}?cohort=${cohortId}`}>
  <UserPlus size={18} /> Invite
</NavLink>
```

### Verified-email onboarding compatibility

- Changed manual Cohort enrollment UI and API calls from learner UUID entry to registered account email.
- Preserved invite-code joining and the existing enrollment response shape.

```ts
await enrollLearner(cohortId, { email: learnerEmail.trim() })
```

## TDD coverage

Red/green cycles cover:

- registered-account email lookup and batched internal profile reads;
- production Spring construction of the Auth directory client;
- manager-only roster reads and mutations, plus learner-safe profile visibility;
- direct enrollment, pending invitation acceptance/cancellation, and duplicate handling;
- TA add/remove and single/bulk learner assignment cleanup;
- enrollment removal and immediate access revocation;
- frontend roster API calls, manager/learner People behavior, and accepted-friend DM deep links;
- manager-visible recovery when TA removal fails;
- canonical Course instructor rendering and the absence of fabricated presence.

## Live browser verification

The full product stack ran through Gateway with PostgreSQL, Redis, Redpanda, MinIO, LiveKit, backend services, and Vite. A real owner, learner, TA, friend, and pending account were created through public APIs.

| View / action | Result |
|---|---|
| Owner People at 1440x900 | Real instructor, TA, learners, pending invitation, assignment, counts, and actions rendered without overlap |
| Owner People at 390x844 | Header, controls, roster groups, and responsive learner table remained within the viewport |
| Course header | Canonical `Professor Rowan`; no `Dr. Alex Johnson` or fabricated `2 live` state |
| Pending invitation | `Pending Riley` displayed as pending with cancel action |
| TA assignment | `Learner Avery` reloaded with persisted `TA Jordan` assignment |
| Learner message action | Accepted friend action enabled and opened `/app/friends?friend=<real-user-id>` |
| Friend conversation at 1280x720 | Exact accepted friend selected; unrelated users were not exposed |

The desktop browser plugin could not initialize, so the visible pass used a separate local headless Chrome instance through the Chrome DevTools Protocol. Details are in `issue-136-debug-log.md`.

## Architecture and docs

- Updated `System Design.md` with the internal account-directory boundary and durable Cohort roster ownership; true email ownership verification remains assigned to #102.
- Updated `HANDOFF.md`, `plan.md`, and `agent-workflow.md` to record #92 merged, #136 locally complete, and #137 next after the PR gate.

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

- Full Java reactor: all 11 modules passed on Java 21.
- Frontend: 42 test files and 141 tests passed.
- Frontend lint and production build passed; the existing informational large-chunk warning remains.
- Desktop, mobile, owner, and learner browser journeys passed against live services and PostgreSQL.
