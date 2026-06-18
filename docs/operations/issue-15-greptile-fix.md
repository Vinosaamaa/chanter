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
- Added partial unique index `uq_friend_requests_pending_pair` on `(sender_user_id, recipient_user_id) WHERE status = 'PENDING'` in `db/migration-postgresql/` for production PostgreSQL.
- H2 smoke tests load `db/migration-h2/` with a generated `pending_pair_key` column normalized across user-pair direction to mirror the PostgreSQL partial unique constraint.
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

### 6. Block Guard On Friend Requests And Accept/Decline Races

Greptile finding (iteration 2):

- Blocked users could still send friend requests.
- `acceptFriendRequest` / `declineFriendRequest` had a TOCTOU race between status check and update.

Fix:

- Reject friend requests with `403 Forbidden` when `isBlocked` is true.
- Added `@Transactional` on accept and decline.
- `updateFriendRequestStatus` now updates only rows still `PENDING` and returns empty when another caller already changed status; service maps that to `409 Conflict`.
- Added `blockedUserCannotSendFriendRequest` smoke test.

### 7. H2 Test Schema Parity And Transactional Mutations

Greptile finding (iterations 3–4):

- H2 test Flyway config skipped pending-request uniqueness enforcement.
- `sendDirectMessage` and `removeFriendship` lacked `@Transactional`.
- Decline→resend→decline failed under the first H2 index shape.

Fix:

- Added `db/migration-h2/` with generated `pending_pair_key` unique constraint.
- Added `@Transactional` to `sendDirectMessage` and `removeFriendship`.
- Added `usersCanDeclineResendAndDeclineAgain` smoke test.

### 8. Bidirectional Pending Uniqueness

Greptile finding (iteration 5):

- Directional pending unique index allowed cross-direction duplicate `PENDING` rows under concurrency.

Fix:

- PostgreSQL index now keys on `LEAST`/`GREATEST` sender/recipient pair for `PENDING` rows.
- H2 `pending_pair_key` normalizes pair direction the same way.
- Added `cannotSendReversePendingFriendRequest` smoke test.

## Verification

- `mvn -pl message-service verify` (9 smoke tests)
- PR #33 CI backend/frontend

## Final Result

- Greptile initial summary: `3/5`
- Greptile after iteration 5: `4/5`, 0 unresolved review threads (9 resolved)
- Remaining Greptile concern: caller identity is request-supplied until `#auth` lands (acknowledged `TODO(#auth)`, consistent with issues #12–#14)
- Greploop iterations: 5 (max)
