# Project Operations Bootstrap

This document captures the first operational milestone for Chanter before application code is generated.

## Goal

Create a clean development operating model for an enterprise-grade microservice project:

- Source control and pull requests live in GitHub.
- Work is tracked as epics, stories, and bugs with acceptance criteria.
- Branch, commit, review, and CI expectations are explicit before implementation begins.
- Docs remain the source of truth until code exists.

## Current State

- The workspace is initialized as a local git repository on `main`.
- Private GitHub repository exists at `https://github.com/Vinosaamaa/chanter`, and local `main` tracks `origin/main`.
- Application code has been bootstrapped. Backend MVP issues **#11–#24** are merged on `main`.
- **Active phase:** [Production Frontend](https://github.com/users/Vinosaamaa/projects/3) — start at **#48**. **Next:** [Workable Product](https://github.com/users/Vinosaamaa/projects/4) after **#51**. Agent order: [`docs/issues/agent-roadmap.md`](../issues/agent-roadmap.md).
- Existing files include planning/design docs, the education MVP PRD, **product design showcase** (`docs/product-design/` — UI mockups and vision), GitHub-ready local issue breakdown, editable draw.io architecture diagrams (`docs/diagrams/`), local repository metadata/templates, Spring Boot services, a React/Vite frontend, CI, and local Docker infrastructure.
- The local `chanter-engineering-workflow` skill has been removed. Use installed workflow skills directly, such as `grill-with-docs`, `to-prd`, `to-issues`, `tdd`, `diagnose`, `zoom-out`, `improve-codebase-architecture`, `prototype`, `setup-pre-commit`, and CodeRabbit review (`docs/operations/coderabbit-review-workflow.md`; replaces Greptile / `greploop`).
- Draw.io **architecture** diagram sources and embedded PNG exports live in `docs/diagrams/`; use `/drawio-skill` for future diagram revisions.
- **Product UI** mockups, user-journey diagram, `vision.md`, and **`visibility-and-social-model.md`** live in `docs/product-design/`; update when target screens or visibility rules change.

## Tracker Choice

Chanter will start with GitHub Projects for the first milestones.

Why:

- It keeps source control, issues, pull requests, milestones, and project tracking in one place.
- It is enough for the first milestones: operations bootstrap, monorepo setup, auth, Study Servers/permissions, realtime course messaging, education MVP workflows, and Docker Compose.
- It avoids early integration overhead while the architecture and first vertical slices are still forming.

Upgrade path:

- Move to Linear if the project needs faster product triage, cycle planning, and a polished issue workflow.
- Move to Jira if the project needs enterprise governance, complex workflows, compliance reporting, or many teams.

Decision status: confirmed.

## Bootstrap Checklist

- [x] Initialize local git with `main` as the default branch.
- [x] Create the GitHub repository: `https://github.com/Vinosaamaa/chanter`.
- [x] Push the current planning docs and repository metadata.
- [x] Create repository labels: `epic`, `story`, `ready-for-agent`, `docs`, `architecture`, `backend`, `frontend`, `infra`, `realtime`, `security`, `education`, `ai-agent`, `billing`, `analytics`, `ops`.
- [x] Add first CI workflow (`backend`, `frontend` checks on pull requests).
- [ ] Enable GitHub branch protection for `main` (requires **GitHub Pro** on private repos — see below).
- [x] Add local `pre-push` hook to block direct pushes to `main` (`make setup-git-hooks`).
- [ ] Require pull requests for `main` on GitHub (after Pro upgrade or if repo is public).
- [ ] Require `backend` and `frontend` status checks on GitHub (after Pro upgrade).
- [ ] Add CODEOWNERS after real ownership exists.
- [x] Create the GitHub Projects board: [Chanter Education MVP](https://github.com/users/Vinosaamaa/projects/1) (issues #1–#24).
- [x] Create [Production Frontend](https://github.com/users/Vinosaamaa/projects/3) (issues #47–#59, #30) and [Workable Product](https://github.com/users/Vinosaamaa/projects/4) (#60–#63, #31–#32).
- [x] Close legacy Social Hub project #2 (empty; #31–#32 moved to project #4).
- [x] Agent issue order documented in [`docs/issues/agent-roadmap.md`](../issues/agent-roadmap.md).
- [x] Convert `docs/issues/education-mvp-issue-breakdown.md` into GitHub epics and vertical-slice stories.

## Initial Epics

- Project Operations Bootstrap
- Monorepo And Local Infrastructure Bootstrap
- Auth And Identity
- Study Servers, Course Channels, Roles, And Permissions
- Realtime Course Messaging
- Course Support Workflow
- Course Resources, Search, And FAQ
- Office Hours And Instructor Analytics
- AI Study Assistant
- SaaS Plans, Quotas, Voice Agents, And Marketplace Foundation
- Hardening, Observability, And Runbooks

## Branch And PR Convention

**Required workflow (confirmed):**

1. Create a **new branch per GitHub issue** (one issue → one branch → one PR).
2. Implement on that branch; run tests locally before pushing.
3. Open a **pull request** targeting `main` with acceptance criteria and test plan.
4. **Do not merge to `main` until the PR is approved** by the repository owner **and CodeRabbit review is complete** (see `docs/operations/coderabbit-review-workflow.md` — **Issue completion loop**).
5. After merge, the linked issue closes (use `Closes #<number>` in the PR body).

Branch naming:

- `feature/<issue-number>-<short-topic>` — e.g. `feature/11-monorepo-bootstrap`
- `feature/<issue-number>-<short-topic>` for vertical slices — e.g. `feature/12-create-study-server`
- `docs/<short-topic>`, `ops/<short-topic>`, `infra/<short-topic>`, `fix/<bug-summary>` when not tied to a numbered story

Recommended PR rules:

- One vertical slice or operational change per PR.
- Include acceptance criteria and a test plan.
- Domain features (#12+): prefer **TDD** (red → green → refactor) per `HANDOFF.md`.
- Update `plan.md`, `System Design.md`, `CONTEXT.md`, or ADRs for durable architecture/product decisions.
- For each non-trivial slice, add an issue-scoped change log under `docs/operations/` that lists what changed, the files touched, representative code snippets, and verification commands.
- For each meaningful debugging incident, add an issue-scoped debug log under `docs/operations/` that records symptoms, hypotheses, commands run, findings, fixes, and final verification.
- For every CodeRabbit suggestion that is fixed or explicitly deferred, add an issue-scoped fix log under `docs/operations/issue-<number>-coderabbit-fix.md` (see `docs/operations/coderabbit-review-workflow.md`).
- Do not push after edits or commits unless the user explicitly approves the push as a separate action at push time.
- Do not merge code that bypasses backend permission enforcement for protected actions.
- Do not commit secrets, local credentials, generated dependency folders, or local runtime data.

## Protecting `main`

**Never push directly to `main`.** Documentation-only changes use the same branch → PR → merge flow as feature code.

### Local (available now)

```bash
make setup-git-hooks
```

Installs `.githooks/pre-push`, which rejects `git push` to `main`.

### GitHub (requires GitHub Pro on private repos)

GitHub returns `403` for branch protection and rulesets on this private repository while on the free plan. After upgrading to **GitHub Pro** (or making the repository public), run:

```bash
chmod +x scripts/enable-github-branch-protection.sh
./scripts/enable-github-branch-protection.sh
```

That enables:

- Required pull request before merge (1 approval)
- Required status checks: `backend`, `frontend`
- No force-push or branch deletion on `main`
- Rules apply to admins

Manual alternative: **Settings → Branches → Add branch protection rule** for `main`.

## Commit Message Style

Use concise imperative messages:

- `Add project operations bootstrap docs`
- `Bootstrap auth service skeleton`
- `Fix refresh token rotation test`

Keep commit bodies focused on why the change exists when the title is not enough.

## Done Criteria

Milestone -1 is done when:

- The repository exists locally and remotely.
- The initial docs and repo templates are committed.
- The tracker choice is confirmed.
- Initial epics and first stories are created.
- `main` has basic PR protection.
- The next implementation milestone has clear acceptance criteria.
