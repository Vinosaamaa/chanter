# Issue #135 - change log

**Branch:** `feature/135-durable-office-hours`
**Commit:** `feat(office-hours): #135 add durable scheduling and live controls`

## Goal

Replace fixture-driven Office Hours with a durable Cohort-scoped workflow: instructors schedule and run sessions, learners join as listeners and raise hands, instructors grant speaking access, and LiveKit tokens enforce the durable permission state.

## What changed

### Durable session lifecycle

- Scheduling derives the instructor from the authenticated principal instead of a request-body user ID.
- Added manager-only edit, cancel, early-start, and end actions.
- Kept the product honest by supporting one-time sessions only; no recurrence control is shown or persisted.
- Ending a session deactivates every participant and removes their voice presence.

```java
@PostMapping("/office-hours/{sessionId}/start")
public OfficeHoursSessionResponse startSession(
        @PathVariable UUID sessionId,
        @RequestAttribute(AuthRequestAttributes.USER_ID) UUID actorUserId
) {
    return OfficeHoursSessionResponse.from(officeHoursService.startSession(sessionId, actorUserId));
}
```

### Durable live participants

- Added Flyway migration `V9__create_office_hours_participants.sql`.
- Active participant rows store listener/speaker permission, raised-hand state, join time, and update time.
- Added direct join, roster, hand, speaking-access, and leave endpoints.
- Enrolled learners join live sessions as listeners; managers join with speaking access.
- Empty boolean control bodies are rejected with HTTP 400 instead of silently meaning `false`.

```java
OfficeHoursParticipant participant = new OfficeHoursParticipant(
        sessionId,
        userId,
        access.canManageOfficeHours(),
        false,
        true,
        now,
        now
);
```

### LiveKit permission enforcement

- Media tokens require an active Office Hours participant.
- `canSpeak` comes from the durable participant row; learners cannot grant it to themselves.
- The frontend refreshes its LiveKit connection when a polled speaking grant changes.
- Leaving disconnects local media and deactivates the durable participant without using the generic Voice Channel leave path.

```ts
const token = await fetchOfficeHoursMediaToken(sessionId)
const connected = await connect(token)
if (connected) setCanSpeak(token.canSpeak)
```

### Operational v2 and legacy surfaces

- Rebuilt the v2 Course Office Hours page around real sessions, participant profiles, loading/error/empty states, and responsive owner/learner layouts.
- Added schedule/edit/cancel/start/end, host audio, mute, listener join, hand, grant/revoke speaking, and leave controls.
- Replaced fixture names and dates with authenticated API data and bounded public profiles.
- Updated the legacy support panel to use the same direct-participant contract so it still compiles and exposes no stale waitlist controls.

## TDD coverage

Red/green cycles cover:

- authenticated scheduler identity and manager-only lifecycle actions;
- invalid lifecycle transitions, including ending scheduled and starting expired sessions;
- direct durable listener join and roster reads;
- raise/lower hand and explicit request validation;
- manager-only speaking grants and LiveKit token permission changes;
- participant leave, ended-session cleanup, and media-token denial after leave;
- v2 durable names/times, schedule payload, grant action, learner hand/leave behavior;
- cross-Cohort session/capability isolation and join success independent of roster polling;
- LiveKit listener connection, permission refresh, and local disconnect.

## Live service verification

The full product stack was restarted with PostgreSQL, Redis, Redpanda, MinIO, LiveKit, backend services, gateway, and Vite. `make product-health` passed.

A two-user smoke through the live gateway used the seeded owner and learner against PostgreSQL:

| Step | Result |
|---|---|
| Instructor schedules and starts | Session became `LIVE` |
| Owner and learner join | Durable roster count became 2 |
| Learner requests media | Token had `canListen=true`, `canSpeak=false` |
| Learner raises hand | Durable roster reflected the hand |
| Instructor grants speaking | New token had `canSpeak=true` |
| Learner leaves and instructor ends | Session became `ENDED`; final roster count was 0 |

Visible browser/audio verification could not run because macOS was locked. Browser-client initialization also failed in the desktop runtime before selection. No application failure was observed; details are recorded in `issue-135-debug-log.md`.

## Architecture and docs

- Updated `System Design.md` because #135 adds a durable participant model and makes Community Service the authorization source for LiveKit speaking permission.
- Updated `HANDOFF.md`, `plan.md`, and `agent-workflow.md` to record #109 merged, #135 active/locally complete, and #92 next after the PR gate.

## Verification

```text
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -pl community-service -am test
cd frontend && npm run lint
cd frontend && npm run test
cd frontend && npm run build
git diff --check
make product-health
```

Final local results before commit:

- Community Service reactor: 34 tests passed, including 16 Office Hours tests.
- Full Java reactor: all 11 modules passed `mvn verify` on Java 21.
- Frontend: 36 test files and 120 tests passed.
- Frontend lint and production build passed; the existing informational large-chunk warning remains.
- Live two-user gateway/LiveKit-token smoke passed.
