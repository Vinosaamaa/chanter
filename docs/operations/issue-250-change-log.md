# Issue 250 change log

Repository: `Vinosaamaa/chanter`. Issue #250. Branch: `codex/250-free-beta`. One issue worktree and one final PR, with root owning integration. Scope is the free-beta entitlement boundary and truthful usage UI; payment-provider acceptance remains open.

The September 12 checkpoint preserved the design and a failing owner-tampering regression. Work resumed September 18. The saved implementation was checkpointed and rebased onto current main, including #319 and #321, without discarding changes.

## Implementation

- Reject the former plan PATCH with 403 and remove the repository mutation. Read effective `FREE_BETA` entitlement from validated operator policy, preserving legacy database tiers as historical data.
- Default to 1,000 lifetime assistant runs per Study Server. Accept only `free_beta` mode and limits from 1 through 1,000. Response metadata identifies `OPERATOR_POLICY` and `LIFETIME`.
- Replace Billing with owner Usage, redirect the old link, display actual count/remaining capacity, and distinguish loading, errors, near-limit and exhausted states. Remove plan selectors and upgrade promises from the modern UI, instructor dashboard and development harness.
- Preserve provider token budgets, private-object limits and existing authorization. No payment or native subscription integration is added.

## Verification

The saved implementation passed 263 frontend tests, lint/build/budgets and six focused backend cases. The original HTTP regression observed 200 instead of the required 403 before implementation. Follow-up regressions reproduced upgrade-promising fallback text and an access-loading error that redirected Home; both now pass. New browser scenarios verify truthful zero, normal, near-limit, exceeded-limit and unavailable states at phone/tablet/desktop widths. The real product journey reads server usage, attempts a forbidden quota upgrade, checks unchanged entitlement and reloads the UI.

Full backend verification, hosted browser evidence and exact-head CI/review are pending on the initial candidate. A local preview launch was blocked by automatic approval review and was not retried; hosted browser checks will provide responsive evidence. Existing #254 preview ownership is preserved.

Design: `docs/architecture/free-beta-entitlements.md`. System review: `issue-250-system-review.md`. Paid checkout, provider events, invoices, subscription recovery, consolidated provider/storage alerting and production release proof remain outside this accepted free-beta slice and keep #250 open.
