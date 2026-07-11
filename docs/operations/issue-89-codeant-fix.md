# Issue #89 — CodeAnt fix log

**PR:** #113  
**Branch:** `feature/89-study-server-enrollment-polish`

## Pass 1

- Triggered `@codeant-ai: review` on PR open.
- CI + quality gates passed.
- **Finding:** unbounded enrollment list query (`JdbcCourseRepository.java`).
- **Fix:** add `limit`/`offset` query params (default 50, max 500), `totalCount` in response, and server-side pagination on enrollment page.
