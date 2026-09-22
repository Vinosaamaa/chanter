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

Existing deployments must run the documented `prepare-recovery` upgrade before
preflight. That locked, idempotent command creates missing disabled error settings
without rotating existing secrets. The legacy-state regression now explicitly
removes and restores `errors.env`. Preflight remains read-only rather than
silently changing an operator's runtime configuration.

The browser review identified inconsistent filename validation between manifest
loading and frame filtering. One shared parser now enforces the hashed filename
format and rejects empty/stale/unhashed manifests. The staged deployment fixture
also enables a synthetic browser key and checks that the public settings, CSP
and compiled manifest refer to the same release without contacting any provider.
A stalled-transport test advances through ten report windows and proves the SDK
still admits only five pending sends and closes within its bound.

The optional bundle allowance is intentionally an exact entry-file allowance.
All shared/vendor imports remain counted against the unchanged core budget, and
the test proves a shared dependency cannot escape that budget. Initial and named
page import-graph limits remain enforced. It is not described as a complete lazy
feature transfer cap.

Source-map moves are not failure-atomic. A failed move/build is rejected; the
private artifact's success manifest is written only after every map has moved.
The image rejects remaining public maps, and upload separately validates every
file/hash and the served build. Partial failed build artifacts are preserved for
inspection and cannot satisfy these gates. No additional rollback/cleanup mechanism
is required: publishing a failed build is refused.

The complete review at `0019fcf` found that frontend changes could bypass native
release validation. The release path filter now includes all frontend sources,
configuration and lockfiles. Source-map packaging also rejects missing/non-string
embedded sources and absolute source paths before moving any map, matching the
upload verifier; the new regression failed before the fix and passes afterward.

The claim that Sentry CLI 3.8.0 lacks `--wait-for` is incorrect. The pinned,
checksum-verified executable's `sourcemaps upload --help` lists `--wait-for <SECS>`;
its tagged upstream implementation also defines it. No provider upload is claimed.

The dashboard's `_ratio` suffix is the documented receiver translation for unit
`1`, not a renamed Java metric. Beyond syntax checks, all 18 actual dashboard
queries now run through pinned Prometheus against independent known-value
fixtures, including staged-environment exclusion and missing telemetry. This
checks the expected translation contract; actual provider ingestion/import still
requires the operator acceptance exercise.

Integration with accepted moderation keeps separate exact-entry JavaScript caps
for moderation and browser reporting. Shared/vendor code stays in the unchanged
core cap. Tests cover both allowances together, independent oversize rejection,
duplicate ownership rejection and startup dependency refusal.
