# Issue #139 - debug log

## 2026-07-14 — Backend TDD bootstrap

- Wrote failing `CourseLifecycleSmokeTest` and `StudyServerLifecycleSmokeTest` before implementation.
- Flyway V15 initially failed on H2 test profile: multi-column `ALTER TABLE` split into separate statements.
- `JAVA_HOME` must be OpenJDK 21 for Maven `release 21`.

## 2026-07-14 — Frontend wiring

- Removed `sessionStorage` study-server description from `CreateStudyServerV2Page` and `StudyServerHomePage`.
- Draft courses are not in the public catalog; create modal navigates to `/settings` governance route.
- `CourseGovernancePage` uses `studyServerNavigation` owner capability before calling lifecycle APIs.
- ESLint `react-hooks/set-state-in-effect`: avoided syncing form state in `useEffect`; use edit overrides cleared on invalidate.

## 2026-07-14 — API additions during frontend integration

- `PATCH /courses/{id}` for metadata edit (acceptance: edit metadata).
- `GET /study-server-invitations` so invitees can accept without owner-shared IDs.
- `AssignCourseInstructorRequest` accepts optional `instructorEmail` for owner UX parity with enrollment.

## Useful commands

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend && mvn -pl community-service test
cd frontend && npm run lint && npm run test -- --run && npm run build
make product-up
make product-health
```
