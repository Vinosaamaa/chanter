# CodeRabbit Review Workflow

Date adopted: 2026-06-22  
Replaces: Greptile / `greploop` (trial expired)

**This file is the master reference for issue/PR completion.** Agents and humans must follow the **Issue completion loop** below. See also `.cursor/rules/git-workflow.mdc`.

## Issue completion loop (mandatory)

An issue is **not done** when code is pushed or a PR is opened. An issue is **done** only after the PR is **merged to `main`** following the full loop below.

**One issue → one branch → one PR at a time.** Do not start the next issue, launch parallel sub-agents, or end your turn while a PR is still in review.

### Required steps (in order)

1. **Implement** on `feature/<N>-<slug>` from latest `main`.
2. **Verify locally** — `mvn verify` (affected services), `npm run lint && npm run build`; browser demo when the slice has UI.
3. **Docs** — `docs/operations/issue-<N>-change-log.md`; update `HANDOFF.md` / `README.md` when the issue closes.
4. **Commit + push** the feature branch only.
5. **Open PR** targeting `main` (`Closes #N`).
6. **Wait for CI green** (`backend`, `frontend`).
7. **Wait for CodeRabbit to finish** — status must be **Review completed** (not `pending`, not `Review in progress`, not `Review failed` because the PR was closed early).
8. **CodeRabbit fix loop** — read all inline comments; fix actionable items; log in `docs/operations/issue-<N>-coderabbit-fix.md`; commit; push; **go back to step 6** until clean or only documented deferrals remain.
9. **Merge** (squash) only after steps 6–8.
10. **Start the next issue** — pull `main`, new branch, repeat.

### Forbidden (caused regressions on #20 and #21)

- Merging while CodeRabbit is still `pending`.
- Ending a session or reporting “done” right after opening a PR.
- Treating “CI green” as sufficient without CodeRabbit complete.
- Launching background agents on multiple issues in parallel on the same repo.
- Skipping `issue-<N>-coderabbit-fix.md` when CodeRabbit changed code or recorded deferrals.

### Polling

If CodeRabbit is `pending`, **keep polling** (`gh pr checks <N>` every 30–60s) in the same session until it completes, then run the fix loop. Do not hand off with “waiting for CodeRabbit.”

## Policy

**From issue #17 onward, use CodeRabbit for AI PR review.** Greptile fix logs under `docs/operations/issue-*-greptile-fix.md` remain historical records for merged PRs #25–#35; do not run new Greptile reviews.

For each issue where CodeRabbit feedback changes code or records an explicit deferral, add or update:

`docs/operations/issue-<number>-coderabbit-fix.md`

Include: finding, fix (or deferral reason), verification commands, and any remaining threads.

Optional: install the Cursor skill so agents can trigger reviews from chat:

```bash
npx skills add coderabbitai/skills@code-review -g -y -a cursor
npx skills add coderabbitai/skills@autofix -g -y -a cursor   # optional — PR thread fixes
```

Docs: https://docs.coderabbit.ai/cli/skills

## Prerequisites (one-time)

1. **GitHub app** — [CodeRabbit on GitHub](https://github.com/apps/coderabbitai) installed on `Vinosaamaa/chanter` (enables automatic PR reviews on push).
2. **Local CLI** — `coderabbit` installed and authenticated:

```bash
coderabbit doctor          # all checks should pass
coderabbit auth login      # if not signed in
```

## Two Ways CodeRabbit Reviews Your Code

| Mode | Trigger | Where results appear |
|------|---------|----------------------|
| **GitHub PR** (like Greptile) | Push to an open PR branch | PR checks, review comments, summary on the PR |
| **Local CLI** | Run `coderabbit review` on your machine | Terminal JSON (`--agent`) or plain text |

Both use the same analysis. Use GitHub for the merge gate; use CLI for faster iteration before push or when you want the agent to read structured findings locally.

### GitHub PR flow (Greptile-equivalent)

Same habit as Greptile:

1. Open PR targeting `main`.
2. `git push` your branch.
3. CodeRabbit runs automatically (check name **CodeRabbit** on the PR).
4. Read inline comments and the summary on the PR.
5. Fix → commit → push → CodeRabbit re-reviews the new commits.

No `@greptile review` comment needed. Re-review happens on each push (subject to your CodeRabbit plan limits).

### Local CLI flow

From the repo root on your feature branch:

```bash
# Full branch diff vs main (what we used on PR #35)
coderabbit review --agent --base main --type committed

# Only uncommitted edits
coderabbit review --agent -t uncommitted

# Human-readable output
coderabbit review --base main
```

`--agent` emits JSON findings for Cursor/agents. Re-run after fixes until critical/major items are addressed.

## Fix Loop (replaces `greploop`)

Manual loop (agent or you):

1. Wait for **CI green** (`backend`, `frontend`).
2. Get findings — GitHub PR comments **or** `coderabbit review --agent --base main`.
3. Fix actionable items (skip false positives / deferred `TODO(#auth)` with a note in the fix log).
4. Log in `docs/operations/issue-<N>-coderabbit-fix.md`.
5. Commit and push.
6. Repeat until PR review is clean or only acknowledged deferrals remain.

Cursor prompts that work:

- *"Run CodeRabbit review on this branch and fix major findings."*
- *"Address CodeRabbit PR comments on #35 and update issue-17-coderabbit-fix.md."*

Optional **autofix** skill (needs `gh` + open PR): fetches unresolved CodeRabbit review threads on the current branch's PR and walks fixes interactively. Trigger phrases: *"coderabbit autofix"*, *"fix coderabbit threads"*.

## What We Deferred vs Fixed

On issue #17, CodeRabbit flagged auth query params (`userId`, `uploaderUserId`, `viewerUserId`) as critical. Those match the existing no-auth demo harness (`TODO(#auth)`, issue #30). **Document and defer** unless the slice explicitly implements auth.

Prefer fixing without opening auth: client timeouts, filename sanitization, missing tests, wrong HTTP status codes, etc. See `issue-17-coderabbit-fix.md` for the pattern.

## Historical: Greptile

Greptile was used through PR #34 (#16). The trial hit the 50-review limit during PR #35. Older logs: `docs/operations/issue-*-greptile-fix.md`.
