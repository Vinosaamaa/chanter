# Chanter Agent Workflow

**Last updated:** 2026-07-12  
**This is the single canonical doc for agents.** It covers issue order, the per-issue completion loop, merge policy, and **CodeAnt AI** PR review. Enforced in `.cursor/rules/git-workflow.mdc`.

---

## Merge policy (non-negotiable)

**Only the repository owner merges pull requests.** Agents must **never** run `gh pr merge`, squash-merge on GitHub, or push to `main`.

After CI is green and **CodeAnt** review is clean, the agent:

1. Tells the owner the PR is ready.
2. **Stops and waits** for the owner to merge.
3. Pulls latest `main` and starts the **next** issue only **after** the owner confirms merge (or `main` contains the merge).

Agents may open PRs, push feature branches, and fix CodeAnt comments. Merging is owner-only.

---

## Issue completion loop (mandatory)

An issue is **not done** when code is pushed or a PR is opened. An issue is **done** only after the owner **merges** the PR to `main` following the full loop below.

**One issue → one branch → one PR at a time.** Do not start the next issue, launch parallel sub-agents, or end your turn while a PR is still in review.

### Required steps (in order)

1. **Read context** — `HANDOFF.md`, `CONTEXT.md`, this file (issue order below), the GitHub issue. For **UI v2** slices (#116+): read `docs/product-design/DESIGN-DECISIONS.md`, `docs/product-design/specs/layout-rules.md`, then the issue-linked PNG(s) in `docs/product-design/mockups/learner-flow/` or `mockups/owner-flow/`.
2. **Branch** — `feature/<N>-<slug>` from latest `main`. Never commit feature work on `main`.
3. **Implement** — scope limited to the issue acceptance criteria. **From issue #56 onward, use TDD** (see [Test-driven development](#test-driven-development-tdd) below).
4. **Verify locally** — `mvn verify` (affected services), `npm run lint && npm run build`; browser demo when the slice has UI (see [Agent browser testing](#agent-browser-testing) below).
5. **Docs** — `docs/operations/issue-<N>-change-log.md`; update `HANDOFF.md` / `README.md` when the issue closes.
6. **Commit + push** the feature branch only (push when the owner approves at push time).
7. **Open PR** targeting `main` with `Closes #N` in the body.
8. **Wait for CI green** (`backend`, `frontend`).
9. **Wait for CodeAnt** — GitHub **CodeAnt AI** check complete (not `pending`, not `in progress`).
10. **CodeAnt fix loop** — read all inline comments; fix actionable items; log in `docs/operations/issue-<N>-codeant-fix.md`; commit; push; **go back to step 8** until clean or only documented deferrals remain.
11. **Hand off for merge** — notify the owner; **do not merge**. Wait for owner merge.
12. **Next issue** — pull `main`, new branch, repeat from step 1.

### Forbidden (caused regressions on #20 and #21)

- **Agents merging PRs** (owner only).
- Merging while CodeAnt is still `pending`.
- Ending a session or reporting “done” right after opening a PR.
- Treating “CI green” as sufficient without CodeAnt review complete.
- Launching background agents on multiple issues in parallel on the same repo.
- Skipping `issue-<N>-codeant-fix.md` when CodeAnt feedback changed code or recorded deferrals.
- `git push origin main` or any direct push to `main`.

### Polling

If CodeAnt is `pending`, **keep polling** (`gh pr checks <N>` every 30–60s) in the same session until it completes, then run the fix loop. Do not hand off with “waiting for CodeAnt.”

---

## Issue order

**Rule:** Work issues **in order** on the active project board. Do not skip ahead.

### Phase status

| Phase | GitHub milestone | Project board | Status |
|-------|------------------|---------------|--------|
| Backend MVP | [Education MVP](https://github.com/Vinosaamaa/chanter/milestone/1) | [#1](https://github.com/users/Vinosaamaa/projects/1) | **Done** (#1–#24) |
| Production Frontend (legacy) | [Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3) | [#3](https://github.com/users/Vinosaamaa/projects/3) | **Done** (#47–#59) |
| Workable Product | [Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4) | [#4](https://github.com/users/Vinosaamaa/projects/4) | **Done** (#60–#63, #31–#32) |
| **UI v2** | [**UI v2 — Course-first shell**](https://github.com/Vinosaamaa/chanter/milestone/7) | [#5](https://github.com/users/Vinosaamaa/projects/5) | **Active — #116** |
| Public Launch | [Public Launch](https://github.com/Vinosaamaa/chanter/milestone/5) | [#5](https://github.com/users/Vinosaamaa/projects/5) | **#94+ after UI v2** (#88–#93 paused) |

Legacy **Social Hub project #2** is **closed**. #31–#32 are on **project #4** only. **#30** is on **project #3** only (pairs with #49).

Repository **Projects** tab: [github.com/Vinosaamaa/chanter/projects](https://github.com/Vinosaamaa/chanter/projects) (user projects #1, #3–#5 linked to this repo).

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

### Phase 2b: UI v2 — Course-first shell (project #5)

**Goal:** Rebuild production frontend to match v2 mockups — sidebar course list + workspace tabs (not nested channel tree).  
**Breakdown:** [`ui-v2-issue-breakdown.md`](../issues/ui-v2-issue-breakdown.md)  
**Design parent:** [#114](https://github.com/Vinosaamaa/chanter/issues/114) (closed)

**Pauses:** Public Launch legacy UI polish **#88–#93** until **#116** merges.

| Order | Issue | Title |
|------:|-------|-------|
| 1 | [#115](https://github.com/Vinosaamaa/chanter/issues/115) | Epic: Implement course-first shell (UI v2) |
| 2 | [**#116**](https://github.com/Vinosaamaa/chanter/issues/116) | **← START HERE** v2 app shell foundation |
| 3 | [#117](https://github.com/Vinosaamaa/chanter/issues/117) | Auth and onboarding v2 |
| 4 | [#118](https://github.com/Vinosaamaa/chanter/issues/118) | Home, Inbox, and Calendar v2 |
| 5 | [#119](https://github.com/Vinosaamaa/chanter/issues/119) | Course workspace — Overview and Chat |
| 6 | [#120](https://github.com/Vinosaamaa/chanter/issues/120) | Course workspace — Questions and AI panel |
| 7 | [#121](https://github.com/Vinosaamaa/chanter/issues/121) | Course workspace — Resources |
| 8 | [#122](https://github.com/Vinosaamaa/chanter/issues/122) | Course workspace — Office Hours |
| 9 | [#123](https://github.com/Vinosaamaa/chanter/issues/123) | Course workspace — People |
| 10 | [#124](https://github.com/Vinosaamaa/chanter/issues/124) | Community hub — five tabs |
| 11 | [#125](https://github.com/Vinosaamaa/chanter/issues/125) | Teaching dashboard and Settings billing |
| 12 | [#126](https://github.com/Vinosaamaa/chanter/issues/126) | Friends and DM v2 chrome |
| 13 | [#127](https://github.com/Vinosaamaa/chanter/issues/127) | Owner create course/cohort and community events |
| 14 | [#128](https://github.com/Vinosaamaa/chanter/issues/128) | Landing and marketing v2 |

Serial order only — one issue per branch.

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

### Phase 4: Public Launch (project #5)

**Goal:** Real LLM/RAG AI, staging + public beta readiness. Legacy UI polish (#88–#93) **paused** until UI v2 **#116** merges.  
**Breakdown:** [`public-launch-issue-breakdown.md`](../issues/public-launch-issue-breakdown.md)

| Order | Issue | Title |
|------:|-------|-------|
| 1 | [#82](https://github.com/Vinosaamaa/chanter/issues/82) | Epic: Public Launch |
| 2 | [#86](https://github.com/Vinosaamaa/chanter/issues/86) | Product stack reliability — **merged** (PR #105) |
| 3 | [#87](https://github.com/Vinosaamaa/chanter/issues/87) | Mockup gap audit — **owner sign-off 2026-07-09** (PR #106) |
| — | **#115–#128** | **UI v2** — see [Phase 2b](#phase-2b-ui-v2--course-first-shell-project-5) (**active**) |
| 4 | [#88](https://github.com/Vinosaamaa/chanter/issues/88)–[#93](https://github.com/Vinosaamaa/chanter/issues/93) | Legacy UI polish — **paused** until #116 |
| 5 | [#94](https://github.com/Vinosaamaa/chanter/issues/94)+ | Real AI + launch readiness |

---

## Test-driven development (TDD)

**Effective from issue #56.** Issues **#47–#55** were implemented with manual/browser verification first; **#55** did not add automated frontend tests.

For **#56 and later**, agents must use **vertical-slice TDD** (red → green → refactor), one behavior at a time:

1. **Red** — write a failing test for one user-visible behavior (hook, API client, or component integration test).
2. **Green** — minimal production code to pass.
3. **Refactor** — clean up without changing behavior; re-run tests.

Rules:

- Test through **public interfaces** (exported hooks, API functions, page behavior), not implementation details.
- Do **not** write all tests then all code (“horizontal slices”).
- Prioritize behaviors from the issue acceptance criteria; confirm with the user when unclear.
- Run the new tests in step 4 of the [issue completion loop](#required-steps-in-order) alongside `mvn verify` / `npm run lint && npm run build`.
- Log test commands in `docs/operations/issue-<N>-change-log.md`.

Frontend test stack for production UI slices: add **Vitest** + **Testing Library** when first needed (#56), then keep using it for subsequent slices.

---

## Agent browser testing

Use this when an issue has production UI under `/app/...` (sign-in required).

### Stack (sticky)

1. Start: `make product-supervise` in a **long-lived background shell** (not one-shot `make product-up` from a short agent command — processes may exit when the shell ends).
2. Wait for `make product-health` green.
3. **Teardown (required):** `make product-down` when browser testing finishes — every session, even if tests fail.

Log browser results in `docs/operations/issue-<N>-change-log.md` (pass/fail matrix, seed steps, known flakes).

### Visible browser for the owner (required default)

**Owner preference (2026-07-10):** whenever an agent runs **browser testing**, the owner must be able to **watch the app in Cursor** — not silent background automation. **You do not type anything**; the agent opens the panel.

#### Agent reveal sequence (every browser session)

1. **`open_resource`** with the target URL (e.g. `http://localhost:5173/app`) — opens the page in **Glass browser** (most reliable way to surface the panel).
2. **`browser_navigate`** to the same URL with **`position: "active"`** — focuses the browser tab while the agent clicks.
3. Optional **`position: "side"`** for beside-chat layout; if the side panel is blank, use steps 1–2 with **`position: "active"`** instead.

**Never skip the reveal steps** during issue browser testing.

| What you might expect | Reality |
|-----------------------|---------|
| Chrome/Safari pops out | **No** — browser is an **in-Cursor Glass panel**, not your system browser. |
| Side panel beside chat | Sometimes works with `position: "side"`; blank dark panel → agent uses `open_resource` + `position: "active"`. |
| Where to look | **Browser** / **Glass** tab or panel inside Cursor (often opens when the agent calls `open_resource`). |

If you still see nothing, tell the agent: *“use open_resource to show the browser.”*

### Sign-in automation

React controlled inputs often reject `browser_fill`. Prefer **`browser_type`** slowly on email/password fields. Smart Mode may block password entry or sign-in clicks — approve when prompted.

### Demo users

Product demo seed users (e.g. `dev-demo-learner@chanter.local` / `chanter-dev-demo`) — see `scripts/seed-workable-product-demo.sh` and issue change logs for per-slice seed steps.

---

## CodeAnt review

Date adopted: 2026-07-10. Replaces **cubic Dev AI** (trial expired 2026-07-10), **CodeRabbit** (trial expired 2026-07-07), and **Greptile** / `greploop` (trial expired 2026-06).

**From issue #88 onward (and all new PRs), use [CodeAnt AI](https://www.codeant.ai/) for AI PR review.** Historical logs:

| Tool | Fix log pattern | Status |
|------|-----------------|--------|
| CodeAnt AI | `docs/operations/issue-<number>-codeant-fix.md` | **Current** |
| cubic | `docs/operations/issue-*-cubic-fix.md` | Retired (trial expired 2026-07-10) |
| CodeRabbit | `docs/operations/issue-*-coderabbit-fix.md` | Retired |
| Greptile | `docs/operations/issue-*-greptile-fix.md` | Retired |

For each issue where **CodeAnt** feedback changes code or records an explicit deferral, add or update:

`docs/operations/issue-<number>-codeant-fix.md`

Include: finding, fix (or deferral reason), verification commands, and any remaining threads. Use the same table format as `issue-59-coderabbit-fix.md` / `issue-91-cubic-fix.md`.

### Prerequisites (one-time)

1. [CodeAnt AI GitHub app](https://github.com/marketplace/codeant-ai) installed on `Vinosaamaa/chanter`.
2. Control center: [app.codeant.ai](https://app.codeant.ai/) for repository settings and re-runs.

### GitHub PR flow (merge gate)

1. Open PR targeting `main`.
2. Push feature branch.
3. Wait for **CodeAnt AI** check — review completes automatically on new PRs and pushes.
4. Read inline PR review comments and summary; fix actionable items.
5. Commit → push → wait for re-review until clean.
6. Log every pass in `issue-<N>-codeant-fix.md`.
7. Owner merges.

### What to defer vs fix

CodeAnt may flag `TODO(#auth)` caller identity params. Those are **document and defer** unless the slice explicitly implements auth. Fix real bugs: timeouts, sanitization, missing tests, wrong status codes. See `issue-17-coderabbit-fix.md` for the historical defer/fix pattern (same discipline applies to CodeAnt).

### Gap-audit issue bodies (#87 onward)

Public Launch UI issues use three scopes:

1. **What to build** + **Acceptance criteria** — agents **must** implement (merge gate).
2. **Non-goals** — explicitly **out of scope** for this issue; implement on the linked issue instead.
3. **(Stretch)** items in acceptance — only after required checkboxes are done.

Do **not** treat a separate “deferred” section as skippable work on the **owning** issue (#102, #104, #100 include deferred gaps inside What to build + Acceptance criteria).

---

## Agent startup (copy-paste)

```text
Read HANDOFF.md, CONTEXT.md, and docs/operations/agent-workflow.md.

Backend MVP #11–#24, Production Frontend #47–#59, and Workable Product #60–#63 are merged.
Active work: Public Launch project #5 — active slice **#88** App shell polish (`feature/88-app-shell-polish`).

Product UI: docs/product-design/README.md
PR review: CodeAnt AI (cubic trial expired) — docs/operations/agent-workflow.md § CodeAnt review
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
| [`codeant-review-workflow.md`](codeant-review-workflow.md) | CodeAnt AI PR review (current) |
| [`.cursor/rules/git-workflow.mdc`](../../.cursor/rules/git-workflow.mdc) | Cursor always-on git rules |
