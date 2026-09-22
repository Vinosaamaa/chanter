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
inactivity, since existing retry/claim transitions update the source timestamp.
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
