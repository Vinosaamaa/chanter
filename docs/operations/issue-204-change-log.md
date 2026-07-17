# Issue #204 Change Log — BUG-03: Escape LIKE wildcards in cohort learner search

## Problem

`JdbcCourseRepository.listCohortEnrollments` wrapped the search term in `%…%` without
escaping `%` / `_`, so those characters acted as SQL LIKE wildcards (over-broad matches).

## Changes

- Escape `\`, `%`, and `_` via `escapeLikePattern` (same approach as approved-FAQ search).
- Use `LIKE … ESCAPE '\'` in the enrollment search filter.
- Extend smoke test: search `%` returns zero enrollments.

## Acceptance

- [x] `%` / `_` treated literally in cohort enrollment search
- [x] Existing substring UUID search still works
- [ ] CI + CodeAnt
- [ ] Browser: cohort learner search with `%` does not list everyone

## Verification

```bash
cd backend
unset CHANTER_JWT_SECRET CHANTER_INTERNAL_SERVICE_TOKEN
mvn -pl community-service -am test -Dtest=CourseEnrollmentSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
```
