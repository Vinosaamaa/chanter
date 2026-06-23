# Issue 17 CodeRabbit Fix Log: Upload Approved Course Resource

Date: 2026-06-22  
Branch: `feature/17-upload-approved-course-resource`  
PR: https://github.com/Vinosaamaa/chanter/pull/35

## Summary

CodeRabbit CLI reviewed the issue #17 branch diff against `main` and reported 24 findings (1 critical, 8 major, 15 trivial). This log records the actionable fixes applied without opening the deferred `TODO(#auth)` impersonation work.

## Fixes Applied

### 1. Community Access Client Timeouts And Error Mapping

CodeRabbit finding:

- `HttpCourseResourceAccessClient` used a default `RestClient` with no connect/read timeouts and only handled 404/403, letting other client failures surface as generic 500s.

Fix:

- Configured `JdkClientHttpRequestFactory` with connect/read timeouts (`chanter.community-service.connect-timeout-seconds`, `read-timeout-seconds`).
- Mapped other 4xx responses, 5xx responses, and transport failures to `502 Bad Gateway` with stable messages.

### 2. Upload Filename Sanitization

CodeRabbit finding:

- `getOriginalFilename()` was stored verbatim, allowing path segments such as `../../notes.txt`.

Fix:

- Added `CourseResourceService.sanitizeFileName()` to keep only the final path segment and reject `.` / `..`.
- Added smoke coverage for a multipart upload whose original filename contains parent-directory segments.

### 3. Download Edge-Case Smoke Tests

CodeRabbit finding:

- List/upload forbidden paths were covered, but download 403/404 cases were missing.

Fix:

- Added `unauthorizedUserCannotDownloadCourseResource` and `downloadingUnknownCourseResourceReturnsNotFound`.

### 4. Storage Failure Status On Download

CodeRabbit finding:

- Storage `IOException` during download was mapped to `404 Not Found` even though it represents a server-side failure.

Fix:

- Return `500 Internal Server Error` when local storage cannot be read.

### 5. Upload Transaction Cleanup Simplification

CodeRabbit finding:

- `repository.deleteById()` after a failed `storage.store()` is redundant inside `@Transactional` because the thrown `ResponseStatusException` rolls back the insert.

Fix:

- Removed the explicit `deleteById` call and rely on transaction rollback.

## Deferred (Documented, Not Fixed In #17)

| Finding | Reason |
|---------|--------|
| `userId` / `uploaderUserId` / `viewerUserId` query params | Matches the existing no-auth demo harness (`TODO(#auth)`) across services |
| Streaming downloads / storage-layer size cap | 10 MB service limit makes `readAllBytes()` acceptable for this slice |
| Relative `storage-dir` default | Local-dev default; production path belongs in deploy config |
| V1 migration CHECK/UNIQUE, Hikari tuning, Mockito config, SQL micro-optimizations | Low priority for MVP merge |

## Prior Review Context

Greptile fixes on this branch are documented in `docs/operations/issue-17-greptile-fix.md`. CodeRabbit did not re-flag the already-fixed `Content-Disposition` and `aiApproved` items.
