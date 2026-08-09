# Issue #239 debug log: Invited member navigation 403

**Date:** 2026-08-09
**Issue:** [#239](https://github.com/Vinosaamaa/chanter/issues/239)

## 1. Symptom

After a user accepted a Study Server invitation, the server appeared in the accessible list but loading its navigation returned 403. Home also returned 403 because Home aggregates navigation for every accessible Study Server.

Observed contract disagreement:

```text
GET /api/v1/study-servers                 200, server included
GET /api/v1/study-servers/{id}/navigation 403
GET /api/v1/me/home-summary               403
```

## 2. Root cause

The accessible-server query recognizes the `STUDY_SERVER_MEMBER` relationship. `StudyServerNavigationService`, however, delegated its entrance check to `StudyAssistantGrantService.findViewerScope`. That narrower scope exists only for an Owner, Instructor, TA, or enrolled Learner.

The navigation endpoint therefore used an AI/Course authorization predicate to guard server-wide community navigation. `HomeSummaryService` inherited the same mismatch because it calls navigation for each server returned by the accessible list.

## 3. Test-first reproduction

The invitation lifecycle test was extended before production code changed. It created a Course, invited a user, accepted the invitation, and requested navigation as the generic member.

The red run failed at the new assertion:

```text
Expected status: 200
Actual status:   403
Error: Study Assistant presence requires Study Server membership or enrollment.
```

Creating a Course in the fixture made the visibility boundary explicit: fixing server membership must not accidentally reveal Course/Cohort content.

## 4. Fix and authorization reasoning

Navigation now:

1. Resolves the Study Server or returns 404.
2. Checks canonical Study Server membership or returns 403.
3. Uses the existing role/Enrollment viewer scope when present.
4. Uses an empty Course/Cohort scope for a generic member.
5. Applies the existing channel, Course, Cohort, role, and capability filters.

The AI scope itself was not widened. This matters because a generic community member is entitled to server-wide channels but not automatically to Course resources or Study Assistant access.

The green run passed the invitation path. Existing Owner, Instructor, TA, enrolled Learner, Home, and stranger tests also passed, followed by all 93 community-service tests and the 12-module Maven reactor.

## 5. Browser runner investigation

### Missing pinned browser

The repository Playwright run initially stopped before the application test because the expected managed Chromium headless shell was absent. `npx playwright install chromium` downloaded Chromium but stalled while finalizing on this managed machine. Only installer processes started during this investigation were stopped.

### System Chrome fallback

A temporary Playwright config outside the repository selected the installed Google Chrome executable while retaining the repository test directory, base URL, reporter, and trace behavior. This exercised the real local product stack without changing product configuration.

### Assertion correction

The first system-Chrome run reached authenticated Home successfully but an old generic heading assertion expected `home`, `welcome`, or `courses`. The actual page heading is time-aware, such as `Good morning, Demo`. The regression was changed to assert the real heading pattern and to wait for loading states to clear.

The rerun passed and observed zero API responses with status 400 or higher while learner Home and sidebar data loaded.

## 6. Maven sandbox false negative

The first full `mvn -B verify` run failed in gateway tests with `SocketException: Operation not permitted` while binding an ephemeral localhost port. The same reactor was rerun with approved host permissions and passed with exit code 0. This was process sandbox isolation, not an application failure.

## 7. Final result

- Accepted generic member: navigation, general channel access, and Home return 200.
- Generic member: no unauthorized Course/Cohort content.
- Owner, Instructor, TA, and enrolled Learner behavior remains green.
- Stranger remains unable to list or navigate the Study Server.
- Signed-in browser regression: zero unexpected API 4xx/5xx responses.
