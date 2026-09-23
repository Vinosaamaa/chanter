# New-chat handoff (2026-09-22)

**@ this file in a fresh agent chat**, then paste the prompt below.

Canonical long-form context: [`HANDOFF.md`](../../HANDOFF.md) · workflow: [`agent-workflow.md`](agent-workflow.md) · glossary: [`CONTEXT.md`](../../CONTEXT.md)

## Status

Chanter has a modern responsive UI and substantial tested product code, but is
**not publicly launched**. Account security, deployment packaging, private storage,
durable events, ingestion, retrieval, AI adapters, moderation, native companion,
monitoring and recovery infrastructure are merged. Current implementation is
deletion/export #251, integrated product interaction #339 and full source/object
recovery #342. Provider configuration and final public proof remain under #255.
Read [launch-execution-status.md](launch-execution-status.md) for current acceptance
and remaining gates. Historical DNS observations are not current provider evidence.

## Paste this into the new chat

```text
@docs/operations/new-chat-handoff.md

Read that handoff, docs/operations/launch-execution-status.md, HANDOFF.md, CONTEXT.md,
docs/operations/agent-workflow.md and the owning open issue/PR. Inspect current Git
branches and worktrees before choosing work; preserve existing changes.

You are continuing Chanter's Product Readiness and Public Production Launch epic #107.
Do not call the product publicly launched until #255 verifies the real deployment.

Continue the existing issue work in dependency order. Use Astra high at normal
speed for all workers. The first public release must use free resources, with
paid upgrades and recharge disabled. AI provider/model choice stays configurable;
subscription access must use provider-supported integrations.
Use one issue -> one branch -> one PR, TDD, local/browser gates, CI, CodeAnt
(maximum three remediation rounds), gated agent merge, then pull main and continue.
Never push directly to main.

Current expected work: #251 and #342 implementation, then #339 final integrated
acceptance. Inspect live issue and PR state before continuing; implementation
acceptance and actual deployment/provider acceptance are different gates.

Local browser stack when needed:
make product-supervise -> make product-health -> make product-demo-seed
Teardown after browser testing: make product-down

Demo: dev-demo-owner@chanter.local, dev-demo-member@chanter.local, and dev-demo-learner@chanter.local
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
- Current implementation/provider distinctions: [launch-execution-status.md](launch-execution-status.md).
- Modern UI direction: [learning-desk-v3.md](../product-design/learning-desk-v3.md), using the pinned `.agents/skills/frontend-design/SKILL.md`.
- Reusable engineering-record authoring: [pull-request-history.md](../engineering/pull-request-history.md).
