# Issue #138 - change log

**Branch:** `feature/138-community-course-discovery-enrollment`
**Commit:** `feat(discovery): #138 operationalize Course discovery and enrollment`

## Goal

Replace the fixture-driven Community Discover page with a real, server-scoped published Course catalog and durable open/invite enrollment while preserving the approved Journey 5e UI and responsive behavior.

## What changed

### Published catalog and enrollment policy

- Added durable `courses.published` and `cohorts.enrollment_policy` state in Community Service.
- New Cohorts default to `OPEN`; upgraded Cohorts remain `INVITE_ONLY` so deployment does not silently weaken an existing invite boundary.
- Supported policies are `OPEN`, `INVITE_ONLY`, `OPENING_SOON`, and `CLOSED`, enforced by a database check constraint.
- Added a server-scoped catalog endpoint with backend search and `ALL`, `ENROLLED`, `OPEN`, and `OPENING_SOON` filters.
- Catalog rows contain real Course/Cohort IDs, instructor IDs, enrollment state, learner counts, and policy; unpublished Courses never appear.

```sql
ALTER TABLE cohorts ADD COLUMN enrollment_policy VARCHAR(32) DEFAULT 'INVITE_ONLY';
UPDATE cohorts SET enrollment_policy = 'INVITE_ONLY' WHERE enrollment_policy IS NULL;
ALTER TABLE cohorts ALTER COLUMN enrollment_policy SET NOT NULL;
ALTER TABLE cohorts ALTER COLUMN enrollment_policy SET DEFAULT 'OPEN';
```

```java
@GetMapping("/{id}/course-catalog")
public CourseCatalogResponse getCourseCatalog(
        @PathVariable UUID id,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "ALL") CourseCatalogFilter filter,
        @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
) {
    return CourseCatalogResponse.from(courseService.findCourseCatalog(id, userId, search, filter));
}
```

### Truthful joining

- `POST /api/v1/cohorts/{cohortId}/join` now accepts an empty body for an open Cohort.
- Open enrollment requires Study Server membership unconditionally; an invite code never substitutes for that boundary.
- Invite-only Cohorts require the exact durable invite code.
- Opening-soon and closed Cohorts return `409`; unpublished or unknown Cohorts return `404`.
- Enrollment remains idempotent through the existing persistence boundary.

```java
switch (joinDetails.enrollmentPolicy()) {
    case OPEN -> {
        boolean member = courseRepository.listAccessibleStudyServers(learnerUserId).stream()
                .anyMatch(server -> server.id().equals(joinDetails.studyServerId()));
        if (!member) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Open Cohort enrollment requires Study Server membership");
        }
    }
    case INVITE_ONLY -> {
        if (!validInvite) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid cohort invite code");
        }
    }
    case OPENING_SOON -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Cohort enrollment is not open yet");
    case CLOSED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Cohort enrollment is closed");
}
```

### Real v2 Discover page

- Replaced synthetic cards, local-only joining, fake instructor application, and request-access controls with TanStack Query data from the catalog API.
- Added backend-driven search/filter state, real instructor profile batching, real learner counts, multi-Cohort selection, and explicit loading/error/retry/empty states.
- Open Cohorts expose Join; invite-only Cohorts expose invite entry; unavailable policies have no dead action.
- Successful joining invalidates catalog, Study Server navigation, and accessible-server caches before navigating to the exact Course/Cohort URL.
- Owner Course creation retains only fields the backend persists and refreshes the real catalog.
- The hub header Course count comes from the full published catalog instead of the viewer's narrower navigation list or a fixture fallback.
- Invite and create dialogs focus the first field, trap Tab, close with Escape when idle, and restore focus to the trigger.

```ts
await Promise.all([
  queryClient.invalidateQueries({ queryKey: ['course-catalog', serverId] }),
  queryClient.invalidateQueries({ queryKey: ['study-server-navigation'] }),
  queryClient.invalidateQueries({ queryKey: ['study-servers'] }),
])
navigate(`${v2CoursePath(serverId, course.id)}?cohort=${encodeURIComponent(cohort.id)}`)
```

### Responsive fidelity

- Preserved the Journey 5e two-column desktop catalog and one-column mobile layout.
- Added scoped styling for Cohort selectors, real Course links, unavailable/status states, and modal errors.
- Filters wrap inside the viewport at mobile widths; cards and dialogs remain scrollable without document-level horizontal overflow.

## TDD coverage

Red-green-refactor cycles cover:

- authenticated, server-scoped published catalog reads with real counts and instructor identity;
- backend search and each supported filter;
- open joining without an invite and the resulting enrolled projection;
- denial when an outsider presents a valid invite for an open Cohort;
- invite-only success/failure, opening-soon conflicts, hidden Courses, and outsider denial;
- migration preservation of legacy invite-only state plus the new open default;
- frontend API paths and open/invite payloads;
- real card rendering, policy actions, exact navigation, and cache invalidation;
- backend-driven search/filter controls and retryable errors;
- keyboard dialog dismissal and trigger-focus restoration;
- truthful full-catalog count in Community chrome.

## Live browser verification

The normal product stack ran through Gateway with PostgreSQL, Redis, Redpanda, MinIO, LiveKit, backend services, and Vite.

| View / action | Result |
|---|---|
| Owner and learner sign-in | Both authenticated through the production UI |
| Owner create Course | UI-created Course persisted with an `OPEN` Cohort and refreshed the catalog |
| Header count | Matched the real three-Course published catalog |
| Search + Open filter | Returned the exact new Course from the backend |
| Open enrollment | Learner joined without an invite and landed on the exact Course/Cohort |
| Enrolled filter | Refreshed card showed Enrolled plus the exact Open Course link |
| Invite enrollment | Valid code joined the invite-only Cohort and navigated exactly |
| Dialog keyboard flow | Initial focus, Escape close, Tab containment, and trigger restoration passed |
| Desktop `1440x900` | Two-column grid matched Journey 5e hierarchy and remained scrollable |
| Mobile `390x844` | Controls wrapped, cards stacked, and no document overflow occurred |
| Runtime diagnostics | Zero browser console errors and zero API responses at `400+` |

Screenshots and the resumable browser runner live under `.product/` and are intentionally gitignored.

## Verification

```text
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -pl community-service -am test
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
make product-health
```

Local results before commit:

- Full Java reactor passed across all backend modules on Java 21.
- Community Service and neighboring module tests passed on Java 21 with all 15 Flyway migrations.
- Frontend lint passed; 50 test files and 164 tests passed; production build passed with only the existing large-chunk warning.
- Disposable PostgreSQL 16 upgraded a legacy Cohort to `INVITE_ONLY`, defaulted a new Cohort to `OPEN`, and created the discovery index.
- The persistent Community database was backed up under `.product/backups/`, upgraded from V13 to V14, and remained healthy with all 45 legacy Cohorts invite-only.
- Desktop and mobile browser smoke passed with no console or API errors.

## Related docs

- Debugging details: `docs/operations/issue-138-debug-log.md`
- Durable ownership model: `System Design.md`
- Progress tracking: `HANDOFF.md`, `plan.md`, and `docs/operations/agent-workflow.md`
