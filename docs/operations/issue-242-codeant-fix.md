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
