# Operational monitoring implementation

The owning issue is #331, under #252 and public cutover #255. Work starts from
merged PR329 at `2dc1d2d6cf010e782043f42353412019f0f404bd`, preserving that issue's
tested branch and worktree. The [design](../architecture/operational-monitoring.md)
defines the data boundary, source ownership and remaining provider gates.

The real Spring Boot fixture first reproduced missing email metrics, then passed
with the pinned agent's Micrometer bridge and fixed email/admission names. It
preserves 600 observations after private account labels are removed and keeps
accepted/retry outcomes distinct. A second regression prevents bridged runtime
gauges from duplicating the agent's own measurements. Exporter canary and outage
tests pass locally.

Durable-event monitoring now samples pending/claimed/failed work outside request
and export threads. Tests prove claimed work stays pending, successful delivery
removes it, failed samples remain unhealthy and stale queue values become unknown.
The fixed SQL helper applies a two-second statement timeout and three-second socket
timeout, then restores the connection's prior setting. The long H2 query test
passes; native PostgreSQL cancellation and enabled production application export
still need hosted proof. Connection acquisition remains bounded by the owning
pool's configuration rather than the statement timeout.

Email and resource collectors now pass source-state tests. Email follows actual
outbox delivery and expiry. Resource monitoring distinguishes active scan/index/
cleanup, retained failures and completed/deleted rows. Its age is pending-row
age since the existing source update timestamp. State/index transitions reset it,
but lease-only retry writes do not; it is not an attempt heartbeat.
Both tests also run against the dedicated hosted PostgreSQL fixture.

The native Boot gauge assertion first reproduced the missing live gauge proof.
The completed fixture keeps its application open until the receiver acknowledges
real queue state and business counters. It passes locally with all three actual
agent/privacy/outage tests. Disabled configuration requires neither database nor
registry. Initial native PostgreSQL statement cancellation passed on AMD64 and
ARM64. Email/resource queries and the live Boot gauge fixture also passed on both
architectures at `6a903e69`.

The actual WebSocket regression first failed for a missing meter, then passed
with the existing connection map exposed as one tag-free gauge. The AI ledger
regressions first reproduced missing measured/unknown observations. The new helper
records reservations and successful settlement writes only after commit, ignores
duplicate settled writes and emits nothing after rollback. Native execution with
unmeasured usage/latency stays unknown. These measurements do not replace the
durable accounting ledger or infer provider consumption from reserved tokens.

Real Boot export now verifies unmeasured settlement labels and timer seconds.
Affected agent/common/telemetry verification passed. The broader realtime suite
exposed a test ordering race: `next()` could close the socket before its count was
asserted. Moving the assertion before receive cancellation passed the full suite.
No production connection behavior changed.

Final combined production proof, dashboard/alert rules, frontend exception tracking and
actual operator receipt remain unfinished. All workers remain Astra High at
normal speed. Work resumed on September 22 from preserved branches and interrupted edits.

Backend exceptions now use the pinned Sentry SDK through a reconstructed safe
event. Real HTTP envelopes exclude private canaries, messages, user/request data,
paths and breadcrumbs. The overload test first reproduced excessive deliveries,
then passed with five reports per process per minute. Cause/frame bounds, disabled
and enabled Boot configuration, queue pressure and receiver outage pass locally.
The deployment initializes a private disabled errors file; it validates configured
HTTPS receivers and includes the settings module in both release copy and archive
lists. No external account or actual operator receipt is claimed.

Full CI passed at `749c221a`, but the release gate remains failed. AMD64 reproduced
a fixed-clock test race caused by database microsecond rounding. The fixture now
uses whole seconds and asserts successful delivery explicitly. ARM64 reached
staging and found auth unhealthy; the next smoke emits only bounded exception
types/code locations and process exit/OOM/restart flags, never free-form logs or
runtime secrets. Diagnosis and a passing release remain required.

All four local native telemetry tests pass with the pinned Java agent and current
privacy extension, including simultaneous SDK error delivery. The actual envelope
still excludes private canaries and forbidden context fields. All 53 deployment
tests, Bash syntax and workflow validation pass. These isolated tests do not
substitute for the failed release startup or an actual provider account.

The enabled auth context reproduced the native startup failure as a Spring bean
name collision between `EmailQueueMetrics` and its factory method. Resource
monitoring had the same collision. Explicit sampler bean names fix both; the
real auth-session and resource-lifecycle contexts now permanently enable
monitoring. The receipt summary was shortened to its required 280-character
limit after the hosted Engineering gate rejected the longer description.

The initial Grafana dashboard has eighteen actual Prometheus expressions for
traffic, latency, errors, heap/CPU, pools, queues, email, AI, realtime and admission.
Its free-beta and missing-data limitations are explicit. Pinned Prometheus 3.14.0
parses every expression and validates seven alert rules. Fixtures cover initial
delay, firing, recovery, failed queue collection, low-volume suppression,
staging isolation and all ten service identities. The operating runbook defines
initial targets, ownership, escalation, provider acceptance, incident handling
and secret rotation. Provider-side import/rendering, metric translation,
independent uptime/backup heartbeats and actual notifications remain unverified.

Frontend builds now generate hidden JavaScript maps and move them outside `dist`
into ignored private build output. Each artifact includes the matching served
script and both SHA-256 hashes in a release-bound manifest. The frontend image
rejects leftover public `.map` files. Privacy regressions reject an invalid
release, an unmatched map or a public map directive before producing a manifest.
The real frontend build prepared eighty matching scripts, served no maps and
remained within all existing bundle budgets. Lint and all six release-tool tests
pass. No source maps were uploaded and no frontend symbolication is claimed.

The enabled monitoring startup fix passed full application CI and both native
release staging jobs at `8722e5ff`. Source-map packaging is preserved at `68272dd5`.

Browser reporting now uses an independently configured public ingestion key and
a lazy pinned Sentry client. Its final event boundary accepts only fixed exception
types and twelve compiled locations present in the exact release manifest. It
removes messages, function names, private URLs, cookies, user/request context and
breadcrumbs. Actual SDK envelopes pass privacy, failed receiver and five-report
overload tests. The browser sends without cookies/referrer, rejects redirects and
uses a 1.5-second deadline. A separate exact-entry cap covers the optional SDK;
initial/page/core budgets remain unchanged. Generated settings expose neither
the private backend receiver nor management credentials. Deployment tests and
actual Caddy adaptation pass. Hosted browser and combined release proof remain
required; no real provider delivery, source-map upload or symbolication is claimed.

The full frontend suite exposed an existing async navigation assertion in the
join-cohort test. It waited for API invocation but read the route before the
following awaited invalidations/navigation completed. The assertion now waits
for the visible destination; product behavior is unchanged.

Private source-map upload uses checksum-pinned Sentry CLI 3.8.0 after exact
merged-main CI and release staging. Before invoking it, the script verifies the
complete private inventory, matching served JavaScript, release and content hashes.
It rejects extra files, symlinks, foreign receiver configuration and mismatched
builds. Credentials remain in the child environment and provider output is never
published. Nine release-tool tests pass, and the verifier accepts the actual built
artifact. The checked-in workflow is disabled without an operator token. No
provider upload has run and no symbolicated error/notification has been observed.

All 318 frontend unit tests and the full hosted application suite pass at
`855234db`, including real browser requests from the optional error client and
disabled-client isolation. The bounded backup heartbeat now follows a verified
backup/configuration receipt. Sixty deployment tests pass, including an actual
HTTP receiver, silent-receiver cancellation, stale/failed receipt rejection and
separation of backup success from unconfirmed monitor acceptance. The release
bundle includes the heartbeat helper. The external HTTPS probe, free-provider
configuration and forced missing/recovery operator notifications remain unverified.

Both packaged native release stages also passed at `855234db`. Follow-up review
unified browser manifest/frame validation and added an independently bounded
stalled-transport test. Enabled staged configuration now verifies matching public
settings, CSP and compiled filenames. Fresh exact-head checks cover these final
changes plus the private upload and backup-heartbeat additions.
