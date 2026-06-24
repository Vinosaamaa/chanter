# Chanter Project Handoff

## Project Summary

Chanter is planned as an enterprise-grade, education-first Discord-like learning community platform with:

- Study Servers for educators, bootcamps, tutoring businesses, cohort-based course creators, and study groups.
- Real-time course/module channels, text chat, instructor/TA/learner roles, permissions, moderation, notifications, course resources, search, learning analytics, and local production-style deployment.
- A first-party AI Study Assistant that answers from approved course resources and allowed context, supports low-confidence human handoff, and remains visible, permissioned, auditable, and cost-controlled.
- A Spring Boot microservice backend rather than a monolith.
- A React + TypeScript + Vite frontend.
- A future AI Agent Platform where agents are installable, permissioned members of Study Servers/channels.
- Later creator course commerce where instructors can sell courses inside a Study Server, learners can buy/enroll, course purchases unlock the right channels/resources/live classes, and instructors can run cohort sessions with community support in the same product.
- Later voice agents and a marketplace for agent personas, assistants, voice packs, prompt packs, tools, course offerings, and paid agents.

Current product positioning:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

## Current Repository State

Important files:

- `README.md`: current project overview and repository entry point.
- `HANDOFF.md`: this file.
- `CONTEXT.md`: canonical product glossary from the active `/grill-with-docs` session.
- `docs/product-design/`: **product showcase for agents and stakeholders** — start at `README.md`; includes `vision.md`, `visibility-and-social-model.md`, 19 UI mockups in `mockups/`, user-journey diagram in `diagrams/`, and optional interactive tour in `interactive/`.
- `docs/product/education-mvp-prd.md`: PRD for the education-focused Study Server MVP.
- `docs/issues/education-mvp-issue-breakdown.md`: GitHub-ready epics and vertical-slice stories for the education MVP.
- `plan.md`: main product, architecture, implementation, testing, scale, and AI-agent roadmap.
- `System Design.md`: detailed backend/system architecture explanation and diagrams.
- `docs/diagrams/`: editable draw.io **engineering** diagram sources plus embedded PNG exports referenced from `plan.md` and `System Design.md` (not product UI mockups — those live under `docs/product-design/`).
- `docs/architecture/social-hub-and-dm-voice.md`: post-MVP Friends Hub and DM voice (#31–#32).
- `docs/sessions/2026-06-16-product-strategy-grill-session.md`: readable recap of the product strategy discussion and active `/grill-with-docs` decision tree.
- `docs/operations/project-operations-bootstrap.md`: Milestone -1 operating model, tracker recommendation, bootstrap checklist, initial epics, and branch/PR conventions.
- `.github/PULL_REQUEST_TEMPLATE.md`: PR checklist aligned with architecture, security, testing, and operations expectations.
- `.github/ISSUE_TEMPLATE/`: GitHub issue forms for epics, stories, and bugs.
- `.gitignore`: initial ignore rules for Java, Node/Vite, Docker/runtime data, caches, and local secrets.

Bootstrap (#11) through **#20** (Approved FAQs) are merged on `main`. **#21** (TA Queue) is active. Cross-cutting auth is **#30**. Post-MVP social UX is **#31–#32**.

## Active Implementation

- **#11–#15** — merged (PRs #25–#33)
- **#16 Post A Support Question** — merged (PR #34)
- **#17 Upload Approved Course Resource** — merged (PR #35)
- **#18 Install AI Study Assistant** — merged (PR #36)
- **#19 Answer Grounded Support Question** — merged (PR #37)
- **#20 Promote Repeated Support Question To Approved FAQ** — merged (PR #38); CodeRabbit follow-up in PR
- **#21 Route Low-Confidence Answer To TA Queue** — next
- **Cross-cutting:** [#30 Wire Auth Service Principal](https://github.com/Vinosaamaa/chanter/issues/30) — after #24

## Major Decisions Made

Frontend:

- Use React + TypeScript + Vite.
- Use React Router, TanStack Query, Zustand, and likely Tailwind/shadcn-style UI.
- Build a Discord-like learning community shell focused on Study Servers, course/module channels, resources, question workflows, office hours, instructor dashboards, and AI Study Assistant controls.
- **Target product UI** (mockups, screen flows, platform delivery = browser web app): `docs/product-design/`. **Visibility:** global friends + enrollment-scoped **My courses** sidebar — `docs/product-design/visibility-and-social-model.md`. The current `frontend/src/App.tsx` is a vertical-slice **API demo**, not the production shell.

Backend:

- Use Java 21 and Spring Boot 3.
- Use true microservices, not a monolithic backend.
- Start locally with Docker Compose.
- Use PostgreSQL, Redis, Redpanda or Kafka-compatible broker, and MinIO.
- Keep service-owned databases and avoid cross-service database queries.
- Use OpenAPI contracts, versioned events, Flyway migrations, structured logs, health checks, and metrics.
- Use education-first domain language in docs and issues: Study Server, course/module channel, course resource, AI Study Assistant, question workflow, FAQ candidate, office-hours queue, instructor insight, and learner/instructor/TA roles.

Core backend services:

- Gateway Service
- Auth Service
- User Service
- Community Service
- Message Service
- Realtime Service
- Notification Service
- Moderation Service
- Media Service
- Search Service
- Analytics Service

AI agent services:

- Agent Service
- Agent Runtime Service
- Voice Agent Service
- Memory Service
- Marketplace Service
- Billing Service
- Safety Service

Education MVP product direction:

- Primary buyer: educators, bootcamps, tutoring businesses, cohort-based course creators, and learning communities.
- Initial business model: SaaS subscriptions.
- First product: Study Server.
- First agent: AI Study Assistant.
- First operational value: reduce repeated questions, improve answer quality, preserve learning knowledge, route unresolved questions to human help, and give instructors actionable analytics.
- Market reality: Discord is better for casual free chat; Chanter must win by solving paid education operations pain such as repeated questions, buried course knowledge, office-hours logistics, weak analytics, fragmented resources, and unsafe/uncontrolled bots.
- Later monetization direction: creator course commerce. Chanter can evolve into a community-native course platform where instructors sell courses, host live classes, publish recordings/transcripts/summaries, and keep learner support inside the Study Server. Keep this out of the first MVP because payments, refunds, tax handling, creator trust, fraud prevention, content moderation, and live-video reliability add major scope.

Identity and social model:

- Users have global accounts.
- Organizations/workspaces are optional at first and become important later for schools, bootcamps, SSO, domain verification, roster import, centralized billing, and policy enforcement.
- Roles use a layered model: Study Server Owner for governance; Instructor at Course scope; TA at Cohort scope; Learner through Enrollment. A user can hold different learning roles in different Courses or Cohorts within the same Study Server.
- A user can be an instructor in one Study Server and a learner in another.
- Instructor and TA powers are assigned by Study Server Owner or Course Instructor—not by self-declaration.
- Friend requests and DMs can exist later, but education deployments need consent, block/report controls, and policy settings for student-teacher messaging.

Scale direction:

- Start with local production-style microservices.
- Later evolve to regional/cell-based architecture for very large scale.
- Use sharding, partitioned event brokers, fan-out services, CQRS for message command/query, cross-region event replication, and strong observability.

## Engineering Workflow

Use installed Cursor workflow skills directly rather than the deleted local `chanter-engineering-workflow` skill.

**Git workflow (required):** one branch per GitHub issue → pull request → merge only after owner approval. See `docs/operations/project-operations-bootstrap.md`.

**Testing workflow:** infrastructure bootstrap (#11) may use smoke tests; **domain features from #12 onward use TDD** (red → green → refactor) for permissions, enrollment, and learning-support workflows.

The expected workflow includes:

- Deep planning and docs before large changes.
- PRD-style acceptance criteria.
- Vertical-slice issue breakdown.
- TDD for risky logic.
- Diagnose loop for bugs and regressions.
- Issue-scoped change logs for non-trivial implementation slices.
- Issue-scoped debug logs for meaningful local/browser failures.
- Issue-scoped CodeRabbit fix logs for every CodeRabbit suggestion that changes code or records an explicit follow-up (`docs/operations/issue-<N>-coderabbit-fix.md`). See `docs/operations/coderabbit-review-workflow.md`.
- Zoom-out architecture review after milestones.
- Prototyping for uncertain UX/system flows.
- Pre-commit and CI-style quality gates once code exists.
- CodeRabbit PR review loops after GitHub PRs are open (replaces Greptile / `greploop`; trial expired).

Use these installed skills by name when relevant:

- `grill-with-docs`: challenge and improve docs before durable product or architecture decisions.
- `to-prd`: turn product ideas into goals, non-goals, user flows, acceptance criteria, and test plans.
- `to-issues`: break roadmap work into epics, stories, and vertical slices.
- `tdd`: write failing tests first for risky implementation logic.
- `diagnose`: reproduce, minimize, hypothesize, fix, and regression-test bugs.
- `zoom-out` or `improve-codebase-architecture`: review architecture boundaries after major changes.
- `prototype`: explore uncertain UI or system flows before production implementation.
- `setup-pre-commit`: add quality gates after runnable code exists.
- **CodeRabbit review:** push to PR → GitHub review; or local `coderabbit review --agent --base main`. Fix loop and fix-log naming: `docs/operations/coderabbit-review-workflow.md`.

Diagram workflow:

- **Product UI** (mockups, user journeys): `docs/product-design/` — see `mockups/README.md` for the 19-screen gallery.
- **Engineering architecture** (services, data flows): `docs/diagrams/*.drawio` referenced from `plan.md` and `System Design.md`.
- PNG exports live beside draw.io sources as `*.drawio.png`, generated with embedded draw.io XML so they can be reopened for editing.
- Use `/drawio-skill` for future polished architecture/sequence diagram revisions.

## GitHub And Project Tracking Direction

Recommended enterprise workflow:

- GitHub owns source control, PRs, CI, branch protection, releases, and security scanning.
- Jira or an alternative owns epics, stories, bugs, sprint/status tracking, and acceptance criteria.
- If Jira is too heavy, consider Linear or GitHub Projects.

Recommended next setup phase:

- Configure repository labels.
- Add protected `main`, PR requirements, and status checks once checks exist.
- Create a GitHub Projects board for work tracking.
- Create initial GitHub issues/epics from `docs/issues/education-mvp-issue-breakdown.md`.

## Suggested Next Step

Before writing application code, add a new operations milestone:

Milestone -1: Project Operations Bootstrap

- Initialize git.
- Create/connect GitHub repository.
- Add repo metadata: `README.md`, `.gitignore`, `LICENSE` if desired, PR template, CODEOWNERS later.
- Use GitHub Projects for the first project tracker.
- Convert `docs/issues/education-mvp-issue-breakdown.md` into GitHub epics/stories.
- Decide branch naming, commit style, and PR requirements.

Current Milestone -1 local progress:

- Initialized local git on `main`.
- Added `README.md`.
- Added `.gitignore`.
- Added `.github/PULL_REQUEST_TEMPLATE.md`.
- Added GitHub issue forms for epics, stories, and bugs.
- Added `docs/operations/project-operations-bootstrap.md`.
- Added Milestone -1 to `plan.md`.
- Removed the local `.cursor/skills/chanter-engineering-workflow/SKILL.md` in favor of installed workflow skills.
- Replaced Mermaid diagrams in `plan.md` and `System Design.md` with draw.io PNG embeds and created editable sources/exports under `docs/diagrams/`.
- Confirmed education-first product strategy: Study Servers for learning communities, AI Study Assistant, instructor operations, and SaaS model.
- Added `docs/product/education-mvp-prd.md`.
- Added `docs/issues/education-mvp-issue-breakdown.md`.
- Added `docs/sessions/2026-06-16-product-strategy-grill-session.md` as a readable session recap.
- Updated `README.md`, `plan.md`, and `System Design.md` for the education MVP direction.
- Created private GitHub repository `https://github.com/Vinosaamaa/chanter`.
- Pushed initial planning/scaffolding commit to `origin/main`.

Active `/grill-with-docs` progress:

- **Complete.** Canonical glossary in `CONTEXT.md` (28 terms).
- Session log: `docs/sessions/2026-06-16-product-strategy-grill-session.md`.
- PRD aligned: `docs/product/education-mvp-prd.md`.
- Issue breakdown aligned: `docs/issues/education-mvp-issue-breakdown.md` (10 epics, 13 vertical slices).

Key grill outcomes for implementation:

- Layered roles: Owner (Study Server), Instructor (Course), TA (Cohort), Learner (Cohort Enrollment).
- Enrollment is primarily to a Cohort.
- MVP includes Discord-style Friend Requests, Direct Messages, and Study Server Voice Channels.
- Built-in Live Class video is post-MVP.
- TA Queue (async) plus Office Hours (scheduled live) for human support.
- Study Server Owner pays SaaS Plan.

Pending user confirmation:

- Whether to enable branch protection after first CI exists.

GitHub issues published (2026-06-17):

- Milestone: https://github.com/Vinosaamaa/chanter/milestone/1
- Project board: https://github.com/users/Vinosaamaa/projects/1 (Education MVP #1–#24, #30)
- Post-MVP project: https://github.com/users/Vinosaamaa/projects/2 (#31–#32, milestone Social Hub & Realtime)
- **#11 Monorepo bootstrap — CLOSED** (merged PR #25)
- **#12 Create A Study Server — CLOSED** (merged PR #26)
- **#13 Create Course, Cohort, And Enroll Learner — CLOSED** (merged PR #28)
- **#14 Join A Voice Channel — CLOSED** (merged PR #29)
- **#15 Send Friend Request And Direct Message — CLOSED** (merged PR #33)
- **Active: #16 Post A Support Question In A Course Channel** — https://github.com/Vinosaamaa/chanter/issues/16 ([PR #34](https://github.com/Vinosaamaa/chanter/pull/34))
- **Cross-cutting: #30 Wire Auth Service Principal** — https://github.com/Vinosaamaa/chanter/issues/30
- **Post-MVP: #31 Friends Hub And Live DM** — https://github.com/Vinosaamaa/chanter/issues/31
- **Post-MVP: #32 DM Voice Call** — https://github.com/Vinosaamaa/chanter/issues/32

Architecture for social UX: `docs/architecture/social-hub-and-dm-voice.md`

Implementation on `main`:

- `backend/` — gateway (:8080), auth (:8081), community (:8082), message (:8083), `common`
- `frontend/` — Vite vertical-slice demo (Study Server, voice, Friends/DM, support questions); target UI in `docs/product-design/mockups/`
- `infra/docker-compose.yml` — local Postgres, Redis, Redpanda, MinIO
- CI: backend `mvn verify`, frontend lint + build

Pending:

- Branch protection on `main` (optional, after owner enables)
- Merge #16 after review; then #17 Course Resources per issue breakdown

Confirmed decision:

- Use GitHub Projects for early project tracking, with Linear or Jira as upgrade paths if workflow needs grow.
- Start with the education market and SaaS model.
- Position Chanter as Discord for learning communities, with AI teaching assistants and instructor operations built in.

After bootstrap (#11) through Friends/DM API (#15), build toward the education MVP:

- **#16 (PR review):** Support Questions in `#questions` — `community-service` access + `message-service` persistence.
- **#17 next:** Course Resources per `docs/issues/education-mvp-issue-breakdown.md`.
- **#30 (cross-cutting):** wire Auth Service principal when ready to replace `TODO(#auth)` on protected endpoints.
- **Post-MVP social (#31–#32):** Friends Hub with presence and live DM panel, then DM voice calls—after `realtime-service` and WebRTC/LiveKit transport. Target UX mockups: `docs/product-design/mockups/friends-hub-dm.png`, `friend-requests.png`. See `docs/architecture/social-hub-and-dm-voice.md`.

Recent operations documentation artifacts:

- `docs/operations/issue-12-change-log.md`: change-by-change implementation log with representative code snippets.
- `docs/operations/issue-12-debug-log.md`: local browser 502/403 debug log and final verification notes.
- `docs/operations/issue-11-greptile-fix.md`: Greptile review feedback and fixes for PR #25.
- `docs/operations/issue-12-greptile-fix.md`: Greptile review feedback and fixes for PR #26.
- `docs/operations/issue-13-greptile-fix.md`: Greptile review feedback and fixes for PR #28.
- `docs/operations/issue-14-change-log.md`: issue #14 voice presence implementation log with representative snippets.
- `docs/operations/issue-14-debug-log.md`: issue #14 local Maven/dev-server debug log.
- `docs/operations/issue-14-greptile-fix.md`: Greptile review feedback and fixes for PR #29.

## New Chat Startup Prompt

Use this prompt after reloading Cursor or starting a new chat:

```text
Read HANDOFF.md and CONTEXT.md.

For product/UI intent before frontend work, read docs/product-design/README.md and docs/product-design/visibility-and-social-model.md.

Issues #11–#15 are merged on main. Issue #16 PR: https://github.com/Vinosaamaa/chanter/pull/34

Auth hardening: https://github.com/Vinosaamaa/chanter/issues/30

Repo: https://github.com/Vinosaamaa/chanter
Issue: https://github.com/Vinosaamaa/chanter/issues/16
```

## Notes For Future Agent

- Do not assume the user wants to code immediately; they have been exploring system design and enterprise workflow.
- **Product showcase:** `docs/product-design/` has target UI mockups, user-journey diagram, `vision.md`, and **`visibility-and-social-model.md`** (global friends vs my-courses sidebar). Use it when implementing frontend routes or explaining scope to stakeholders. `frontend/src/App.tsx` is an API demo, not the final shell.
- **Engineering diagrams:** `docs/diagrams/` is for service architecture — do not put product mockups there.
- **One GitHub issue → one branch → one PR → merge only after user approval.** Never push directly to `main` for any change, including docs. Use `make setup-git-hooks` to block accidental local pushes.
- Do not push after edits or commits unless the user explicitly approves the push as a separate action at push time.
- **TDD from issue #12 onward** for domain behavior; bootstrap/infra may use smoke tests only.
- For every non-trivial slice, create or update an issue-scoped change log in `docs/operations/` before final handoff.
- For every meaningful debugging incident, create or update an issue-scoped debug log in `docs/operations/` before final handoff.
- For every CodeRabbit suggestion that is fixed or explicitly deferred, create or update `docs/operations/issue-<number>-coderabbit-fix.md` (see `docs/operations/coderabbit-review-workflow.md`). Historical Greptile logs remain under `issue-*-greptile-fix.md`.
- Keep explanations beginner-friendly but production-oriented.
- Preserve and update the docs when architecture or process decisions change.
- Ask before creating remote repositories, pushing code, creating tickets, or installing third-party integrations.
- Treat `plan.md` and `System Design.md` as living design documents.
