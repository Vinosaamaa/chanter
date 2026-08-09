# Chanter Product Readiness Issue Breakdown

> **Goal:** Move Chanter from a strong local beta to a publicly operated product at `https://chanter.app`.
> **Parent epic:** [#107 - Product Readiness and Public Production Launch](https://github.com/Vinosaamaa/chanter/issues/107)
> **Audit:** [#238](https://github.com/Vinosaamaa/chanter/issues/238) and [`product-readiness-audit-2026-08-09.md`](../operations/product-readiness-audit-2026-08-09.md)
> **Rule:** Board/dependency order is implementation order. One issue, branch, PR, review loop, and merge at a time unless isolated worktrees have no dependency.
> **Progress:** #238–#241 are complete. #242 is the next unmerged issue.

## Scope

This program owns launch blockers that earlier milestone labels did not actually close: authorization consistency, real signed-in release gates, production auth/email, deployment, durable media/events, AI quality/safety/cost, administration/moderation, billing truthfulness, customer data lifecycle, edge security, observability/recovery, no-dead-control UX, and final public cutover.

Course storefront commerce, optional marketing ornaments, and pixel-perfect polish beyond the approved responsive product are not launch blockers unless they remain exposed as non-working controls.

## Ordered issues

| Order | Issue | Vertical outcome | Type | Blocked by |
|---:|---|---|---|---|
| 1 | [#238](https://github.com/Vinosaamaa/chanter/issues/238) | Verified audit, truthful docs, executable launch backlog | AFK | None |
| 2 | [#239](https://github.com/Vinosaamaa/chanter/issues/239) | Invited members and learners get consistent authorized navigation/Home | AFK | #238 |
| 3 | [#240](https://github.com/Vinosaamaa/chanter/issues/240) | Sign-out/account switching is isolated and auth controls are accessible | AFK | #238 |
| 4 | [#241](https://github.com/Vinosaamaa/chanter/issues/241) | Hermetic tests, signed-in product E2E, dependency and bundle gates | AFK | #239, #240 |
| 5 | [#242](https://github.com/Vinosaamaa/chanter/issues/242) | Transactional email and durable secure browser sessions | AFK | #240, #241 |
| 6 | [#243](https://github.com/Vinosaamaa/chanter/issues/243) | Reproducible real staging/production infrastructure | AFK + account checkpoint | #241 |
| 7 | [#244](https://github.com/Vinosaamaa/chanter/issues/244) | Private durable Course Resources with upload quarantine | AFK | #243 |
| 8 | [#245](https://github.com/Vinosaamaa/chanter/issues/245) | Durable events drive notifications and search convergence | AFK | #241, #243 |
| 9 | [#246](https://github.com/Vinosaamaa/chanter/issues/246) | Every AI-supported resource format ingests truthfully | AFK | #244, #245 |
| 10 | [#247](https://github.com/Vinosaamaa/chanter/issues/247) | Authorized production vector retrieval replaces demo scans | AFK | #243, #246 |
| 11 | [#248](https://github.com/Vinosaamaa/chanter/issues/248) | Safe, grounded, evaluated, cost-metered AI runtime | AFK + provider checkpoint | #247 |
| 12 | [#249](https://github.com/Vinosaamaa/chanter/issues/249) | Platform admin, reports, blocking, moderation, audit | AFK | #242, #243, #245 |
| 13 | [#250](https://github.com/Vinosaamaa/chanter/issues/250) | Truthful free-beta mode and provider-backed SaaS billing | AFK + provider checkpoint | #242, #243, #244, #248 |
| 14 | [#251](https://github.com/Vinosaamaa/chanter/issues/251) | Export, coordinated deletion, retention, legal/support truth | AFK | #244, #245, #249 |
| 15 | [#252](https://github.com/Vinosaamaa/chanter/issues/252) | Production telemetry, alerts, backups, restore drills | AFK + provider checkpoint | #243, #245 |
| 16 | [#253](https://github.com/Vinosaamaa/chanter/issues/253) | Trusted Cloudflare edge and distributed abuse controls | AFK + account checkpoint | #242, #243 |
| 17 | [#254](https://github.com/Vinosaamaa/chanter/issues/254) | No dead controls; accessibility, responsive, browser, performance gates | AFK | #242, #244, #248-#251 |
| 18 | [#255](https://github.com/Vinosaamaa/chanter/issues/255) | Release-candidate proof and public `chanter.app` cutover | HITL cutover | #252, #253, #254 |

`AFK` means an agent can implement repository work without product clarification. `Account checkpoint` means the final provisioning step requires least-privilege access to an owning provider account. `HITL` means a human must approve the irreversible public cutover/sign-off.

## Execution waves

### Wave 0: make green mean green

Complete #238-#241 before adding infrastructure or product surface. These issues remove observed authorization/session failures and make CI capable of detecting their return.

### Wave 1: create a durable production substrate

Complete #242-#245 and #253. This establishes real identity recovery, deployable infrastructure, private object storage, reliable asynchronous delivery, and an edge/origin abuse boundary.

### Wave 2: productionize differentiated capabilities

Complete #246-#251. AI, moderation, billing, and data rights are customer-facing product contracts; none may remain a demo adapter or placeholder while being advertised.

### Wave 3: operate and launch

Complete #252 and #254, then #255. The final issue validates the deployed release rather than creating another checklist whose boxes remain empty.

## Non-negotiable release gates

- Zero confirmed Critical/High authorization, security, data-loss, payment, or privacy finding.
- Clean Java 21 backend suite, frontend lint/unit/build, signed-in multi-role browser suite, dependency audit, and production-image scan.
- No unexpected 4xx/5xx, page error, failed request, or console error on critical journeys.
- Email, refresh/revocation, media, AI, billing mode, moderation, export/delete, realtime, and voice verified in production-like staging.
- Backup restore and deployment rollback rehearsed against the release candidate.
- Real DNS/TLS/origin protection, provider budgets/alerts, support path, and named sign-off.

## Workflow

Follow [`agent-workflow.md`](../operations/agent-workflow.md): TDD, issue change log, browser verification for UI, feature-branch push, PR, CI, CodeAnt review/fix loop (maximum three rounds), gated merge, latest `main`, next unblocked issue.
