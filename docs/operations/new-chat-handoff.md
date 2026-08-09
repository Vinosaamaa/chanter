# New-chat handoff (2026-08-09)

**@ this file in a fresh agent chat**, then paste the prompt below.

Canonical long-form context: [`HANDOFF.md`](../../HANDOFF.md) · workflow: [`agent-workflow.md`](agent-workflow.md) · glossary: [`CONTEXT.md`](../../CONTEXT.md)

## Status

UI v2, local operationalization, launch-preparation code through #104, Codebase Hardening #180, product-readiness audit #238, and membership consistency #239 are merged on `main`. Chanter is a **strong local beta, not publicly launched**: session isolation and production providers/operations remain incomplete, and `chanter.app` was parked at the 2026-08-09 audit. Active program: [#107](https://github.com/Vinosaamaa/chanter/issues/107), with #240 next and #241–#255 following in dependency order.

## Paste this into the new chat

```text
@docs/operations/new-chat-handoff.md

Read that handoff, then HANDOFF.md, CONTEXT.md, docs/operations/agent-workflow.md,
docs/operations/product-readiness-audit-2026-08-09.md, and
docs/issues/product-readiness-issue-breakdown.md.

You are continuing Chanter's Product Readiness and Public Production Launch epic #107.
Do not call the product publicly launched until #255 verifies the real deployment.

Work the first unmerged, unblocked issue in #238-#255 dependency order.
Use one issue -> one branch -> one PR, TDD, local/browser gates, CI, CodeAnt
(maximum three remediation rounds), gated agent merge, then pull main and continue.
Never push directly to main.

Current expected start: #240 browser session isolation and auth accessibility.

Local browser stack when needed:
make product-supervise -> make product-health -> make product-demo-seed
Teardown after browser testing: make product-down

Demo: dev-demo-owner@chanter.local and dev-demo-learner@chanter.local
Password: DEMO_PASSWORD (local default chanter-dev-demo)
Frontend: http://localhost:5173

Repo: https://github.com/Vinosaamaa/chanter
Epic: https://github.com/Vinosaamaa/chanter/issues/107
```

## Verified launch baseline

- Audit: [`product-readiness-audit-2026-08-09.md`](product-readiness-audit-2026-08-09.md)
- Issue order: [`product-readiness-issue-breakdown.md`](../issues/product-readiness-issue-breakdown.md)
- `chanter.app`: parked at audit time; no verified public Chanter environment.
- Cloudflare: no repository/public zone usage verified; account-level usage requires authenticated account access.
- Completed launch slices: #238 audit/program and #239 membership/navigation/Home authorization.
- Next launch slices: #240 session isolation and #241 release gates.
