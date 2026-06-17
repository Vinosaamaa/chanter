# Chanter

Chanter is an education-first Discord-like learning community platform planned as a Spring Boot microservice system with a React, TypeScript, and Vite frontend. The first product wedge is Study Servers for educators, bootcamps, tutoring businesses, cohort-based course creators, and study groups.

Positioning:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

The roadmap includes realtime course chat, Study Servers, course/module channels, instructor/TA/learner roles, course resources, question workflows, office-hours queues, learning analytics, and a first-party AI Study Assistant. Later phases expand into voice agents, marketplace agents, broader enterprise learning, and paid agent templates.

## Current Status

Milestone 0 (monorepo bootstrap) and [issue #12](https://github.com/Vinosaamaa/chanter/issues/12) (Create A Study Server) are merged on `main`. [Issue #13](https://github.com/Vinosaamaa/chanter/issues/13), Create Course, Cohort, And Enroll Learner, is implemented in PR #28 and ready for owner merge. After #13 is merged and `main` is synced, the next education MVP slice is [issue #14](https://github.com/Vinosaamaa/chanter/issues/14), Join A Voice Channel.

GitHub repository: `https://github.com/Vinosaamaa/chanter`  
Project board: `https://github.com/users/Vinosaamaa/projects/1`

Implemented bootstrap:

- `backend/` — Maven multi-module Spring Boot (`gateway-service`, `auth-service`, `community-service`, `common`; other service dirs reserved)
- `frontend/` — React + TypeScript + Vite shell for creating and viewing a Study Server
- `infra/docker-compose.yml` — PostgreSQL, Redis, Redpanda, MinIO
- `.github/workflows/ci.yml` — backend + frontend build checks

Key planning files:

- `CONTEXT.md`: canonical product glossary.
- `plan.md`: product roadmap, implementation milestones, testing strategy, scale direction, and AI-agent roadmap.
- `System Design.md`: backend/system architecture, service boundaries, event flow, consistency model, reliability rules, and agent platform design.
- `HANDOFF.md`: current state and startup guidance for future Cursor sessions.
- `docs/product/education-mvp-prd.md`: PRD for the education-focused Study Server MVP.
- `docs/issues/education-mvp-issue-breakdown.md`: GitHub-ready epics and vertical-slice stories for the education MVP.
- `docs/diagrams/`: editable draw.io sources plus embedded PNG exports used by `plan.md` and `System Design.md`.

Diagram workflow:

- Edit `.drawio` source files in draw.io/diagrams.net.
- Re-export PNG with embedded diagram XML so the exports remain editable later.
- Use PNG references in Markdown and keep `.drawio` sources available for precise edits.

## Agent Workflow

Use `HANDOFF.md` as the first resume point for new agent sessions. Then apply the installed workflow skills by name as needed, especially:

- `grill-with-docs` for questioning and tightening docs before major decisions.
- `to-prd` for turning unclear product ideas into requirements and acceptance criteria.
- `to-issues` for breaking roadmap work into reviewable epics/stories.
- `tdd` for risky implementation areas such as auth, permissions, realtime authorization, billing, memory, and agent tools.
- `diagnose` for bugs, regressions, flaky tests, or performance issues.
- `zoom-out` or `improve-codebase-architecture` for architecture reviews.
- `prototype` for uncertain UI or system interaction flows.
- `setup-pre-commit` after runnable code and checks exist.
- `greploop` later, after GitHub PRs and the review integration are configured.

## Architecture Direction

- Frontend: React, TypeScript, Vite, React Router, TanStack Query, Zustand, and a component system chosen during implementation.
- Backend: Java 21, Spring Boot 3, true microservices, service-owned PostgreSQL data, Redis, Redpanda or Kafka-compatible event broker, MinIO-compatible object storage, OpenAPI contracts, Flyway migrations, structured logs, health checks, metrics, and Docker Compose local deployment.
- Education MVP: Study Servers, course/module channels, learner/instructor/TA roles, approved course resources, question workflow, office-hours queue, instructor dashboard, and SaaS plan limits.
- AI platform: the first agent is a visible, permissioned AI Study Assistant that answers from approved course resources and allowed context. Marketplace, voice, advanced billing, safety, and memory deepen after the Study Assistant is trusted.

## Local Development

Prerequisites: Java 21+, Node 20+, Maven 3.9+, Docker (for infra).

```bash
cp .env.example .env
make infra-up          # PostgreSQL, Redis, Redpanda, MinIO
make backend-test      # requires JAVA_HOME 21+ (see .java-version)
make frontend-install
make backend-auth      # terminal 1 — port 8081
make backend-community # terminal 2 — port 8082
make backend-gateway   # terminal 3 — port 8080
make frontend-dev      # terminal 4 — http://localhost:5173
```

The frontend proxies `/api` and `/actuator` to the gateway. Bootstrap health endpoints:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/api/v1/auth/health`
- `http://localhost:8080/api/v1/study-servers`

## Next Milestone

Next after PR #28 merges: [#14 Join A Voice Channel](https://github.com/Vinosaamaa/chanter/issues/14).
