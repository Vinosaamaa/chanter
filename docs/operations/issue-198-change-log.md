# Issue #198 Change Log — SEC-17: Media upload content-type allowlist

## Problem

Course resource upload accepted any/empty content type (blank became `application/octet-stream`). Downloads called `MediaType.parseMediaType` on stored values with no fallback.

## Changes

- `CourseResourceService`: allowlist of document/audio/video MIME types; reject blank and disallowed types with `400`.
- `CourseResourceController.safeContentType`: parse stored type safely; fall back to `application/octet-stream`.
- Smoke tests: reject HTML/blank; accept PDF; unit assert parse fallback.

## Acceptance

- [x] Upload allowlist enforced
- [x] Download parse guarded
- [ ] CI + CodeAnt
- [ ] Browser: owner upload allowed type; rejected type fails cleanly
