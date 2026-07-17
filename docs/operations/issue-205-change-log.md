# Issue #205 Change Log — BUG-04: Restrict announcements channel write by role

## Problem

Course and study-server `announcements` channels returned `canPostMessages=true` for every
member, so learners could post in announcement channels. The chat UI also ignored that flag.

## Changes

- Course `announcements`: `canPostMessages` only for course instructors or study-server owners.
- Study-server `announcements`: `canPostMessages` only for the study-server owner.
- Other text channels unchanged (members can still post).
- Frontend fetches channel-message-access and disables the composer when `canPostMessages` is false.
- Smoke tests assert learner read-only / owner-instructor can post on announcements.

## Acceptance

- [x] Learners cannot post on announcements (access API)
- [x] Instructors/owners can post
- [x] Other channels unchanged
- [x] UI disables composer for learners on announcements
- [ ] CI + CodeAnt
- [ ] Browser: owner posts; learner cannot on announcements

## Verification

```bash
cd backend
unset CHANTER_JWT_SECRET CHANTER_INTERNAL_SERVICE_TOKEN
mvn -pl community-service -am test \
  -Dtest=CourseEnrollmentSmokeTest,StudyServerNavigationSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false

cd frontend
npm test -- --run src/features/v2-shell/pages/course/CourseChatPage.test.tsx
```
