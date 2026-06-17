# Issue 15 Greptile Fix Log: Friend Requests And Direct Messages

Date: 2026-06-17  
Branch: `feature/15-send-friend-request-and-dm`  
PR: https://github.com/Vinosaamaa/chanter/pull/33

## Summary

Greptile reviewed the Friend Request and Direct Message slice and flagged race conditions on user blocks and pending friend requests, a missing block guard on DM reads, missing query indexes, and caller-supplied identity parameters. Iteration 1 addresses the concrete data-layer and authorization gaps; caller identity remains on the existing MVP `TODO(#auth)` posture tracked in issue #30.

## Fixes Applied

### 1. Atomic User Block Upsert

Greptile finding:

- `saveUserBlock` used check-then-insert, which could race under concurrent block requests.

Fix:

- Branch upsert SQL by database product: `ON CONFLICT … DO NOTHING` on PostgreSQL and H2 `MERGE INTO … KEY` in tests.

### 2. Pending Friend Request Race

Greptile finding:

- `sendFriendRequest` could insert duplicate `PENDING` rows under concurrent requests.

Fix:

- Added `@Transactional` on `sendFriendRequest`.
- Added partial unique index `uq_friend_requests_pending_pair` on `(sender_user_id, recipient_user_id) WHERE status = 'PENDING'` in `db/migration-postgresql/` (applied only when Flyway loads `classpath:db/migration-postgresql`; H2 tests use `@Transactional` plus the application-level pending check).
- Map `DataIntegrityViolationException` to `409 Conflict`.

### 3. Block Guard On DM Reads

Greptile finding:

- `findDirectMessages` did not mirror the block check used on send.

Fix:

- Reject reads with `403 Forbidden` when either user has blocked the other.
- Added `blockedUserCannotReadDirectMessages` smoke test.

### 4. Query Indexes

Greptile finding:

- Friend request and direct message tables lacked indexes for common lookup patterns.

Fix:

- Added `V2__add_social_messaging_indexes.sql` with sender/recipient/status and conversation indexes.

### 5. Caller Identity Matches Existing MVP Auth Posture

Greptile finding:

- Request bodies accept caller-supplied user ids, allowing impersonation without an authenticated principal.

Fix:

- Acknowledged as deferred to Auth Service principal slice (`#auth` / issue #30).
- Request records already carry `TODO(#auth)` comments, consistent with issues #12–#14.

## Verification

- `mvn -pl message-service verify`
- PR #33 CI backend/frontend
