# New-chat handoff (2026-07-17)

**@ this file in a fresh Cursor chat**, then paste the prompt block below.

Canonical long-form context: [`HANDOFF.md`](../../HANDOFF.md) · workflow: [`agent-workflow.md`](agent-workflow.md) · glossary: [`CONTEXT.md`](../../CONTEXT.md)

---

## Status (one paragraph)

Public beta **slice work is complete on `main`** (#11–#104 merged). Codebase Hardening epic [#180](https://github.com/Vinosaamaa/chanter/issues/180) is **complete** — all slices **#181–#205** (+ hotfix **#220**) merged. Review report: [`codebase-review-2026-07-16.md`](codebase-review-2026-07-16.md). **Next (only when the owner asks):** post-launch backlog [#107](https://github.com/Vinosaamaa/chanter/issues/107).

---

## Paste this into the new chat

```text
@docs/operations/new-chat-handoff.md

Read that handoff, then HANDOFF.md, CONTEXT.md, and docs/operations/agent-workflow.md.

You are continuing Chanter after Codebase Hardening (#180) completed.

Do not start post-launch #107 unless I explicitly ask. Do not reopen closed beta or hardening slices unless fixing a regression.

If I ask for the next product work, start from #107 / docs/operations/post-launch-ui-backlog.md.

Local stack (if needed): make product-supervise → make product-health → make product-demo-seed
Demo: dev-demo-owner@chanter.local / chanter-dev-demo · Frontend http://localhost:5173 · Gateway :8080

Repo: https://github.com/Vinosaamaa/chanter
```

---

## Hardening closed

Epic #180 children #181–#205 and hotfix #220 are closed. Change logs: `docs/operations/issue-*-change-log.md` / `issue-*-codeant-fix.md`.
