# Issue #242 implementation and verification

Owning issue: [#242](https://github.com/Vinosaamaa/chanter/issues/242). Baseline: `2073de358c7007aba4824ec48e09eafb56196fc5`.

Status: in progress. No merge, release, provider delivery, or production deployment is claimed.

## Implementation plan

1. Add durable session families, refresh rotation and replay revocation, cookie transport, origin checks, and device endpoints.
2. Move browser tokens to memory, restore sessions after reload, coordinate refresh and account changes, and add session controls.
3. Add authenticated SMTP delivery through a durable retry queue and a loopback-only test inbox.
4. Exercise verification and recovery through the actual browser and email transport.
5. Review the exact integrated diff, record tests and limitations, create the Engineering receipt, and complete hosted CI and CodeAnt review.

Each writer uses an issue-scoped worktree and branch from the baseline. The coordinator reviews and cherry-picks accepted commits into `codex/242-secure-sessions`. One final PR owns the complete issue. Machine-local lane paths are kept in ignored execution records.

## Documents

- [Session and email design](../architecture/secure-browser-sessions-and-email.md)
- [Launch execution status](launch-execution-status.md)
- [Engineering evidence authoring](../engineering/pull-request-history.md)

## Verification record

| Check | Result |
|---|---|
| Shared primary checkout and origin/main | Clean and synchronized at the baseline |
| Open and closed equivalent issues | #242 is the existing owner; no duplicate created |
| Isolated worker and integration worktrees | Created from current main |
| Java 21 and Maven | Java 21 found; local Maven distribution checksum verified |
| Baseline backend | Entire reactor `mvn verify` passed on Java 21 |
| Integrated session/email and gateway tests | `mvn -pl auth-service,gateway-service -am verify` passed on Java 21; 115 tests across auth, gateway and common |
| Frontend unit/lint/build | Integrated lint/build and 237 tests across 72 files passed; four device-dialog tests passed again after the access-expiry disclosure fix |
| Public browser parsing | Product suite lists 13 tests after moving worker-level artifact options outside `describe` |
| Desktop/mobile visual and keyboard review | Passed in the implementation lane at 1280×800 and 390×844 using a simulated API; this is UI evidence only |
| Browser account and recovery journeys | Passed at `3d0ecb5` in hosted Docker-backed CI, including real SMTP delivery to Mailpit; remediation head rerun required |
| Hosted exact-head checks | Full CI passed at `3d0ecb5`; remediation head rerun required |
| CodeAnt review | Initial review completed at `3d0ecb5`; round 1 fixes documented, re-review required |
| Merged-main and provider verification | Pending |

The integrated frontend tests run with `npm test -- --maxWorkers=2` to bound local resource use. The hosted Linux unit suite also passed all 237 tests at `2c8280b`. Hosted backend, dependency review and Engineering policy passed at that head. The product environment started all services, passed health checks, and verified the demo accounts through real SMTP before Playwright rejected an artifact option placed inside a test group. The corrected configuration is under a new exact-head run; no signed-in browser pass is inferred from startup or seeding.

Manual integration review corrected the device-dialog disclosure: revoking a refresh session does not immediately expire an already issued access token. The interface states the existing 15-minute bound. A regression first failed without that disclosure and then passed. The review also confirmed the client serializes cookie mutations with Web Locks, checks account generations before applying responses, and clears old account queries synchronously before new-account queries mount.

The next hosted browser run exercised the journeys but failed the shared health fixture on Chromium's `ERR_ABORTED` signal after successful bodyless HTTP 204 responses. Inspection of the retained anonymous trace confirmed status 204 and a completed fetch. The fixture now accepts that signal only when it has observed the exact 204 response; focused tests keep missing responses, HTTP 401/200 aborts and connection failures blocking. The device-revocation API probe now explicitly sends its captured Secure cookie over the test loopback HTTP connection. The account-switch test waits for the owner's initial member content before recording requests after sign-out, rather than counting legitimate initial page loads as leakage.

## Review remediation and integrated browser proof

[Hosted run 34673531551](https://github.com/Vinosaamaa/chanter/actions/runs/34673531551) passed all jobs at `3d0ecb5f55d5a8708f790cde83ef9d15207ee1d4`: backend, frontend, signed-in product E2E, Engineering policy and dependency review. The 13 signed-in browser tests include delivered verification and reset links, refresh-cookie renewal, device revocation, reload/sign-out and the existing Owner/Member/Learner workflows. Seven anonymous browser tests also passed. These are local-beta environment receipts, not public deployment evidence.

CodeAnt completed its initial review. Round 1 fixes prevent stale-account streaming chunks and stop provider discovery from mutating OAuth state. Both regressions failed before correction. Local affected-service Maven verification, frontend lint/build and all 10 focused API/cache tests passed afterward. The email expiry test now independently checks configured lifetime, and a cache unit-test name describes the boundary it actually exercises. The [review log](issue-242-codeant-fix.md) records the bounded SMTP row-lock tradeoff and required re-review. Full hosted checks must run again on the resulting head before merge.

## Environment findings

The next review pass at `c43b837` completed after the whole hosted suite passed again. Round 2 isolates development-demo cookies, distinguishes neutral registration from actual verification-required login, explains the site-storage requirement, and moves password hashing outside the account row lock while rechecking the hash under lock to preserve reset safety. The [CodeAnt log](issue-242-codeant-fix.md) records each regression and its verification. This remains an active review until the final head's gates complete.

Round 3 preserves truthful password-reset success and clears rejected credentials when site storage fails, recovers concurrent registration through a database savepoint, rejects seed registration during outages/rate limits, and releases discarded HTTP response streams. The browser logout test now waits for the actual revocation response before asserting cookie removal; a new real-service journey exercises concurrent registration and the delivered verification link. See the same review log for evidence, retained single-use OAuth behavior, and the remaining launch-owned retention/capacity work.

The current execution host has no Docker or WSL runtime. The complete production-profile stack therefore cannot run locally yet. Hosted CI has a Docker-backed signed-in browser job and remains required. No test-double result will be reported as full-stack proof.

The GitHub token can read/write repository issues and pull requests, but it lacks project-board scopes. Issue #242 carries the execution checklist until board access is available.

The initial frontend audit found a high-severity Browserslist advisory in build tooling. The integration lockfile updates Browserslist and its browser data within the existing dependency constraints. The post-update audit reports no high or critical findings. Two moderate Vitest findings remain in development dependencies and need a separately reviewed toolchain update.

The baseline Engineering suite has two Windows portability failures: CRLF checkout conversion changes exact schema hashes, and a POSIX-path fixture is built with Windows path separators. These failures predate this change. Linux hosted checks remain required; the local failure is recorded rather than described as a passing check.

Issue #311 owns the Windows tooling repair in a separate worktree and PR. The first hosted #242 run also exposed a pre-existing startup blocker: the pinned `minio/minio` image cannot be pulled. Course Resources currently use `LocalCourseResourceStorage`; no application uses MinIO. The product startup list now omits that unused dependency, while preserving the optional Compose service and existing data. Issue #244 owns durable private object storage. Shell tests first reproduced the failing service selection, then passed after the startup correction; they also verify that only local SMTP mode starts Mailpit.

Named workflow skills in older repository instructions, including `tdd`, `diagnose`, and `zoom-out`, were not found in the installed skill directories. Their documented repository procedures remain usable. The Engineering authoring commands and schemas are checked into the repo and can be run directly.
