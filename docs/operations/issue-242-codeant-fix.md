# Issue #242 CodeAnt review

PR: [#312](https://github.com/Vinosaamaa/chanter/pull/312).

## Remediation round 1

Initial review head: `3d0ecb5f55d5a8708f790cde83ef9d15207ee1d4`.

| Finding | Resolution | Verification |
|---|---|---|
| Streaming response bodies could continue under a different account | Check the captured account generation before and after each stream read. Disable prefetch in the guarded stream, cancel the upstream reader on rejection, and forward consumer cancellation. | The buffered old-account test failed before the fix. Tests also cover account changes during pending reads, cancellation, normal completion and retained content type. |
| Loading Google provider metadata replaced another tab's OAuth state cookie | Metadata now returns the first-party start URL without allocating state or setting cookies. Only explicit navigation to the start endpoint creates and binds the state and PKCE challenge. | Browser-state regression failed before the fix; controller and pending-store tests cover metadata isolation and state binding. |
| Email expiry test compared only two implementation-produced values | Independently bound expiry against the configured two-hour lifetime around the operation. | Parameterized verification/reset tests. |
| Cache-boundary unit test overstated cookie restoration coverage | Rename the unit test to describe its auth-store boundary. Actual cookie restoration is covered separately by the signed-in browser journey. | Focused cache-boundary tests and hosted product suite. |
| SMTP delivery holds a database connection and row lock | Retain the bounded transactional claim for this single-host launch. `SKIP LOCKED` lets another worker claim other rows; it does not serialize the whole queue. SMTP connection/read/write timeouts are each bounded to five seconds. The remaining connection occupancy is an explicit capacity tradeoff. Introduce a durable lease only if measured throughput or connection pressure justifies that additional failure state. | Existing competing-worker, rollback, retry and transport-timeout tests; no throughput or production-provider capacity claim. |

Local commands: `npm exec -- vitest run src/lib/api-client.test.ts src/app/AuthenticatedQueryCacheBoundary.test.tsx --maxWorkers=2`, `npm run lint`, `npm run build`, and Java 21 `mvn -pl auth-service,gateway-service -am verify` with repository Maven settings.

The initial integrated head passed [full hosted CI](https://github.com/Vinosaamaa/chanter/actions/runs/34673531551), including the signed-in SMTP/browser journeys. The remediation head still requires its own hosted checks and completed CodeAnt re-review before merge. Production SMTP and HTTPS acceptance remain open in #242/#243/#255.

## Remediation round 2

Round 1 head `c43b8373a8c01648357640468b4d10057b7c9725` passed [all hosted CI jobs](https://github.com/Vinosaamaa/chanter/actions/runs/34674011689), including 240 frontend tests, seven anonymous browser checks and 13 signed-in journeys. CodeAnt completed re-review at that head.

| Finding | Resolution | Verification |
|---|---|---|
| Development persona bootstrap replaced the customer's refresh cookie | Demo login and registration explicitly omit browser credentials; only their returned bearer tokens enter the isolated demo harness. HTTP 202 gives a clear verification prerequisite instead of being parsed as a session. The harness remains excluded from production builds. | Cookie-mode regression failed first; existing-login and missing-persona tests pass. |
| Neutral registration response was mistaken for a verification instruction | Retry login after HTTP 202. Success continues immediately; only login's verification-required HTTP 403 uses the local inbox. Invalid passwords fail without waiting for absent verification mail. | Shell regression failed first; verified, unverified and wrong-password paths pass. The extracted existing auth functions are tested directly and included in `make product-test`. |
| Blocked site storage raised an unexplained browser exception | Explain that site storage must be allowed. Preserve the fail-closed cross-tab/session design: no credential-changing request starts when the durable sign-out marker cannot be written. Supporting browsers that prohibit this storage would require a separately validated coordination contract; silently swallowing the error is unsafe. | Blocked-storage test failed first and passes with zero auth operations. |
| Password hashing held the user's row lock | Verify the expensive password hash before acquiring the lock. Re-read the password hash under the same lock used by reset and reject any concurrent change before creating a session. Invalid attempts never lock the row. | Regression verifies the lock is absent during hashing and rejects a password reset racing the request. |

Affected Java 21 verification, frontend focused tests/lint/build and the three shell scenarios are rerun for this round. The resulting head requires hosted checks and completed re-review before merge.

## Remediation round 3

CodeAnt completed review of `b2f5440ddd619384a012ae7b9a3e53e853084137`. [Hosted CI](https://github.com/Vinosaamaa/chanter/actions/runs/34674634877) passed backend, frontend, dependency and Engineering jobs. Its product run passed 12 of 13 journeys; the cookie-removal assertion ran after local sign-out navigation but before waiting for the logout response.

| Finding | Resolution | Verification |
|---|---|---|
| Completed password reset appeared to fail if site storage became unavailable | Preserve the server's success message, explain that site storage is required to sign in again, and clear local credentials even if the marker cannot be stored. Also clear credentials when a rejected refresh encounters the same storage failure. This does not relax sign-in's fail-closed prerequisite. | Both storage regressions failed first; the focused browser-session tests now pass. |
| Concurrent registration could leave PostgreSQL's outer transaction aborted after a unique-email collision | User insertion runs in a nested transaction using a database savepoint. The failed insert rolls back before the caller reads the winning account and enqueues email. Successful inserts remain part of the outer account/email transaction. Apply the same savepoint boundary to the existing recoverable OAuth-link insertion. Only a confirmed existing email receives the neutral collision response. | Transaction regression checks recovery and outer rollback; hosted product coverage sends six concurrent registrations, verifies a delivered link and signs in. The local H2 test alone does not prove PostgreSQL's aborted-transaction behavior. |
| Callback failures consume browser state, preventing retry of the same callback | Retain intentional single-use state. A valid browser-bound callback consumes the authorization attempt; the existing error UI returns to sign-in to start a fresh attempt. Replaying an authorization code after an ambiguous provider response is not a supported retry contract. | Existing browser binding, state replay, metadata isolation and callback UI tests. |
| Failed seed login could trigger registration during an outage or rate limit | Register only after 401 or verification-required 403. Fail immediately on 429, transport errors and service failures. A 401 intentionally cannot distinguish a nonexistent account from an incorrect password; this explicit seed command operates on fixed local demo personas and may send the corresponding local signup notice. | Existing three scenarios plus outage and rate-limit checks; the latter failed before the guard. |
| Browser journey asserted cookie removal before remote logout completed | Await the actual logout HTTP 204 and observable browser cookie removal before reloading. Chromium does not reliably emit a completion event for a bodyless 204. Immediate local logout remains intentional; the durable marker already prevents restoration after interrupted logout. | Full hosted product journey, with local ordering and failed-logout regressions retained. |
| Retrying a 401 left its response stream unconsumed | Cancel the original body before retry. Reset test mock implementations between cases. | Cancellation assertion failed first; all authenticated HTTP tests pass. |
| Registration queried an existing email twice; runbook abbreviated secret variable names | Use one initial email lookup and spell out the complete documented SMTP/OAuth variable names. | Auth tests and exact diff review. |

Remaining custom suggestions are assessed as follows; none substitutes for unresolved launch acceptance:

- The launch-status link already resolves to a tracked file. The status table is an execution snapshot linked to the canonical issue breakdown; historical verification receipts intentionally retain the head they actually tested.
- Reacquiring an already-held user lock inside session creation is harmless in the same transaction and preserves that repository operation's other callers. Keep the small repeated session guards and device-label parsing rather than adding pass-through abstractions.
- Active-session pagination and expired/revoked-row retention remain explicit work in #253 and #251 respectively; those launch issues stay open. They are not claimed complete by this authentication PR.
- Keep the previously documented bounded SMTP transaction tradeoff. A durable lease requires additional ownership/recovery states and is not justified by measured throughput yet.
- Public pages render during the background restoration attempt. Keep synchronous reads of the opaque cross-tab marker: they detect a different tab's completed mutation before delivery of its asynchronous storage event; caching that value would weaken the guard.
- Dialog prototype setup is isolated to its test file. The demo request helper and bounded local inbox polling are small operator/test code; further abstractions or processed-message caches have no demonstrated correctness or capacity benefit here.
- A foreign-origin/CSRF-rejected OAuth callback does not clear valid browser state. Rejecting an unauthorized request before mutating a valid login attempt prevents cross-origin cancellation; that request has not consumed an authorized callback. A later valid attempt still requires the matching browser cookie, single-use server state, PKCE verifier and provider code.

Round 3 local verification covers the affected Java module, frontend tests/lint/build, seed scenarios and exact diff. Its resulting head still requires hosted CI and completed full CodeAnt review. After this round, only non-blocking suggestions with documented reasons may be deferred; confirmed security, authorization, data-loss defects and failing CI still block merge.

### Final review assessment and browser gate correction

CodeAnt completed the third re-review at `d36aa04478a17b0b62eb4e54861cb680a85b4244`. Its remaining inline suggestion concerns a caller abandoning a response reader without cancelling it. The shared wrapper already cancels on rejected reads and forwards explicit consumer cancellation. The existing question-stream parser needs `finally` cleanup when its own JSON parsing or callback fails; that non-blocking consumer work is assigned to the active #248 runtime integration. No account-switch content is released by an abandoned reader.

Retain the nested transaction/savepoint boundary: it preserves both PostgreSQL recovery and atomic account/email rollback across the existing repository callers. A PostgreSQL-specific conflict-insert API may reduce overhead later, but there is no measured performance blocker. Other new custom suggestions ask for small test factories, a different test-name emphasis, fewer fixture steps, or consolidation of already-linked historical evidence; they do not change release behavior. The existing test name describes the password-verification phase, and the product login helper already skips cleanup in a fresh `about:blank` page. Previously recorded retention, capacity and SMTP decisions remain in force.

[This hosted run](https://github.com/Vinosaamaa/chanter/actions/runs/34675134064) passed backend/frontend/Engineering checks and 13 of 14 product journeys, including concurrent registration against PostgreSQL and usable SMTP verification. The only failure was waiting for Playwright's completion event after an observed logout HTTP 204. Replace that wait with polling the actual browser cookie state; this preserves the logout assertion and avoids treating an absent protocol event as user-visible failure. Failing CI remains blocking until the corrected head passes.
