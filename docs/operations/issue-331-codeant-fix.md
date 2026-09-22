# Monitoring review dispositions

Full CodeAnt review completed at initial head `853e4fa8` without blocking inline
findings. The document now says #331 owns unfinished coverage, so its opening
cannot be mistaken for a completion claim. The test receiver drains discarded
trace bodies instead of allocating a complete byte array.

Sampler interruption is not JDBC cancellation. Each source statement already has
a two-second query timeout and three-second socket timeout, with original settings
restored and the connection returned. PostgreSQL cancellation and absence of a
remaining active statement passed on both native architectures. Pool acquisition
uses the application's separate bound. Shutdown unregisters gauges and prevents
new samples; it does not claim that interrupt instantly cancels database work.

Retain one sampler per bounded source queue so one slow database query cannot
starve another source. Aggregate queries have hard deadlines; an overloaded or
unavailable source becomes visibly unhealthy rather than reporting empty. A
covering index or maintained summary will require measured production query/load
evidence rather than unverified schema changes across the active migration lanes.

The source-owned SQL helper deliberately exposes only the fixed aggregate result
contract, exercised with actual schema/state tests. No user input supplies SQL.
The privacy extension keeps an independent finite operation allowlist to reject
unknown dimensions. It must not acquire a runtime dependency on gateway code.

H2 fixture databases keep connections between setup and sampling; removing
`DB_CLOSE_DELAY=-1` would destroy the schema between operations. The timeout test
uses a real interruptible database query, not a mocked delay. The explicit native
test selection intentionally spans Maven modules without matching tests in every
module; individual native assertions prove the selected behavior. Suggested test
grouping and traversal-helper changes do not resolve a demonstrated defect.

Subsequent collector and gauge proof changes require fresh full review and hosted
checks before this draft can become ready. No review disposition waives unfinished
error tracking, operational alerts or actual provider acceptance.

The full review of `749c221a` identified two actionable gaps. Resource metrics
omitted claimable LEGACY rows when legacy migration is enabled. The collector now
follows that exact existing setting; its source-state regression verifies both
enabled and disabled cases. The telemetry workflow also omitted AI-ledger and
realtime source paths. Both now trigger the native privacy proof, together with
the new error-reporting configuration and deployment module. Neither fix changes
the source worker's scheduling or authorization behavior.

The next review found that the new error settings were omitted from configuration
snapshots/fingerprints, and differently cased receiver hostnames passed preflight
but could fail Java startup. Failing regressions reproduced both. Snapshots now
include error configuration and preflight returns the normalized URL. Receipt
metadata lists remaining unknowns. The native workflow compiles and exercises
the actual AI/realtime modules as well as testing their exported metrics.

Resource age intentionally follows `updated_at`, not every worker attempt.
`retryJob` changes lease/retry fields without updating that timestamp. The code
comment, design and implementation record now state this accurately; no source
worker behavior is changed to make a monitoring claim appear true.
