# New-chat handoff (2026-07-16)

**@ this file in a fresh Cursor chat**, then paste the prompt block below.

Canonical long-form context: [`HANDOFF.md`](../../HANDOFF.md) · workflow: [`agent-workflow.md`](agent-workflow.md) · glossary: [`CONTEXT.md`](../../CONTEXT.md)

---

## Status (one paragraph)

Public beta **slice work is complete on `main`** (#11–#104 merged). A full-repo security review lives in [`codebase-review-2026-07-16.md`](codebase-review-2026-07-16.md) (**merged** via PR [#179](https://github.com/Vinosaamaa/chanter/pull/179)). **Active phase:** [Codebase Hardening](https://github.com/users/Vinosaamaa/projects/7) epic [#180](https://github.com/Vinosaamaa/chanter/issues/180) — slices **#181–#205**. **Current:** [#181](https://github.com/Vinosaamaa/chanter/issues/181) (SEC-04 — reject default JWT/internal secrets). Post-launch [#107](https://github.com/Vinosaamaa/chanter/issues/107) waits until High/Medium hardening lands.

---

## Paste this into the new chat

```text
@docs/operations/new-chat-handoff.md

Read that handoff, then HANDOFF.md, CONTEXT.md, docs/operations/agent-workflow.md § Phase 5, and docs/issues/codebase-hardening-issue-breakdown.md.

You are continuing Chanter after public-beta completion. Active phase is Codebase Hardening (epic #180, GitHub project #7).

Do this next, in order:

1. Check PR #179 (cursor/codebase-review-9c80). If still open: wait for CI + CodeAnt, finish any fix loop, then merge it into main. Pull latest main after merge.
2. Implement issue #181 — Slice: Reject default JWT and internal-service secrets (SEC-04).
   - Finding detail: docs/operations/codebase-review-2026-07-16.md § SEC-04
   - Branch from latest main: feature/181-reject-default-secrets
   - Empty placeholders in .env.example; remove compose secret defaults; reject known default values (not just length); keep local make product-up documented.
   - TDD / regression check where practical. Write docs/operations/issue-181-change-log.md.
   - One issue → one branch → one PR with Closes #181 → CI green → CodeAnt (≤3 rounds) → merge. Never push main.
3. After #181 merges, continue the board order: #182 → #183 → … unless I say otherwise.

Do not start post-launch #107 yet. Do not reopen closed beta slices #94–#104 unless fixing a regression.

Local stack (if needed): make product-supervise → make product-health → make product-demo-seed
Demo: dev-demo-owner@chanter.local / chanter-dev-demo · Frontend http://localhost:5173 · Gateway :8080

Repo: https://github.com/Vinosaamaa/chanter
Active project: https://github.com/users/Vinosaamaa/projects/7
Start now with step 1 (PR #179), then #181.
```

---

## Run the product locally

```bash
cp -n .env.example .env
grep -q DEMO_PASSWORD .env || echo 'DEMO_PASSWORD=chanter-dev-demo' >> .env
make product-supervise   # sticky stack
make product-health
make product-demo-seed
# open http://localhost:5173/sign-in
make product-down        # when done
```

More: [`getting-started.md`](getting-started.md) · [`workable-product-demo.md`](workable-product-demo.md) · [`public-beta-launch-checklist.md`](public-beta-launch-checklist.md)

---

## What shipped recently (pointers)

| Area | Issues / PRs | Notes |
|------|--------------|--------|
| UI v2 ops | #132–#145 | Truthful home, inbox, calendar, community, billing, etc. |
| Real AI | #94–#100 | Ingestion, embeddings, RAG, LLM adapters, agent runtime, MCP, streaming Ask AI |
| Launch | #101–#104 | Staging HTTPS docs, auth verify/reset/SSO hooks, Playwright, beta checklist + landing preview |
| Ops | PR #175 | Idempotent Postgres DB ensure on `product-supervise` restart |
| **Hardening kickoff** | PR #179, epic #180, #181–#205 | 2026-07-16 codebase review → GitHub issues + project #7 |

Change logs: `docs/operations/issue-*-change-log.md` / `issue-*-codeant-fix.md`

---

## Known beta limitations

- Course storefront commerce (`course-storefront.png`) — **post-MVP** (PL-08 on #107)
- Marketing header friends/notification badges — post-launch (PL-06)
- Staging hostname placeholder: `https://staging.chanter.example`
- Flaky CI: `SocialRealtimeWebSocketSmokeTest` — empty-commit re-push often clears it
- Agent service lives under `backend/agent-service` (not `services/`)
- **Security:** see [`codebase-review-2026-07-16.md`](codebase-review-2026-07-16.md) — High findings #181–#188; remediation starts at #181

---

## Next work order

1. Merge PR [#179](https://github.com/Vinosaamaa/chanter/pull/179) (findings report + tracking docs)
2. [#181](https://github.com/Vinosaamaa/chanter/issues/181) → #182 → … per [`codebase-hardening-issue-breakdown.md`](../issues/codebase-hardening-issue-breakdown.md)
3. After High/Medium hardening: post-launch [#107](https://github.com/Vinosaamaa/chanter/issues/107) ([`post-launch-ui-backlog.md`](post-launch-ui-backlog.md))

---

## Git / agent rules (short)

- Preferred base: `main`. Branch `feature/<N>-<slug>` (or `cursor/` if required).
- Merge only after CI green + CodeAnt (≤3 remediation rounds). Never push `main`.
- Full policy: [`agent-workflow.md`](agent-workflow.md) · `.cursor/rules/git-workflow.mdc`

Repo: https://github.com/Vinosaamaa/chanter · **Active project:** https://github.com/users/Vinosaamaa/projects/7
