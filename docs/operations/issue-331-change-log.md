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
ARM64; new collector queries still require their own hosted run.

AI/realtime coverage, dashboard/alert rules, exception tracking and actual operator
receipt remain unfinished. All workers remain Astra High at normal speed. Work
resumed on September 22 from preserved branches and interrupted edits.
