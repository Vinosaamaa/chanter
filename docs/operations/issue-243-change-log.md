# Issue #243 implementation and verification record

Owner: [issue #243](https://github.com/Vinosaamaa/chanter/issues/243), repository `Vinosaamaa/chanter`, deployment lane, branch `codex/243-reproducible-deployment`, one intended issue-linked deployment PR. Work began from main commit `2073de3`; #242 is an explicit prerequisite before release builds. The lane owns `infra/production`, `scripts/deploy`, its release workflow and deployment evidence. No external resources have been created.

## Accepted scope adjustment

The owner requires only free resources and has no hosting accounts. The reviewed plan uses one A1 VM fixed at 2 OCPUs/12 GB, self-managed PostgreSQL and Redis, same-origin Caddy HTTPS and only one active host environment. CI staging is ephemeral. Separate managed services, simultaneous always-on staging/production, account-dependent Cloudflare configuration and a real public deployment are not claimed complete. There is no automatic paid fallback. Storage recovery and AI-provider changes remain #244 and #248.

## Test-driven changes

- Manifest, Compose resource boundaries, migration ordering and schema/persistence rollback tests failed before `release.mjs` existed, then passed with its implementation.
- A public-health failure regression failed before `executeDeployment` existed, then passed when compatible previous-release recovery was implemented without recording the failed release.
- A post-health receipt-write failure left ingress running in the first test. The corrected failure path closes ingress before attempting recovery, including incompatible schema changes. Host ownership is checked while holding the deployment lock; JSON receipts replace their predecessor atomically.
- Host regressions failed before `stopEnvironment` existed. The implementation now stops partial first deployments without deleting volumes, retains failure state when Docker stop fails, rejects partial reinitialization, and rejects missing credentials. Literal punctuation and credential isolation also pass.
- The Java readiness helper was compiled and exercised against a real local HTTP server: UP succeeds, DOWN and unavailable endpoints fail.

## Local evidence, 2026-09-12

| Check | Result |
| --- | --- |
| `node --test scripts/deploy/*.test.mjs` with Java 21 | 10 tests passed |
| Rendered Compose configuration, `docker compose config --quiet` | Passed with Compose 5.5.1; no secret values printed |
| OCI `tofu init`, `fmt -check`, `validate` | Passed with OpenTofu 1.12.6 and pinned OCI provider 9.1.0 |
| Provider lock verification for Linux ARM64/AMD64 and Windows AMD64 | Passed |
| Caddy `validate` against the production Caddyfile with ephemeral internal TLS | Passed with Caddy 2.11.4 |
| Java `Probe` and Flyway `Migrate` helper compilation | Passed with Java 21 and the project's Flyway library |
| Bash syntax for build, staging, PostgreSQL initialization and host bootstrap | Passed |
| Release workflow syntax and permissions, Actionlint 1.7.12 | Passed |
| Rich Engineering record contract and public-safety validator | Passed |
| Actual image builds/scans and full container staging | Pending: no local Docker engine; dedicated dual-architecture workflow added |
| Public account, DNS/TLS, SMTP, load and rollback test | Pending external prerequisites; no provisioning attempted |

`production-deploy.md` describes build/release provenance, private configuration, infrastructure planning, first installation, compatible rollback, partial failure recovery and the remaining public-release checks. The journal architecture review records the resource and trust decisions. The PR's exact-head hosted checks and eventual deployment receipts must be added before declaring #243 ready or complete.

## Initial hosted feedback and review

Draft [PR #315](https://github.com/Vinosaamaa/chanter/pull/315) exposed an unsupported setup-java version spelling before image build. The workflow now selects the returned exact `21.0.12+8.0.LTS` version and maintained, commit-pinned setup actions. The release gate requires a successful `CI` push run on `main`, excluding edited pull-request runs with skipped build jobs. These changes still require their new hosted run.

Review added native volume ownership/write checks, per-service boot timings, container resource samples and an authenticated secure WebSocket handshake through Caddy's `/livekit/rtc` rewrite. The Caddy image removes its upstream privileged-port file capability before running with all capabilities dropped. Error logging filters credential-bearing query parameters. The runbook requires explicit migration compatibility evidence for unchanged schema epochs, corrects the OAuth browser callback, and excludes future scanner/object-storage memory from the current budget.

## Packaged dependency blocker and compatibility

The initial native release built the application images and then failed security scanning: the original Spring Boot 3.4.1 application package contained 33 high/critical findings. PR #320 is now merged with the supported-framework repair and complete nested-library scan. No advisory was suppressed. This deployment branch has been rebased onto that repair; native image and staging verification must run again against the integrated candidate before it can be accepted.

The new password encoder creates versioned PBKDF2 hashes, which the previous application cannot verify. The release policy therefore advances to epoch 3 before any such data is written. This incompatibility exists without a database-column change. The later media quarantine migration must use epoch 4. Rebase onto merged UI and AI foundations preserved the exact release scripts and combined the existing LF rules with the Engineering schema rules from PR #313.

## Runtime image repair, September 18

The next native release attempt passed the ten Java image scans, then found
HIGH findings in the pinned Caddy image. Remote scans also identified outdated
Redis/PostgreSQL operating-system packages, PostgreSQL's unused `gosu` helper,
and LiveKit dependencies. Fresh upstream digests remove the operating-system
findings; LiveKit 1.13.7 also passes its binary scan.

The refreshed official Caddy image still includes the original vulnerable
release binary. Build the same Caddy 2.11.4 with the official main entry point,
locked patched modules and Go 1.27.1. Retain its upstream license and checksum
provenance. Remove PostgreSQL's unused root privilege-switching helper because
the deployment already starts the database as UID 70. Extend native smoke
verification to require the expected Caddy version and unprivileged PostgreSQL
without that helper. No advisory was suppressed. Full final-image scans and
native staging remain required before accepting this candidate.
