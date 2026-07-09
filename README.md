# Chanter

Chanter is an education-first Discord-like learning community platform planned as a Spring Boot microservice system with a React, TypeScript, and Vite frontend. The first product wedge is Study Servers for educators, bootcamps, tutoring businesses, cohort-based course creators, and study groups.

Positioning:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

---

## New here? Run Chanter on your laptop

**Start here:** [`docs/operations/getting-started.md`](docs/operations/getting-started.md) — install tools, start the stack, seed demo users, and try each feature step by step (no prior repo knowledge required).

Quick version:

```bash
git clone https://github.com/Vinosaamaa/chanter.git && cd chanter
cp .env.example .env
echo 'DEMO_PASSWORD=chanter-dev-demo' >> .env
make product-up          # first run: several minutes
make product-health
make product-demo-seed   # demo logins + Study Server + AI Study Assistant
```

Open **http://localhost:5173** → sign in as `dev-demo-owner@chanter.local` / password from `DEMO_PASSWORD` in `.env`.

Full two-user demo checklist: [`docs/operations/workable-product-demo.md`](docs/operations/workable-product-demo.md).

---

The roadmap includes realtime course chat, Study Servers, course/module channels, instructor/TA/learner roles, course resources, question workflows, office-hours queues, learning analytics, and a first-party AI Study Assistant. Later phases expand into voice agents, marketplace agents, broader enterprise learning, and paid agent templates.

## Current Status

**Backend MVP (milestone 1)** — issues **#11–#24** merged.

**Production Frontend (milestone 3)** — issues **#47–#59** merged (full UI shell, live text, search, instructor tools).

**Workable Product (milestone 4)** — **#60–#63**, #31–#32 merged. Run the full local product with `make product-up` — see [Getting started](docs/operations/getting-started.md).

GitHub repository: `https://github.com/Vinosaamaa/chanter`

### Project boards (issue order on each board = implementation order)

| Project | URL | Scope |
|---------|-----|--------|
| Education MVP (historical) | [projects/1](https://github.com/users/Vinosaamaa/projects/1) | Backend slices #1–#24 |
| **Production Frontend** | [**projects/3**](https://github.com/users/Vinosaamaa/projects/3) | **#47–#59** — **complete** |
| [**Workable Product**](https://github.com/users/Vinosaamaa/projects/4) | [**projects/4**](https://github.com/users/Vinosaamaa/projects/4) | **#60–#63**, #31–#32 — **complete** |
| **Public Launch** | [**projects/5**](https://github.com/users/Vinosaamaa/projects/5) | **#82–#104** — UI polish, real AI, launch readiness — **active** |

**Agent workflow (order + loop + merge policy):** [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md) (mandatory for all agents).

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
| [`docs/issues/education-mvp-issue-breakdown.md`](docs/issues/education-mvp-issue-breakdown.md) | Backend epics and slices (#11–#24) — **done** |
| [`docs/issues/production-frontend-issue-breakdown.md`](docs/issues/production-frontend-issue-breakdown.md) | Production UI slices (#47–#59) — **active** |
| [`docs/issues/workable-product-issue-breakdown.md`](docs/issues/workable-product-issue-breakdown.md) | Workable full-stack app (#60–#63, #31–#32) |
| [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md) | **Mandatory agent workflow** — issue order, completion loop, owner-only merge |
| [`docs/issues/agent-roadmap.md`](docs/issues/agent-roadmap.md) | Redirect → `agent-workflow.md` |
| [`plan.md`](plan.md) | Roadmap, milestones, frontend/backend direction, scale and AI-agent phases |
| [`System Design.md`](System Design.md) | Backend architecture, service boundaries, event flows (engineering diagrams in `docs/diagrams/`) |
| [`docs/diagrams/`](docs/diagrams/) | Editable draw.io **architecture** sources + PNG exports for `plan.md` / `System Design.md` |
| [`docs/operations/getting-started.md`](docs/operations/getting-started.md) | **Run locally** — beginner step-by-step (start here if you are new) |
| [`docs/operations/workable-product-demo.md`](docs/operations/workable-product-demo.md) | Full two-user E2E demo checklist |
| [`docs/operations/ai-study-assistant.md`](docs/operations/ai-study-assistant.md) | How the Study Assistant works today (no external LLM API yet) |

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
- **cubic Dev AI** for PR review loops — see [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md) § cubic review. CodeRabbit and Greptile/`greploop` retired (trials expired).

## Architecture Direction

- Frontend: React, TypeScript, Vite, React Router, TanStack Query, Zustand, and a component system chosen during implementation.
- Backend: Java 21, Spring Boot 3, true microservices, service-owned PostgreSQL data, Redis, Redpanda or Kafka-compatible event broker, MinIO-compatible object storage, OpenAPI contracts, Flyway migrations, structured logs, health checks, metrics, and Docker Compose local deployment.
- Education MVP: Study Servers, course/module channels, learner/instructor/TA roles, approved course resources, question workflow, office-hours queue, instructor dashboard, and SaaS plan limits.
- AI platform: the first agent is a visible, permissioned AI Study Assistant that answers from approved course resources and allowed context. Marketplace, voice, advanced billing, safety, and memory deepen after the Study Assistant is trusted.

## Local Development

**New to the project?** Use [`docs/operations/getting-started.md`](docs/operations/getting-started.md) — full beginner walkthrough.

Prerequisites: Java 21+, Node 20+, Maven 3.9+, Docker Desktop running.

### One-command product stack (recommended)

```bash
cp .env.example .env
echo 'DEMO_PASSWORD=chanter-dev-demo' >> .env   # required for product-demo-seed
make product-up          # infra + realtime + LiveKit + all services + frontend
make product-health      # verify gateway, auth, realtime, LiveKit
make product-demo-seed   # demo users, Study Server, friendship, AI Study Assistant
make product-down        # stop app processes and product Docker services
```

Open **http://localhost:5173** — the frontend proxies `/api` to the gateway at **http://localhost:8080**.

Demo logins after seed: `dev-demo-owner@chanter.local` / `dev-demo-learner@chanter.local` — password `chanter-dev-demo`.

Guides:

- **Getting started (step-by-step):** [`docs/operations/getting-started.md`](docs/operations/getting-started.md)
- **Full two-user E2E demo:** [`docs/operations/workable-product-demo.md`](docs/operations/workable-product-demo.md)

### Manual multi-terminal setup

```bash
cp .env.example .env
make infra-up          # PostgreSQL, Redis, Redpanda, MinIO
make backend-test      # requires JAVA_HOME 21+ (see .java-version)
make frontend-install
make backend-auth      # terminal 1 — port 8081
make backend-community # terminal 2 — port 8082
make backend-message   # terminal 3 — port 8083
make backend-media     # terminal 4 — port 8084
make backend-agent     # terminal 5 — port 8085
make backend-analytics # terminal 6 — port 8086
make backend-search    # terminal 7 — port 8088
make backend-gateway   # terminal 8 — port 8080
make frontend-dev      # terminal 9 — http://localhost:5173
```

For live channel text chat, also start realtime: `docker compose -f infra/docker-compose.yml --profile product up -d realtime-service`

The frontend proxies `/api` and `/actuator` to the gateway. Bootstrap health endpoints:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/api/v1/auth/health`
- `http://localhost:8080/api/v1/study-servers`

## Next Milestone

**Active:** [Public Launch — issue #86](https://github.com/Vinosaamaa/chanter/issues/86) on [project board #5](https://github.com/users/Vinosaamaa/projects/5) ([repo Projects tab](https://github.com/Vinosaamaa/chanter/projects)).

**Definition of workable local product:** [`docs/operations/workable-product-demo.md`](docs/operations/workable-product-demo.md)

See [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md) for the full ordered list through Public Launch (project #5).
