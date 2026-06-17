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
- Application code has been bootstrapped. Issues #11 and #12 are merged on `main`; issue #13 is implemented in PR #28 and ready for owner merge.
- Existing files include planning/design docs, the education MVP PRD, GitHub-ready local issue breakdown, editable draw.io diagrams, local repository metadata/templates, Spring Boot services, a React/Vite frontend, CI, and local Docker infrastructure.
- The local `chanter-engineering-workflow` skill has been removed. Use installed workflow skills directly, such as `grill-with-docs`, `to-prd`, `to-issues`, `tdd`, `diagnose`, `zoom-out`, `improve-codebase-architecture`, `prototype`, `setup-pre-commit`, and `greploop`.
- Draw.io diagram sources and embedded PNG exports live in `docs/diagrams/`; use `/drawio-skill` for future diagram revisions.

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
- [ ] Enable branch protection for `main` after the first CI workflow exists.
- [ ] Require pull requests for `main`.
- [ ] Require status checks once tests/typechecks exist.
- [ ] Add CODEOWNERS after real ownership exists.
- [x] Create the GitHub Projects board: [Chanter Education MVP](https://github.com/users/Vinosaamaa/projects/1) (issues #1–#24 added).
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
4. **Do not merge to `main` until the PR is approved** by the repository owner.
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
- For every Greptile/GrepTile/Grep tile suggestion that is fixed, add an issue-scoped Greptile fix log under `docs/operations/` with the finding, fix, representative snippet, verification, final Greptile confidence, and any unresolved follow-up.
- Do not push after edits or commits unless the user explicitly approves the push as a separate action at push time.
- Do not merge code that bypasses backend permission enforcement for protected actions.
- Do not commit secrets, local credentials, generated dependency folders, or local runtime data.

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
