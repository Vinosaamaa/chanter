# Issue 16 Greptile Fix Log: Post Support Question In Course Channel

Date: 2026-06-18  
Branch: `feature/16-post-support-question-in-course-channel`  
PR: https://github.com/Vinosaamaa/chanter/pull/34

## Summary

Greptile reviewed the support-question slice at 3/5 confidence and flagged a Flyway checksum violation from editing an already-shipped migration and a PostgreSQL transaction-abort bug on concurrent idempotent posts.

## Fixes Applied

### 1. Flyway Checksum On V2_1

Greptile finding:

- `V2_1__add_pending_friend_request_unique_index.sql` was modified after issue #15 merged to `main`, which breaks Flyway checksum validation on environments that already applied `V2_1`.

Fix:

- Restored `V2_1` to the shipped `main` content (index creation only).
- Moved the duplicate pending friend-request cleanup `DELETE` into new `V2_2__cleanup_duplicate_pending_friend_requests.sql`.

### 2. Concurrent Idempotency Under PostgreSQL

Greptile finding:

- `SupportQuestionService` caught `DataIntegrityViolationException` and ran a fallback `SELECT` in the same transaction. PostgreSQL aborts the transaction on constraint violation, so the fallback read fails instead of returning the existing row.

Fix:

- Added `SupportQuestionIdempotencyRecovery` with `@Transactional(propagation = REQUIRES_NEW, readOnly = true)` to load the existing support question in a fresh transaction after a unique-constraint race.

### 3. Channel Kind Guard On Support Question Access

Greptile finding (iteration 2):

- `findSupportQuestionChannelAccess` granted post rights on any enrolled course channel, not only `#questions`.

Fix:

- Restrict access query to `TEXT` channels named `questions`.

### 4. Idempotency Key Validation And List Response Shape

Greptile finding (iteration 2):

- Request DTO lacked `@Size(max = 128)` matching the DB column.
- `GET /support-questions` exposed learner idempotency keys to instructors.

Fix:

- Added `@Size(max = 128)` on `CreateSupportQuestionRequest.idempotencyKey`.
- Introduced `SupportQuestionSummaryResponse` for list responses without `idempotencyKey`; POST still returns the full `SupportQuestionResponse`.
