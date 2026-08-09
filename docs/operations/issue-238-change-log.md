# Issue #238 change log: Product readiness audit and launch program

**Issue:** [#238](https://github.com/Vinosaamaa/chanter/issues/238)
**Branch:** `docs/238-product-readiness-audit`
**Date:** 2026-08-09

## Outcome

Replaced the stale "public beta complete" planning baseline with a verified status: Chanter is a strong local beta, not publicly launched. Published a dependency-ordered production-readiness program under epic #107 and made the release definition objective.

## GitHub changes

- Revised [#107](https://github.com/Vinosaamaa/chanter/issues/107) to **Product Readiness and Public Production Launch**.
- Created audit issue [#238](https://github.com/Vinosaamaa/chanter/issues/238).
- Created implementation/release issues [#239](https://github.com/Vinosaamaa/chanter/issues/239) through [#255](https://github.com/Vinosaamaa/chanter/issues/255).
- Project #6 assignment was not performed because the current `gh` token lacks `read:project`/`project` scope. Issues and dependencies remain fully linked from #107 and the local breakdown.

## Repository changes

### New source-of-truth documents

- [`product-readiness-audit-2026-08-09.md`](product-readiness-audit-2026-08-09.md): evidence, verified failures, full gap register, Cloudflare boundary, production topology, cost posture, release gates, and limitations.
- [`product-readiness-issue-breakdown.md`](../issues/product-readiness-issue-breakdown.md): #238–#255 order, dependencies, execution waves, and non-negotiable gates.
- [`issue-238-debug-log.md`](issue-238-debug-log.md): reproducible browser/test/authorization investigation.
- [`product-readiness-target-deployment.drawio`](../diagrams/product-readiness-target-deployment.drawio): editable target deployment diagram, with an embedded-source PNG for rendered documentation.

### Corrected canonical status

- `README.md`, `HANDOFF.md`, `plan.md`, `agent-workflow.md`, and `new-chat-handoff.md` now identify #107/#238–#255 as active.
- `public-beta-launch-checklist.md` now says its sign-off never completed.
- `staging-deploy.md` now says the hostname is a placeholder and corrects the false log-email walkthrough.
- `post-launch-ui-backlog.md` and `no-dead-controls-inventory.md` are retained as historical inputs, not active release proof.

Representative release rule:

```markdown
Chanter is product-ready only when critical customer journeys work end to end
on production infrastructure and recovery, security, support, administration,
AI, billing mode, legal/data lifecycle, and operations are verified.
```

## Verification performed

Backend and frontend baseline:

```text
Java 21 mvn -B clean test     PASS (12 modules)
npm run lint                  PASS
npm run test                  PASS (65 files / 213 tests)
npm run build                 PASS (large-chunk warning retained as finding)
```

Local product/browser baseline:

```text
make product-supervise        PASS outside sandbox isolation
make product-health           PASS
make product-demo-seed        PASS
Direct system-Chrome audit    PASS as an audit runner; product findings recorded
make product-down             PASS
```

Public/provider checks:

- Verified `chanter.app` DNS and HTTP response as GoDaddy parking.
- Verified no repository Cloudflare/Wrangler configuration.
- Reviewed current official Cloudflare Pages, R2, WebSocket, network-port, Spectrum, Containers, and LiveKit deployment documentation.
- Cloudflare account-level usage remains unverified because no authenticated account session/token scope was available.

## TDD note

#238 is a documentation/audit slice and changed no production behavior. The implementation issues it created require vertical-slice TDD under the canonical workflow. Browser findings were reproduced before their owning bug issues were written.
