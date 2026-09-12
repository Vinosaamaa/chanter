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
| Integrated session/email and gateway tests | `mvn -pl auth-service,gateway-service -am verify` passed on Java 21 |
| Frontend unit/lint/build | Pending |
| Browser account and recovery journeys | Pending |
| Hosted exact-head checks | Pending |
| CodeAnt review | Pending |
| Merged-main and provider verification | Pending |

## Environment findings

The current execution host has no Docker or WSL runtime. The complete production-profile stack therefore cannot run locally yet. Hosted CI has a Docker-backed signed-in browser job and remains required. No test-double result will be reported as full-stack proof.

The GitHub token can read/write repository issues and pull requests, but it lacks project-board scopes. Issue #242 carries the execution checklist until board access is available.

The initial frontend audit found a high-severity Browserslist advisory in build tooling. The integration lockfile updates Browserslist and its browser data within the existing dependency constraints. The post-update audit reports no high or critical findings. Two moderate Vitest findings remain in development dependencies and need a separately reviewed toolchain update.

The baseline Engineering suite has two Windows portability failures: CRLF checkout conversion changes exact schema hashes, and a POSIX-path fixture is built with Windows path separators. These failures predate this change. Linux hosted checks remain required; the local failure is recorded rather than described as a passing check.

Issue #311 owns the Windows tooling repair in a separate worktree and PR. The first hosted #242 run also exposed a pre-existing startup blocker: the pinned `minio/minio` image cannot be pulled. Course Resources currently use `LocalCourseResourceStorage`; no application uses MinIO. The product startup list now omits that unused dependency, while preserving the optional Compose service and existing data. Issue #244 owns durable private object storage. Shell tests first reproduced the failing service selection, then passed after the startup correction; they also verify that only local SMTP mode starts Mailpit.

Named workflow skills in older repository instructions, including `tdd`, `diagnose`, and `zoom-out`, were not found in the installed skill directories. Their documented repository procedures remain usable. The Engineering authoring commands and schemas are checked into the repo and can be run directly.
