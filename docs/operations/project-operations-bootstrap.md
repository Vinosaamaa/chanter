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
- No application code has been bootstrapped.
- Existing files are planning/design docs, the education MVP PRD, GitHub-ready local issue breakdown, editable draw.io diagrams, and local repository metadata/templates.
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
- [ ] Create repository labels: `epic`, `story`, `bug`, `docs`, `architecture`, `backend`, `frontend`, `infra`, `security`, `education`, `ai-agent`, `billing`, `analytics`, `observability`.
- [ ] Enable branch protection for `main` after the first CI workflow exists.
- [ ] Require pull requests for `main`.
- [ ] Require status checks once tests/typechecks exist.
- [ ] Add CODEOWNERS after real ownership exists.
- [ ] Create the GitHub Projects board.
- [ ] Convert `docs/issues/education-mvp-issue-breakdown.md` into GitHub epics and vertical-slice stories.

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

Recommended branch names:

- `docs/<short-topic>`
- `ops/<short-topic>`
- `infra/<short-topic>`
- `backend/<service-or-slice>`
- `frontend/<feature-or-slice>`
- `feature/<vertical-slice>`
- `fix/<bug-summary>`

Recommended PR rules:

- One vertical slice or operational change per PR.
- Include acceptance criteria and a test plan.
- Update `plan.md`, `System Design.md`, or ADRs for durable architecture decisions.
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

