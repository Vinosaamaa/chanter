# New-chat handoff (2026-07-15)

**@ this file in a fresh Cursor chat** to continue Chanter without loading the long historical thread.

Canonical long-form context: [`HANDOFF.md`](../../HANDOFF.md) · workflow: [`agent-workflow.md`](agent-workflow.md) · glossary: [`CONTEXT.md`](../../CONTEXT.md)

---

## Status (one paragraph)

Public beta **slice work is complete on `main`**. UI v2 shell (#116–#128), operationalization (#132–#145), Real AI (#94–#100), staging HTTPS (#101), production auth hooks (#102), Playwright + no-dead-controls (#103), and public beta checklist + landing polish (#104) are merged. Ops fix for idempotent `product-ensure-databases` is also on `main` (PR #175). Full product browser QA (owner + learner) passed; demo recording: `/opt/cursor/artifacts/chanter-beta-product-qa-demo.mp4` (local agent artifact).

**Open GitHub items are parent epics only** (#82, #83, #84, #85, #115, #131, #107) — not actionable slices. Next product work is **post-launch backlog [#107](https://github.com/Vinosaamaa/chanter/issues/107)** (break into child stories when the owner is ready).

---

## Paste this into the new chat

```text
Read docs/operations/new-chat-handoff.md, HANDOFF.md, CONTEXT.md, and docs/operations/agent-workflow.md.

Chanter public-beta slices through #104 are merged on main. Do not reopen #94–#104 unless fixing a regression.
Open items are parent epics only; next work is post-launch #107 (see docs/operations/post-launch-ui-backlog.md) unless I name a different task.

Rules:
- Never push to main. Branch cursor/<name>-NNNN → PR → CI + CodeAnt → merge.
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

Change logs: `docs/operations/issue-*-change-log.md` / `issue-*-codeant-fix.md`

---

## Known beta limitations

- Course storefront commerce (`course-storefront.png`) — **post-MVP** (PL-08 on #107)
- Marketing header friends/notification badges — post-launch (PL-06)
- Staging hostname placeholder: `https://staging.chanter.example`
- Flaky CI: `SocialRealtimeWebSocketSmokeTest` — empty-commit re-push often clears it
- Agent service lives under `backend/agent-service` (not `services/`)

---

## Next work (#107) — do not start until owner asks

Source: [`post-launch-ui-backlog.md`](post-launch-ui-backlog.md) · epic [#107](https://github.com/Vinosaamaa/chanter/issues/107)

Examples: resource folders, richer global search, full billing page, landing badges, storefront commerce, retire `/dev/demo` from prod paths, a11y/perf.

When starting: create child stories from PL-01…PL-16, then one issue → one branch → one PR.

---

## Git / agent rules (short)

- Preferred base: `main`. Branch prefix `cursor/` + suffix as required by the cloud agent.
- Merge only after CI green + CodeAnt (≤3 remediation rounds). Never push `main`.
- Full policy: [`agent-workflow.md`](agent-workflow.md) · `.cursor/rules/git-workflow.mdc`

Repo: https://github.com/Vinosaamaa/chanter · Project: https://github.com/users/Vinosaamaa/projects/5
