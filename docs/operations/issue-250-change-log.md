# Issue 250 checkpoint

Repository: `Vinosaamaa/chanter`. Issue: #250. Branch: `codex/250-free-beta`. One issue worktree and one intended final PR; no PR exists yet.

The owner paused development on 2026-09-12. This commit preserves the design and an intentionally failing regression test. It is not a completed implementation or a merge candidate.

The design in `docs/architecture/free-beta-entitlements.md` selects an operator-controlled free beta and removes simulated paid-plan behavior. Existing paid-provider acceptance remains future work.

The focused test `SaasPlanSmokeTest.ownerCannotRaiseAStudyServerQuotaThroughTheFormerPlanEndpoint` reproduces the defect: an owner can raise the quota through PATCH. The expected status is 403; the current implementation returns 200. The existing local test log is preserved. No fix has been implemented.

On authorized resumption, reject the former plan mutation, verify the focused test, then implement the effective operator policy and truthful usage UI in small tested steps. Full verification, Engineering evidence, one issue-linked PR, hosted checks and review remain required. Do not merge this red checkpoint.
