# Issue 18 CodeRabbit Fix Log: Install AI Study Assistant

Date: 2026-06-22  
Branch: `feature/18-install-ai-study-assistant`  
PR: https://github.com/Vinosaamaa/chanter/pull/36

## Summary

CodeRabbit reviewed PR #36 and reported 13 unresolved findings. This log records the actionable fixes applied without opening the deferred `TODO(#auth)` impersonation work (issue #30).

## Fixes Applied

### 1. Skip Resource Catalog Lookups For Instructors

CodeRabbit finding:

- `findPresence` always called `resourceCourseIdsForGrants`, which fans out to media-service even when `canViewAllGrants` short-circuits filtering.

Fix:

- Return an empty `resourceCourseIds` map when `canViewAllGrants` is true.

### 2. HTTP Client Null-Safety And Exception Causes

CodeRabbit finding:

- `HttpCourseResourceCatalogClient` and `HttpStudyAssistantGrantCandidatesClient` could NPE on null JSON list fields and dropped upstream causes on `502` mapping.

Fix:

- Null-safe list handling before `stream()` / `Set.copyOf()`.
- Pass caught exceptions as the cause on `ResponseStatusException` for non-403 paths.

### 3. Duplicate Install Conflict Cause

CodeRabbit finding:

- `JdbcStudyAssistantRepository` mapped `DuplicateKeyException` to `409` without preserving the cause.

Fix:

- Include the `DuplicateKeyException` as the `ResponseStatusException` cause.

### 4. Grant Type CHECK Constraint

CodeRabbit finding:

- `study_assistant_grants.grant_type` accepted arbitrary strings.

Fix:

- Added a `CHECK` constraint on the five `GrantType` enum values in `V1__create_study_assistant_tables.sql`.

### 5. Test Double Viewer Filtering

CodeRabbit finding:

- `TestCourseResourceCatalogClient` ignored `viewerUserId`, so presence tests did not exercise learner resource scope.

Fix:

- Added optional per-course viewer allow-lists and `grantViewerAccess()` registration in smoke tests.

### 6. Learner Scope Negative Assertion

CodeRabbit finding:

- Smoke tests lacked coverage that learners do not see grants outside enrollment.

Fix:

- Added `learnerPresenceHidesGrantsOutsideEnrollmentScope`.

### 7. Grant-Candidate Query Batching

CodeRabbit finding:

- `findGrantCandidates` issued ~2 + 3N queries via per-course lookups.

Fix:

- Replaced N+1 loading with batched cohort and course-channel queries keyed by study server.

### 8. Frontend Preview / Confirm Guard

CodeRabbit finding:

- Confirm install could post grants from a preview tied to a different Study Server.

Fix:

- Guard confirm on `studyAssistantPreview.studyServerId === studyServer.id` and disable the confirm button when they differ.

## Deferred (Documented, Not Fixed In #18)

| Finding | Reason |
|---------|--------|
| `instructorUserId` / `viewerUserId` / `userId` query params | Matches the existing no-auth demo harness (`TODO(#auth)`) across services; deferred to issue #30 |
| README auth wording | Clarified as demo-harness params with explicit `TODO(#auth)` deferral instead of implementing auth in #18 |

## Verification

- `mvn -pl community-service,agent-service verify`
