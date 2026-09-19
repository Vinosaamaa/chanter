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

Email/resource collectors, AI/realtime coverage, dashboard/alert rules, exception
tracking and actual operator receipt remain unfinished. All workers remain Astra
High at normal speed.
