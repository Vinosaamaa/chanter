# Chanter Agent Workflow

**Last updated:** 2026-06-25  
**This is the single canonical doc for agents.** It covers issue order, the per-issue completion loop, merge policy, and CodeRabbit review. Enforced in `.cursor/rules/git-workflow.mdc`.

---

## Merge policy (non-negotiable)

**Only the repository owner merges pull requests.** Agents must **never** run `gh pr merge`, squash-merge on GitHub, or push to `main`.

After CI is green and CodeRabbit is clean, the agent:

1. Tells the owner the PR is ready.
2. **Stops and waits** for the owner to merge.
3. Pulls latest `main` and starts the **next** issue only **after** the owner confirms merge (or `main` contains the merge).

Agents may open PRs, push feature branches, and fix CodeRabbit comments. Merging is owner-only.

---

## Issue completion loop (mandatory)

An issue is **not done** when code is pushed or a PR is opened. An issue is **done** only after the owner **merges** the PR to `main` following the full loop below.

**One issue → one branch → one PR at a time.** Do not start the next issue, launch parallel sub-agents, or end your turn while a PR is still in review.

### Required steps (in order)

1. **Read context** — `HANDOFF.md`, `CONTEXT.md`, this file (issue order below), the GitHub issue, and relevant mockups in `docs/product-design/mockups/` when building UI.
2. **Branch** — `feature/<N>-<slug>` from latest `main`. Never commit feature work on `main`.
3. **Implement** — scope limited to the issue acceptance criteria.
4. **Verify locally** — `mvn verify` (affected services), `npm run lint && npm run build`; browser demo when the slice has UI.
5. **Docs** — `docs/operations/issue-<N>-change-log.md`; update `HANDOFF.md` / `README.md` when the issue closes.
6. **Commit + push** the feature branch only (push when the owner approves at push time).
7. **Open PR** targeting `main` with `Closes #N` in the body.
8. **Wait for CI green** (`backend`, `frontend`).
9. **Wait for CodeRabbit** — status must be **Review completed** (not `pending`, not `Review in progress`).
10. **CodeRabbit fix loop** — read all inline comments; fix actionable items; log in `docs/operations/issue-<N>-coderabbit-fix.md`; commit; push; **go back to step 8** until clean or only documented deferrals remain.
11. **Hand off for merge** — notify the owner; **do not merge**. Wait for owner merge.
12. **Next issue** — pull `main`, new branch, repeat from step 1.

### Forbidden (caused regressions on #20 and #21)

- **Agents merging PRs** (owner only).
- Merging while CodeRabbit is still `pending`.
- Ending a session or reporting “done” right after opening a PR.
- Treating “CI green” as sufficient without CodeRabbit complete.
- Launching background agents on multiple issues in parallel on the same repo.
- Skipping `issue-<N>-coderabbit-fix.md` when CodeRabbit changed code or recorded deferrals.
- `git push origin main` or any direct push to `main`.

### Polling

If CodeRabbit is `pending`, **keep polling** (`gh pr checks <N>` every 30–60s) in the same session until it completes, then run the fix loop. Do not hand off with “waiting for CodeRabbit.”

---

## Issue order

**Rule:** Work issues **in order** on the active project board. Do not skip ahead.

### Phase status

| Phase | GitHub milestone | Project board | Status |
|-------|------------------|---------------|--------|
| Backend MVP | [Education MVP](https://github.com/Vinosaamaa/chanter/milestone/1) | [#1](https://github.com/users/Vinosaamaa/projects/1) | **Done** (#1–#24) |
| Production Frontend | [Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3) | [#3](https://github.com/users/Vinosaamaa/projects/3) | **Active — start here** |
| Workable Product | [Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4) | [#4](https://github.com/users/Vinosaamaa/projects/4) | After #51 on project #3 |

Legacy **Social Hub project #2** is **closed**. #31–#32 are on **project #4** only. **#30** is on **project #3** only (pairs with #49).

### Phase 2: Production Frontend (project #3)

**Goal:** Mockup-aligned UI, auth screens, app shell, live **text** chat.  
**Breakdown:** [`production-frontend-issue-breakdown.md`](../issues/production-frontend-issue-breakdown.md)  
**PRD:** [`education-mvp-prd.md`](../product/education-mvp-prd.md) § Phase 2

| Order | Issue | Title |
|------:|-------|-------|
| 1 | [#47](https://github.com/Vinosaamaa/chanter/issues/47) | Epic: Product Shell And Production Frontend |
| 2 | [**#48**](https://github.com/Vinosaamaa/chanter/issues/48) | **← START HERE** Bootstrap Production Frontend Foundation |
| 3 | [#49](https://github.com/Vinosaamaa/chanter/issues/49) | Auth UI And Protected App Routes |
| 4 | [#30](https://github.com/Vinosaamaa/chanter/issues/30) | Wire Auth Service Principal (implement with #49) |
| 5 | [#50](https://github.com/Vinosaamaa/chanter/issues/50) | Study Server App Shell And Navigation |
| 6 | [#51](https://github.com/Vinosaamaa/chanter/issues/51) | Bootstrap Realtime Service And Live Course Channel Chat |
| 7 | [#52](https://github.com/Vinosaamaa/chanter/issues/52) | Production `#questions` UX With AI Context Panel |
| 8 | [#53](https://github.com/Vinosaamaa/chanter/issues/53) | Production Course Resources Panel |
| 9 | [#54](https://github.com/Vinosaamaa/chanter/issues/54) | Production Support Operations UI |
| 10 | [#55](https://github.com/Vinosaamaa/chanter/issues/55) | Production Instructor Dashboard And SaaS Plan UI |
| 11 | [#56](https://github.com/Vinosaamaa/chanter/issues/56) | Production Onboarding And Enrollment Flows |
| 12 | [#57](https://github.com/Vinosaamaa/chanter/issues/57) | Global Search UI And Search Service Bootstrap |
| 13 | [#58](https://github.com/Vinosaamaa/chanter/issues/58) | Channel Summary UI For Course Channels |
| 14 | [#59](https://github.com/Vinosaamaa/chanter/issues/59) | Public Marketing Landing Page (optional polish) |

After **#50**, issues **#53–#56** may run in parallel if coordinated; default is still top-to-bottom on the board.

### Phase 3: Workable Product (project #4)

**Goal:** Clickable **full-stack local app** — voice in channels, live friends/DM, one-command dev stack.  
**Breakdown:** [`workable-product-issue-breakdown.md`](../issues/workable-product-issue-breakdown.md)  
**PRD:** [`education-mvp-prd.md`](../product/education-mvp-prd.md) § Phase 3

**Do not start project #4 until #51 (realtime text chat) is merged.**

| Order | Issue | Title |
|------:|-------|-------|
| 1 | [#60](https://github.com/Vinosaamaa/chanter/issues/60) | Epic: Workable Local Product (Full Stack) |
| 2 | [#62](https://github.com/Vinosaamaa/chanter/issues/62) | One-Command Local Product Stack (may start early) |
| 3 | [#61](https://github.com/Vinosaamaa/chanter/issues/61) | Voice Channel WebRTC And LiveKit Local Stack |
| 4 | [#31](https://github.com/Vinosaamaa/chanter/issues/31) | Friends Hub And Live DM Conversation |
| 5 | [#32](https://github.com/Vinosaamaa/chanter/issues/32) | Direct Message Voice Call Between Friends |
| 6 | [#63](https://github.com/Vinosaamaa/chanter/issues/63) | Workable Product End-To-End Demo Path |

**#30** ships in phase 2 with **#49** on project #3. If auth is incomplete when phase 3 starts, finish **#30** before **#31**.

### Workable app checklist (phase 3 definition of done)

1. One command starts the full local stack (#62).
2. Sign in as two users (#30 + #49).
3. Live text in a shared Course Channel (#51).
4. Friend request → accept → live DM (#31).
5. Join Voice Channel with real audio (#61).
6. Optional: DM voice call (#32).

---

## CodeRabbit review

Date adopted: 2026-06-22. Replaces Greptile / `greploop` (trial expired).

**From issue #17 onward, use CodeRabbit for AI PR review.** Historical Greptile logs: `docs/operations/issue-*-greptile-fix.md`.

For each issue where CodeRabbit feedback changes code or records an explicit deferral, add or update:

`docs/operations/issue-<number>-coderabbit-fix.md`

Include: finding, fix (or deferral reason), verification commands, and any remaining threads.

### Prerequisites (one-time)

1. [CodeRabbit GitHub app](https://github.com/apps/coderabbitai) on `Vinosaamaa/chanter`.
2. Local CLI: `coderabbit doctor` / `coderabbit auth login`.

### GitHub PR flow (merge gate)

1. Open PR targeting `main`.
2. Push feature branch.
3. Wait for **CodeRabbit** check — **Review completed**.
4. Fix comments → commit → push → re-review until clean.
5. Owner merges.

### Local CLI (optional, pre-push)

```bash
coderabbit review --agent --base main --type committed
coderabbit review --agent -t uncommitted
```

### What to defer vs fix

CodeRabbit often flags `TODO(#auth)` caller identity params. Those are **document and defer** until **#30**, unless the slice explicitly implements auth. Fix real bugs: timeouts, sanitization, missing tests, wrong status codes. See `issue-17-coderabbit-fix.md` for the pattern.

---

## Agent startup (copy-paste)

```text
Read HANDOFF.md, CONTEXT.md, and docs/operations/agent-workflow.md.

Backend MVP #11–#24 is merged. Active work: Production Frontend project #3.
Start at issue #48 unless a higher-priority in-order issue is already in progress.

Product UI: docs/product-design/README.md
Do not merge PRs — owner merges only.
```

---

## Related docs

| Doc | Purpose |
|-----|---------|
| [`HANDOFF.md`](../../HANDOFF.md) | Session context and current slice |
| [`CONTEXT.md`](../../CONTEXT.md) | Product glossary |
| [`production-frontend-issue-breakdown.md`](../issues/production-frontend-issue-breakdown.md) | Phase 2 slice details |
| [`workable-product-issue-breakdown.md`](../issues/workable-product-issue-breakdown.md) | Phase 3 slice details |
| [`.cursor/rules/git-workflow.mdc`](../../.cursor/rules/git-workflow.mdc) | Cursor always-on git rules |
