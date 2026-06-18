# Issue 17 Greptile Fix Log: Upload Approved Course Resource

Date: 2026-06-18  
Branch: `feature/17-upload-approved-course-resource`  
PR: https://github.com/Vinosaamaa/chanter/pull/35

## Summary

Greptile reviewed the media-service slice at 3/5 confidence and flagged orphaned filesystem writes on DB rollback, unsafe `Content-Disposition` header construction, and a caller-controlled `aiApproved` default.

## Fixes Applied

### 1. Persist Metadata Before Writing Files

Greptile finding:

- `storage.store()` ran before `repository.save()` inside `@Transactional`, so a DB failure left orphan files on disk.

Fix:

- Save the `course_resources` row first, then write bytes to local storage.
- On storage failure, delete the new row via `CourseResourceRepository.deleteById`.

### 2. RFC 6266 Content-Disposition Encoding

Greptile finding:

- Download responses concatenated the raw filename into a quoted `filename="..."` header.

Fix:

- Build the header with Spring `ContentDisposition.attachment().filename(name, UTF_8)`.

### 3. Require Explicit `aiApproved` On Upload

Greptile finding:

- `aiApproved` defaulted to `true`, so clients could omit the flag and every upload appeared AI-approved.

Fix:

- Removed `defaultValue = "true"`; upload requests must send `aiApproved` explicitly (demo frontend already does).

### 4. CI Timestamp Precision In Smoke Test

Pre-Greptile CI failure:

- Upload JSON kept nanosecond `createdAt` while list responses round-trip through Postgres at microsecond precision.

Fix:

- Compare list items with recursive equality ignoring `createdAt`, then assert microsecond-truncated timestamps match.
