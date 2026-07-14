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
- `docs/product-design/`: **product showcase for agents and stakeholders** — start at `README.md`; **v2 UI:** [`DESIGN-DECISIONS.md`](docs/product-design/DESIGN-DECISIONS.md) + approved PNGs in `mockups/learner-flow/` and `mockups/owner-flow/`; includes `vision.md`, `visibility-and-social-model.md`, legacy gallery in `mockups/`, user-journey diagram in `diagrams/`, and optional interactive tour in `interactive/`.
- `docs/product/education-mvp-prd.md`: PRD for the education-focused Study Server MVP.
- `docs/issues/education-mvp-issue-breakdown.md`: GitHub-ready epics and vertical-slice stories for the education MVP (**#11–#24, done**).
- `docs/issues/production-frontend-issue-breakdown.md`: Legacy Production UI phase (**#47–#59**, project #3) — **superseded for layout**.
- `docs/issues/ui-v2-issue-breakdown.md`: **UI v2 implementation** — course-first shell (**#115–#128**, milestone 7).
- `docs/issues/workable-product-issue-breakdown.md`: Workable full-stack local app (**#60–#63**, #31–#32, project #4).
- `docs/issues/public-launch-issue-breakdown.md`: Public Launch (**#82–#104**, project #5).
- `docs/operations/agent-workflow.md`: **Mandatory agent workflow** — issue order, autonomous gated merge policy, **CodeAnt AI** PR review (cubic retired).
- `plan.md`: main product, architecture, implementation, testing, scale, and AI-agent roadmap.
- `System Design.md`: detailed backend/system architecture explanation and diagrams.
- `docs/diagrams/`: editable draw.io **engineering** diagram sources plus embedded PNG exports referenced from `plan.md` and `System Design.md` (not product UI mockups — those live under `docs/product-design/`).
- `docs/architecture/social-hub-and-dm-voice.md`: Friends Hub and DM voice (#31–#32) on **Workable Product** project #4.
- `docs/sessions/2026-06-16-product-strategy-grill-session.md`: readable recap of the product strategy discussion and active `/grill-with-docs` decision tree.
- `docs/operations/project-operations-bootstrap.md`: Milestone -1 operating model, tracker recommendation, bootstrap checklist, initial epics, and branch/PR conventions.
- `.github/PULL_REQUEST_TEMPLATE.md`: PR checklist aligned with architecture, security, testing, and operations expectations.
- `.github/ISSUE_TEMPLATE/`: GitHub issue forms for epics, stories, and bugs.
- `.gitignore`: initial ignore rules for Java, Node/Vite, Docker/runtime data, caches, and local secrets.

Bootstrap (**#11**) through **#24** (SaaS plan limits) are **merged** on `main`. Production frontend **#48**, auth **#49** + backend principal **#30**, and study shell **#50** are **merged**.

**Active phase:** [**#131 — Make UI v2 fully operational**](https://github.com/Vinosaamaa/chanter/issues/131), on [Public Launch project #5](https://github.com/users/Vinosaamaa/projects/5). UI v2 **#116–#128** is merged in PR #130.

**Active slice:** [**#135 — Durable Office Hours scheduling and live controls**](https://github.com/Vinosaamaa/chanter/issues/135) on `feature/135-durable-office-hours`. #109 merged in PR #149. #135 implementation, TDD coverage, full frontend/community-service tests, and a live two-user gateway/LiveKit-token smoke are complete locally. The visible browser/audio pass remains pending because the Mac was locked during automation. Next: commit, PR, CI + CodeAnt loop, autonomous merge, then **#92**.

**Historical handoff:** `/tmp/chanter-handoff-ui-v2-codex.md` (2026-07-13) explains the rejected Cursor bulk build. Codex reimplemented #117–#128 with mockup and responsive browser verification.

**Paused:** Public Launch legacy UI polish **#88–#93** until the UI v2 PR merges. AI + launch slices **#94+** are unchanged.

**Agent read order for UI work:** `DESIGN-DECISIONS.md` → `specs/layout-rules.md` → issue mockup PNG(s) → `visibility-and-social-model.md`. Breakdown: [`docs/issues/ui-v2-issue-breakdown.md`](docs/issues/ui-v2-issue-breakdown.md).

**TDD policy:** Issues **#47–#55** were built test-last (manual/browser verification). **From #56 onward**, agents must follow vertical-slice TDD per `docs/operations/agent-workflow.md` § Test-driven development.

Cross-cutting auth **#30** pairs with **#49** during Production Frontend (partial principal retrofit; office hours / messaging deferred).

## Active Implementation

Backend MVP (**merged**):

- **#11–#24** — all education MVP vertical slices merged on `main` (through SaaS plan limits, PR #46).

Production Frontend (**complete** — [project #3](https://github.com/users/Vinosaamaa/projects/3)):

| Order | Issue | Notes |
|------:|-------|-------|
| 1 | [#47](https://github.com/Vinosaamaa/chanter/issues/47) | Epic |
| 2 | [**#48**](https://github.com/Vinosaamaa/chanter/issues/48) | Foundation — **merged** |
| 3 | [**#49**](https://github.com/Vinosaamaa/chanter/issues/49) | Auth UI — **merged** (with #30, PR #66) |
| 4 | [**#30**](https://github.com/Vinosaamaa/chanter/issues/30) | Auth backend — **merged** (with #49, PR #66) |
| 5 | [**#50**](https://github.com/Vinosaamaa/chanter/issues/50) | Study Server shell — **merged** |
| 6 | [**#51**](https://github.com/Vinosaamaa/chanter/issues/51) | Realtime text chat — **merged** (PR #68) |
| 7 | [**#52**](https://github.com/Vinosaamaa/chanter/issues/52) | `#questions` + AI context panel — **merged** (PR #69) |
| 8 | [**#53**](https://github.com/Vinosaamaa/chanter/issues/53) | Course resources panel — **merged** (PR #70) |
| 9 | [**#54**](https://github.com/Vinosaamaa/chanter/issues/54) | Support operations UI — **merged** (PR #71) |
| 10 | [**#55**](https://github.com/Vinosaamaa/chanter/issues/55) | Instructor dashboard & SaaS plan — **merged** (PR #72) |
| 11 | [**#56**](https://github.com/Vinosaamaa/chanter/issues/56) | Onboarding & enrollment — **merged** (PR #73) |
| 12 | [**#57**](https://github.com/Vinosaamaa/chanter/issues/57) | Global search — **merged** (PR #74) |
| 13 | [**#58**](https://github.com/Vinosaamaa/chanter/issues/58) | Channel summary UI — **merged** (PR #75) |
| 14 | [**#59**](https://github.com/Vinosaamaa/chanter/issues/59) | Public marketing landing page — **merged** (PR #76) |

Workable Product (**active** — [project #4](https://github.com/users/Vinosaamaa/projects/4)):

| Order | Issue | Notes |
|------:|-------|-------|
| 1 | [#60](https://github.com/Vinosaamaa/chanter/issues/60) | Epic |
| 2 | [**#62**](https://github.com/Vinosaamaa/chanter/issues/62) | One-command stack — **merged** (PR #77) |
| 3 | [**#61**](https://github.com/Vinosaamaa/chanter/issues/61) | Voice WebRTC + LiveKit — **merged** (PR #78) |
| 4 | [**#31**](https://github.com/Vinosaamaa/chanter/issues/31) | Friends Hub — **merged** (PR #79) |
| 5 | [**#32**](https://github.com/Vinosaamaa/chanter/issues/32) | DM voice — **merged** (PR #80) |
| 6 | [**#63**](https://github.com/Vinosaamaa/chanter/issues/63) | E2E demo — **merged** (PR #81) |

Production Frontend table retained above for history; milestone **complete** (legacy shell — superseded by UI v2 **#115–#128**).

### UI v2 — Course-first shell (merged)

**Goal:** Rebuild production frontend to match v2 mockups and [`DESIGN-DECISIONS.md`](docs/product-design/DESIGN-DECISIONS.md).  
**Breakdown:** [`docs/issues/ui-v2-issue-breakdown.md`](docs/issues/ui-v2-issue-breakdown.md)  
**Design parent:** [#114](https://github.com/Vinosaamaa/chanter/issues/114) (closed)

| Order | Issue | Notes |
|------:|-------|-------|
| 1 | [#115](https://github.com/Vinosaamaa/chanter/issues/115) | Epic |
| 2 | [**#116**](https://github.com/Vinosaamaa/chanter/issues/116) | v2 app shell foundation — **merged** |
| 3 | [#117](https://github.com/Vinosaamaa/chanter/issues/117) | Auth and onboarding v2 — **merged** |
| 4 | [#118](https://github.com/Vinosaamaa/chanter/issues/118) | Home, Inbox, Calendar — **merged** |
| 5 | [#119](https://github.com/Vinosaamaa/chanter/issues/119) | Course workspace — Overview + Chat — **merged** |
| 6 | [#120](https://github.com/Vinosaamaa/chanter/issues/120) | Course workspace — Questions + AI — **merged** |
| 7 | [#121](https://github.com/Vinosaamaa/chanter/issues/121) | Course workspace — Resources — **merged** |
| 8 | [#122](https://github.com/Vinosaamaa/chanter/issues/122) | Course workspace — Office Hours — **merged** |
| 9 | [#123](https://github.com/Vinosaamaa/chanter/issues/123) | Course workspace — People — **merged** |
| 10 | [#124](https://github.com/Vinosaamaa/chanter/issues/124) | Community hub — five tabs — **merged** |
| 11 | [#125](https://github.com/Vinosaamaa/chanter/issues/125) | Teaching dashboard + billing — **merged** |
| 12 | [#126](https://github.com/Vinosaamaa/chanter/issues/126) | Friends + DM v2 chrome — **merged** |
| 13 | [#127](https://github.com/Vinosaamaa/chanter/issues/127) | Owner course/cohort + events — **merged** |
| 14 | [#128](https://github.com/Vinosaamaa/chanter/issues/128) | Landing and marketing v2 — **merged** |

### UI v2 operationalization (active)

Epic [#131](https://github.com/Vinosaamaa/chanter/issues/131) turns the approved UI into a truthful full-stack product. Work in this order: **#132**, **#133**, **#134**, **#109**, **#135**, **#92**, **#136–#145**, then AI **#94–#100**, E2E **#103**, staging/auth **#101–#102**, and beta **#104**.

#132–#134 and #109 are merged. Current #135 operationalizes durable Office Hours scheduling, direct listener joins, hand raising, speaking grants, LiveKit permissions, and instructor lifecycle controls. Next, #92 operationalizes Questions, support, and Teaching.

**#30** ships in phase 2 with **#49** (project #3).

Full tables: [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md).

## Major Decisions Made

Frontend:

- Use React + TypeScript + Vite.
- Use React Router, TanStack Query, Zustand, and Tailwind/shadcn-style UI (**#48** landed the base stack).
- Build a Discord-like learning community shell focused on Study Servers, course/module channels, resources, question workflows, office hours, instructor dashboards, and AI Study Assistant controls.
- **Target product UI** (mockups, screen flows, platform delivery = browser web app): `docs/product-design/`. **v2 canonical:** `docs/product-design/DESIGN-DECISIONS.md`. **Visibility:** global friends + enrollment-scoped course sidebar — `docs/product-design/visibility-and-social-model.md`. Legacy API demo: `/dev/demo`.

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

**Git workflow (required):** one issue per branch and PR → CI → initial CodeAnt review → up to three remediation rounds → agent merge → next issue. Master doc: `docs/operations/agent-workflow.md`. Also enforced in `.cursor/rules/git-workflow.mdc`. Never push directly to `main`; do not stop after opening a PR.

**Testing workflow:** backend bootstrap (#11) may use smoke tests; **backend domain features #12–#24 use TDD**. Production frontend **#47–#55** were test-last; **from #56 onward** use vertical-slice TDD per `agent-workflow.md`.

The expected workflow includes:

- Deep planning and docs before large changes.
- PRD-style acceptance criteria.
- Vertical-slice issue breakdown.
- TDD for risky logic.
- Diagnose loop for bugs and regressions.
- Issue-scoped change logs for non-trivial implementation slices.
- Issue-scoped debug logs for meaningful local/browser failures.
- Issue-scoped **CodeAnt** fix logs for every CodeAnt suggestion that changes code or records an explicit follow-up (`docs/operations/issue-<N>-codeant-fix.md`). See `docs/operations/agent-workflow.md`. Historical cubic logs: `issue-*-cubic-fix.md`. Historical CodeRabbit logs: `issue-*-coderabbit-fix.md`.
- Zoom-out architecture review after milestones.
- Prototyping for uncertain UX/system flows.
- Pre-commit and CI-style quality gates once code exists.
- **CodeAnt AI PR review** loops after GitHub PRs are open (current; replaces cubic, CodeRabbit, and Greptile).

Use these installed skills by name when relevant:

- `grill-with-docs`: challenge and improve docs before durable product or architecture decisions.
- `to-prd`: turn product ideas into goals, non-goals, user flows, acceptance criteria, and test plans.
- `to-issues`: break roadmap work into epics, stories, and vertical slices.
- `tdd`: write failing tests first for risky implementation logic.
- `diagnose`: reproduce, minimize, hypothesize, fix, and regression-test bugs.
- `zoom-out` or `improve-codebase-architecture`: review architecture boundaries after major changes.
- `prototype`: explore uncertain UI or system flows before production implementation.
- `setup-pre-commit`: add quality gates after runnable code exists.
- **CodeAnt review:** push to PR → wait for CodeAnt AI check + inline comments. See `docs/operations/agent-workflow.md`.

Diagram workflow:

- **Product UI** (mockups, user journeys): `docs/product-design/` — **v2:** `DESIGN-DECISIONS.md` + `mockups/learner-flow/` + `mockups/owner-flow/`; legacy gallery: `mockups/README.md`.
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
- Project board: https://github.com/users/Vinosaamaa/projects/1 (Education MVP backend #1–#24)
- **Production Frontend project:** https://github.com/users/Vinosaamaa/projects/3 (#30, #47–#59, milestone 3)
- **Public Launch project:** https://github.com/users/Vinosaamaa/projects/5 (#82–#104, milestone 5) — linked on [repo Projects tab](https://github.com/Vinosaamaa/chanter/projects)
- **Workable Product project:** https://github.com/users/Vinosaamaa/projects/4 (#30–#32, #60–#63, milestone 4) — **complete**
- ~~Social Hub project #2~~ — closed; #31–#32 live on project #4 only
- **#11 Monorepo bootstrap — CLOSED** (merged PR #25)
- **#12 Create A Study Server — CLOSED** (merged PR #26)
- **#13 Create Course, Cohort, And Enroll Learner — CLOSED** (merged PR #28)
- **#14 Join A Voice Channel — CLOSED** (merged PR #29)
- **#15 Send Friend Request And Direct Message — CLOSED** (merged PR #33)
- **#48 Bootstrap Production Frontend Foundation** — https://github.com/Vinosaamaa/chanter/issues/48
- **Cross-cutting:** [#30 Wire Auth Service Principal](https://github.com/Vinosaamaa/chanter/issues/30) — with #49
- **Workable Product:** [#31](https://github.com/Vinosaamaa/chanter/issues/31) Friends Hub, [#32](https://github.com/Vinosaamaa/chanter/issues/32) DM voice — project #4 only (not before #51)

Architecture for social UX: `docs/architecture/social-hub-and-dm-voice.md`

Implementation on `main`:

- `backend/` — gateway (:8080), auth (:8081), community (:8082), message (:8083), `common`
- `frontend/` — Vite vertical-slice demo (Study Server, voice, Friends/DM, support questions); target UI in `docs/product-design/mockups/`
- `infra/docker-compose.yml` — local Postgres, Redis, Redpanda, MinIO
- CI: backend `mvn verify`, frontend lint + build

Pending:

- Branch protection on `main` (optional, after owner enables)

After UI v2 shell (**#116**), resume Public Launch on [project #5](https://github.com/users/Vinosaamaa/projects/5):

1. **#86** Product stack reliability — **merged** (PR #105).
2. **#87** mockup gap audit — owner sign-off 2026-07-09.
3. **#116–#128** UI v2 course-first shell (**merged** in PR #130).
4. **#94–#100** Real AI / RAG / MCP.
5. **#101–#104** Launch readiness.

See [`agent-workflow.md`](docs/operations/agent-workflow.md) § Phase 4.

Target UX mockups: `docs/product-design/DESIGN-DECISIONS.md`. Social architecture: `docs/architecture/social-hub-and-dm-voice.md`.

Recent operations documentation artifacts:

- `docs/operations/issue-12-change-log.md`: change-by-change implementation log with representative code snippets.
- `docs/operations/issue-12-debug-log.md`: local browser 502/403 debug log and final verification notes.
- `docs/operations/issue-11-greptile-fix.md`: Greptile review feedback and fixes for PR #25.
- `docs/operations/issue-12-greptile-fix.md`: Greptile review feedback and fixes for PR #26.
- `docs/operations/issue-13-greptile-fix.md`: Greptile review feedback and fixes for PR #28.
- `docs/operations/issue-14-change-log.md`: issue #14 voice presence implementation log with representative snippets.
- `docs/operations/issue-14-debug-log.md`: issue #14 local Maven/dev-server debug log.
- `docs/operations/issue-51-change-log.md`: realtime-service bootstrap and live course channel chat.
- `docs/operations/issue-52-change-log.md`: production `#questions` UX and AI context panel (#52, PR #69).
- `docs/operations/issue-52-coderabbit-fix.md`: CodeRabbit review passes for PR #69.

## New Chat Startup Prompt

Use this prompt after reloading Cursor or starting a new chat:

```text
Read HANDOFF.md, CONTEXT.md, and docs/operations/agent-workflow.md.

Backend MVP #11–#24, Production Frontend #47–#59, and Workable Product #60–#63 are merged on main.
UI redesign #114 is closed. UI v2 #116–#128 is implemented locally on feature/116-v2-app-shell with issue-scoped commits and change logs; it has not been pushed.

Product UI: docs/product-design/DESIGN-DECISIONS.md + mockups/learner-flow/ and owner-flow/
Breakdown: docs/issues/ui-v2-issue-breakdown.md
Next: inspect the final diff, ask before pushing, open one PR for #116–#128, complete the CodeAnt review loop, and leave the merge to the owner.

Repo: https://github.com/Vinosaamaa/chanter
Epic: https://github.com/Vinosaamaa/chanter/issues/115
Slices: #116–#128 per docs/issues/ui-v2-issue-breakdown.md
Project: https://github.com/users/Vinosaamaa/projects/5
Demo: docs/operations/workable-product-demo.md
```

## Notes For Future Agent

- **Agent workflow:** read [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md) before picking work; follow the active project board order. Agents may merge after CI and CodeAnt gates; never push directly to `main`.
- **Product showcase:** `docs/product-design/` has target UI mockups, user-journey diagram, `vision.md`, and **`visibility-and-social-model.md`** (global friends vs enrollment-scoped course sidebar). **Implement against v2:** `DESIGN-DECISIONS.md`. Use it when implementing frontend routes or explaining scope to stakeholders. `frontend/src/App.tsx` is an API demo, not the final shell.
- **Engineering diagrams:** `docs/diagrams/` is for service architecture — do not put product mockups there.
- **One GitHub issue → one branch → one PR → merge only after user approval.** Never push directly to `main` for any change, including docs. Use `make setup-git-hooks` to block accidental local pushes.
- Do not push after edits or commits unless the user explicitly approves the push as a separate action at push time.
- **TDD:** backend #12–#24 used TDD for domain behavior; production frontend **#47–#55** test-last; **#56+** vertical-slice TDD per `agent-workflow.md`.
- For every non-trivial slice, create or update an issue-scoped change log in `docs/operations/` before final handoff.
- For every meaningful debugging incident, create or update an issue-scoped debug log in `docs/operations/` before final handoff.
- For every **CodeAnt** suggestion that is fixed or explicitly deferred, create or update `docs/operations/issue-<number>-codeant-fix.md` (see `docs/operations/agent-workflow.md`). Historical cubic logs remain under `issue-*-cubic-fix.md`. Historical CodeRabbit logs remain under `issue-*-coderabbit-fix.md`.
- Keep explanations beginner-friendly but production-oriented.
- Preserve and update the docs when architecture or process decisions change.
- Ask before creating remote repositories, pushing code, creating tickets, or installing third-party integrations.
- Treat `plan.md` and `System Design.md` as living design documents.
