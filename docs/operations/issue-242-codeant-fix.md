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
