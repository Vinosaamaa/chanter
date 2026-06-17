# Chanter Project Handoff

## Project Summary

Chanter is planned as an enterprise-grade, education-first Discord-like learning community platform with:

- Study Servers for educators, bootcamps, tutoring businesses, cohort-based course creators, and study groups.
- Real-time course/module channels, text chat, instructor/TA/learner roles, permissions, moderation, notifications, course resources, search, learning analytics, and local production-style deployment.
- A first-party AI Study Assistant that answers from approved course resources and allowed context, supports low-confidence human handoff, and remains visible, permissioned, auditable, and cost-controlled.
- A Spring Boot microservice backend rather than a monolith.
- A React + TypeScript + Vite frontend.
- A future AI Agent Platform where agents are installable, permissioned members of Study Servers/channels.
- Later voice agents and a marketplace for agent personas, assistants, voice packs, prompt packs, tools, and paid agents.

Current product positioning:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

## Current Repository State

Important files:

- `README.md`: current project overview and repository entry point.
- `plan.md`: main product, architecture, implementation, testing, scale, and AI-agent roadmap.
- `System Design.md`: detailed backend/system architecture explanation and diagrams.
- `docs/product/education-mvp-prd.md`: PRD for the education-focused Study Server MVP.
- `docs/issues/education-mvp-issue-breakdown.md`: GitHub-ready epics and vertical-slice stories for the education MVP.
- `docs/diagrams/`: editable draw.io diagram sources plus embedded PNG exports referenced from `plan.md` and `System Design.md`.
- `docs/operations/project-operations-bootstrap.md`: Milestone -1 operating model, tracker recommendation, bootstrap checklist, initial epics, and branch/PR conventions.
- `.github/PULL_REQUEST_TEMPLATE.md`: PR checklist aligned with architecture, security, testing, and operations expectations.
- `.github/ISSUE_TEMPLATE/`: GitHub issue forms for epics, stories, and bugs.
- `.gitignore`: initial ignore rules for Java, Node/Vite, Docker/runtime data, caches, and local secrets.
- `HANDOFF.md`: this file.

No application code has been bootstrapped yet. The repo currently contains planning/design assets, the education MVP PRD, GitHub-ready local issue breakdown, editable draw.io architecture diagrams, local repository metadata/templates, and an operations bootstrap doc. The local `main` branch tracks `origin/main` at `https://github.com/Vinosaamaa/chanter`.

## Major Decisions Made

Frontend:

- Use React + TypeScript + Vite.
- Use React Router, TanStack Query, Zustand, and likely Tailwind/shadcn-style UI.
- Build a Discord-like learning community shell focused on Study Servers, course/module channels, resources, question workflows, office hours, instructor dashboards, and AI Study Assistant controls.

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

Scale direction:

- Start with local production-style microservices.
- Later evolve to regional/cell-based architecture for very large scale.
- Use sharding, partitioned event brokers, fan-out services, CQRS for message command/query, cross-region event replication, and strong observability.

## Engineering Workflow

Use installed Cursor workflow skills directly rather than the deleted local `chanter-engineering-workflow` skill.

The expected workflow includes:

- Deep planning and docs before large changes.
- PRD-style acceptance criteria.
- Vertical-slice issue breakdown.
- TDD for risky logic.
- Diagnose loop for bugs and regressions.
- Zoom-out architecture review after milestones.
- Prototyping for uncertain UX/system flows.
- Pre-commit and CI-style quality gates once code exists.
- Greploop later for PR review loops after GitHub and Greptile are configured.

Use these installed skills by name when relevant:

- `grill-with-docs`: challenge and improve docs before durable product or architecture decisions.
- `to-prd`: turn product ideas into goals, non-goals, user flows, acceptance criteria, and test plans.
- `to-issues`: break roadmap work into epics, stories, and vertical slices.
- `tdd`: write failing tests first for risky implementation logic.
- `diagnose`: reproduce, minimize, hypothesize, fix, and regression-test bugs.
- `zoom-out` or `improve-codebase-architecture`: review architecture boundaries after major changes.
- `prototype`: explore uncertain UI or system flows before production implementation.
- `setup-pre-commit`: add quality gates after runnable code exists.
- `greploop`: use later for PR review loops after GitHub and Greptile are configured.

Diagram workflow:

- Existing Mermaid diagrams in `plan.md` and `System Design.md` were replaced with draw.io PNG embeds.
- Canonical editable sources live in `docs/diagrams/*.drawio`.
- PNG exports live beside the sources as `*.drawio.png`.
- The PNG exports were generated with embedded draw.io XML, so they can also be reopened in draw.io/diagrams.net for editing.
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
- Updated `README.md`, `plan.md`, and `System Design.md` for the education MVP direction.
- Created private GitHub repository `https://github.com/Vinosaamaa/chanter`.
- Pushed initial planning/scaffolding commit to `origin/main`.

Pending user confirmation:

- Whether to configure repository labels and branch protection now.
- Whether to create the GitHub Projects board and initial issues now.

Confirmed decision:

- Use GitHub Projects for early project tracking, with Linear or Jira as upgrade paths if workflow needs grow.
- Start with the education market and SaaS model.
- Position Chanter as Discord for learning communities, with AI teaching assistants and instructor operations built in.

After that, start Milestone 0 and build toward the education MVP:

- Bootstrap monorepo layout.
- Initialize Spring Boot services.
- Initialize React Vite frontend.
- Add Docker Compose with PostgreSQL, Redis, Redpanda, and MinIO.
- First implementation vertical slice after bootstrap: educator creates a Study Server with default course channels and instructor/TA/learner roles.

## New Chat Startup Prompt

Use this prompt after reloading Cursor or starting a new chat:

```text
Read HANDOFF.md, plan.md, and System Design.md.

Use installed workflow skills directly as needed: grill-with-docs, to-prd, to-issues, tdd, diagnose, zoom-out, improve-codebase-architecture, prototype, setup-pre-commit, and greploop.

Continue the Chanter project. We are building an education-first Discord-like learning community SaaS app with Spring Boot microservices, React + TypeScript + Vite, Docker Compose local deployment, Study Servers, course resources, office-hours workflows, instructor analytics, and a first-party AI Study Assistant. The private GitHub repository is `https://github.com/Vinosaamaa/chanter`, and local `main` tracks `origin/main`. The next likely step is project operations bootstrap: configure repository labels/branch protection, then create the GitHub Projects board and initial issues from `docs/issues/education-mvp-issue-breakdown.md` before writing application code.
```

## Notes For Future Agent

- Do not assume the user wants to code immediately; they have been exploring system design and enterprise workflow.
- Keep explanations beginner-friendly but production-oriented.
- Preserve and update the docs when architecture or process decisions change.
- Ask before creating remote repositories, pushing code, creating tickets, or installing third-party integrations.
- Treat `plan.md` and `System Design.md` as living design documents.
