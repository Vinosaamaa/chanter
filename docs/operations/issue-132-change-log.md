# Issue #132 - change log

**Branch:** `feature/132-course-capabilities-cohort-context`
**Commit:** `feat(permissions): #132 add explicit course capabilities and cohort context`

## Goal

Replace the overloaded `canViewFullCatalog` authorization inference with backend-issued Study Server, Course, and Cohort capabilities. Give every course workspace one selected Cohort that remains consistent across its chrome, URL, tabs, and cohort-scoped operations.

## What changed

### Backend authorization contract

- Added `cohort_roles` as the canonical Cohort-scoped teaching-assistant assignment.
- Added explicit capability records at Study Server, Course, and Cohort scope.
- Filtered navigation to the Courses and Cohorts the current user can actually access.
- Included Cohort TAs in Study Server membership, member counts, shared membership, and community channel access.
- Aligned Course channel, Questions, TA Queue, Resources, and Office Hours endpoint authorization with the same role model.
- Retained `canViewFullCatalog` only as a deprecated compatibility field; it is owner-only and no production frontend authorization reads it.

```java
public record CourseCapabilities(
        boolean instructor,
        boolean teachingAssistant,
        boolean enrolled,
        boolean canManageCourse,
        boolean canManageQuestions,
        boolean canApproveFaq,
        boolean canManageTaQueue,
        boolean canUploadResources,
        boolean canScheduleOfficeHours,
        boolean canManagePeople
) {}
```

### Frontend capability use

- Extended the shared navigation types with explicit capability objects.
- Replaced catalog-based owner inference in v2 and legacy control gates.
- Gated Teaching by `canTeach`; Billing and Study Server governance remain owner-only.
- Gated Course, Question, Resource, Office Hours, People, Community, and event controls by their action capability.

```tsx
const value: V2CourseWorkspaceContextValue = {
  studyServerCapabilities: navigation.capabilities,
  courseCapabilities: course.capabilities,
  selectedCohort,
  selectCohort,
  isOwner: navigation.capabilities.owner,
}
```

### Cohort context

- Selected Cohort priority is URL query, last local preference, then first accessible Cohort.
- Instructors and owners with multiple Cohorts can switch from the course chrome.
- The canonical `?cohort=` query is written on initial multi-Cohort resolution and preserved by every workspace tab.
- Questions, Office Hours, and People now consume the shared selected Cohort rather than choosing independently.
- Top-bar breadcrumbs and workspace copy resolve from the same URL-backed state.

```tsx
const selectedCohortId = [requestedCohortId, storedCohortId]
  .find((candidate) => course.cohorts.some((cohort) => cohort.id === candidate))
  ?? course.cohorts[0]?.id
```

## Test coverage

Backend public HTTP coverage verifies:

- owner, learner, and unrelated-user navigation;
- an instructor cannot see or manage an unrelated Course;
- a Cohort TA sees only the assigned Cohort and receives only the intended endpoint actions.

Frontend component coverage verifies:

- loading and inaccessible-course boundaries;
- owner and per-Course instructor controls;
- TA question-management access without people-management controls;
- learner access without teaching controls;
- last-Cohort restoration, canonical URL state, and switching.

## Browser verification

Verified against the full local product stack on 2026-07-13:

- Owner: a second local Cohort (`Fall 2026`) appeared in the picker; switching updated the URL, breadcrumb, header, and every workspace tab.
- Owner direct navigation: the remembered Cohort automatically populated `?cohort=` and the breadcrumb without a manual switch.
- Learner: only the enrolled Cohort was visible; Invite and Cohort picker controls were absent.
- Learner direct visits to `/app/teaching` and `/app/settings/billing` redirected to `/app/home`.
- Owner and learner browser consoles had zero error entries.
- The additional `Fall 2026` Cohort was local Docker verification data only and is not part of repository migrations or production seed data.

## Verification

```bash
cd backend
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn -pl community-service test
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home mvn verify

cd ../frontend
npm run lint
npm run test
npm run build
```

Final results:

- community-service: 22 tests passed;
- full Java reactor: `mvn verify` passed with Java 21;
- frontend: 22 test files and 75 tests passed;
- frontend lint passed;
- frontend production build passed with the existing non-blocking Vite chunk-size warning;
- `make product-health` passed for gateway, auth, realtime, and LiveKit.

## Verification diagnostics

- An initial Maven command inherited the machine's older default JDK and failed before compilation with `release version 21 not supported`. Pinning Corretto 21 resolved it.
- The sandboxed full reactor could not let Mockito/Byte Buddy self-attach in `message-service`. The same full reactor passed outside that process sandbox; no application code changed for this environment restriction.
- A sandboxed health probe could not reach localhost ports. The unsandboxed probe confirmed the running stack was healthy.
