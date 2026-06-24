# Chanter

Chanter is an education-first Discord-like learning community platform planned as a Spring Boot microservice system with a React, TypeScript, and Vite frontend. The first product wedge is Study Servers for educators, bootcamps, tutoring businesses, cohort-based course creators, and study groups.

Positioning:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

The roadmap includes realtime course chat, Study Servers, course/module channels, instructor/TA/learner roles, course resources, question workflows, office-hours queues, learning analytics, and a first-party AI Study Assistant. Later phases expand into voice agents, marketplace agents, broader enterprise learning, and paid agent templates.

## Current Status

Milestone 0 (monorepo bootstrap) through [issue #20](https://github.com/Vinosaamaa/chanter/issues/20) (Approved FAQs, including CodeRabbit follow-up PR #40) are merged on `main`. [Issue #21](https://github.com/Vinosaamaa/chanter/issues/21) (TA Queue) is in progress.

GitHub repository: `https://github.com/Vinosaamaa/chanter`  
Project board: `https://github.com/users/Vinosaamaa/projects/1` (Education MVP) · Post-MVP: `https://github.com/users/Vinosaamaa/projects/2` (#31–#32)

Implemented bootstrap:

- `backend/` — Maven multi-module Spring Boot (`gateway-service`, `auth-service`, `community-service`, `message-service`, `common`; other service dirs reserved)
- `frontend/` — React + TypeScript + Vite shell for Study Server setup, voice presence, and Friends/DM API demo
- `infra/docker-compose.yml` — PostgreSQL, Redis, Redpanda, MinIO
- `.github/workflows/ci.yml` — backend + frontend build checks

Key planning files:

| Path | Purpose |
|---|---|
| [`HANDOFF.md`](HANDOFF.md) | **Start here** for new agent sessions — current slice, workflow, startup prompt |
| [`CONTEXT.md`](CONTEXT.md) | Canonical product glossary (Study Server, Cohort, Support Question, …) |
| [`docs/product-design/`](docs/product-design/README.md) | **Product showcase** — target UI mockups (19 screens), vision walkthrough, **visibility and social model**, user-journey diagram, interactive screen tour |
| [`docs/product/education-mvp-prd.md`](docs/product/education-mvp-prd.md) | Education MVP PRD — problem, user stories, out of scope |
| [`docs/issues/education-mvp-issue-breakdown.md`](docs/issues/education-mvp-issue-breakdown.md) | Epics and vertical slices (#11–#24) |
| [`plan.md`](plan.md) | Roadmap, milestones, frontend/backend direction, scale and AI-agent phases |
| [`System Design.md`](System Design.md) | Backend architecture, service boundaries, event flows (engineering diagrams in `docs/diagrams/`) |
| [`docs/diagrams/`](docs/diagrams/) | Editable draw.io **architecture** sources + PNG exports for `plan.md` / `System Design.md` |
| [`docs/operations/`](docs/operations/) | Issue-scoped change logs, debug logs, CodeRabbit fix logs |

**Product vs engineering visuals:** `docs/product-design/mockups/` = target **browser UI** concepts for educators and learners. `docs/diagrams/` = **system architecture** and data-flow diagrams. Do not confuse the API demo in `frontend/src/App.tsx` with the mockups.

Diagram workflow:

- Edit `.drawio` source files in draw.io/diagrams.net.
- Re-export PNG with embedded diagram XML so the exports remain editable later.
- Use PNG references in Markdown and keep `.drawio` sources available for precise edits.

## Agent Workflow

Use `HANDOFF.md` as the first resume point for new agent sessions. For **what the finished product should look like**, read [`docs/product-design/README.md`](docs/product-design/README.md) and [`docs/product-design/visibility-and-social-model.md`](docs/product-design/visibility-and-social-model.md) before building UI. Then apply the installed workflow skills by name as needed, especially:

- `grill-with-docs` for questioning and tightening docs before major decisions.
- `to-prd` for turning unclear product ideas into requirements and acceptance criteria.
- `to-issues` for breaking roadmap work into reviewable epics/stories.
- `tdd` for risky implementation areas such as auth, permissions, realtime authorization, billing, memory, and agent tools.
- `diagnose` for bugs, regressions, flaky tests, or performance issues.
- `zoom-out` or `improve-codebase-architecture` for architecture reviews.
- `prototype` for uncertain UI or system interaction flows.
- `setup-pre-commit` after runnable code and checks exist.
- `coderabbit` for PR review loops — see [`docs/operations/coderabbit-review-workflow.md`](docs/operations/coderabbit-review-workflow.md). Greptile/`greploop` retired (trial expired).

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

Active: [#16 Post A Support Question In A Course Channel](https://github.com/Vinosaamaa/chanter/issues/16) ([PR #34](https://github.com/Vinosaamaa/chanter/pull/34)).
