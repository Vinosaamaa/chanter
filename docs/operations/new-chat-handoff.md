# New-chat handoff (2026-07-16)

**@ this file in a fresh Cursor chat** to continue Chanter without loading the long historical thread.

Canonical long-form context: [`HANDOFF.md`](../../HANDOFF.md) · workflow: [`agent-workflow.md`](agent-workflow.md) · glossary: [`CONTEXT.md`](../../CONTEXT.md)

---

## Status (one paragraph)

Public beta **slice work is complete on `main`** (#11–#104 merged). A full-repo security review is documented in [`codebase-review-2026-07-16.md`](codebase-review-2026-07-16.md) (PR [#179](https://github.com/Vinosaamaa/chanter/pull/179) — **open**, not draft). **Active work:** [Codebase Hardening](https://github.com/users/Vinosaamaa/projects/7) epic [#180](https://github.com/Vinosaamaa/chanter/issues/180) — remediation slices **#181–#205**. **Start implementation at [#181](https://github.com/Vinosaamaa/chanter/issues/181)** (SEC-04) after merging #179. Post-launch product backlog [#107](https://github.com/Vinosaamaa/chanter/issues/107) resumes after High/Medium hardening items.

---

## Paste this into the new chat

```text
Read docs/operations/new-chat-handoff.md, HANDOFF.md, CONTEXT.md, and docs/operations/agent-workflow.md.

Chanter public-beta slices through #104 are merged on main.
Active phase: Codebase Hardening epic #180 (project #7). Findings: docs/operations/codebase-review-2026-07-16.md (PR #179).
Start remediation at #181 unless I name a different issue. Breakdown: docs/issues/codebase-hardening-issue-breakdown.md.

Rules:
- Never push to main. Branch feature/<N>-<slug> or cursor/<name> → PR → CI + CodeAnt → merge.
- One issue → one branch → one PR. Vertical-slice TDD from #56 onward.
- Product UI: docs/product-design/DESIGN-DECISIONS.md + mockups.
- Local stack: make product-supervise → make product-health → make product-demo-seed
  Demo: dev-demo-owner@chanter.local / chanter-dev-demo (learner: dev-demo-learner@chanter.local)
  Frontend http://localhost:5173 · Gateway :8080

Wait for my next instruction before starting implementation.
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
- **Security:** see [`codebase-review-2026-07-16.md`](codebase-review-2026-07-16.md) — High findings tracked in #181–#188; remediation in progress

---

## Next work order

1. Merge PR [#179](https://github.com/Vinosaamaa/chanter/pull/179) (findings report)
2. [#181](https://github.com/Vinosaamaa/chanter/issues/181) → #182 → … per [`codebase-hardening-issue-breakdown.md`](../issues/codebase-hardening-issue-breakdown.md)
3. After High/Medium hardening: post-launch [#107](https://github.com/Vinosaamaa/chanter/issues/107) ([`post-launch-ui-backlog.md`](post-launch-ui-backlog.md))

---

## Git / agent rules (short)

- Preferred base: `main`. Branch `feature/<N>-<slug>` or `cursor/` per task.
- Merge only after CI green + CodeAnt (≤3 remediation rounds). Never push `main`.
- Full policy: [`agent-workflow.md`](agent-workflow.md) · `.cursor/rules/git-workflow.mdc`

Repo: https://github.com/Vinosaamaa/chanter · **Active project:** https://github.com/users/Vinosaamaa/projects/7
