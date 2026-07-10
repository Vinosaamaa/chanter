# Chanter Public Launch Issue Breakdown

> **Goal:** Move from **Workable Product** (local full-stack demo) to a **publicly launchable** education product: mockup-faithful UI, real LLM/RAG AI (resume-grade orchestration), and staging/production readiness.
>
> **Depends on:** [Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4) (**#60–#63**, **#31–#32**) and [Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3) (**#48–#59**).

## GitHub milestone

**[Public Launch](https://github.com/Vinosaamaa/chanter/milestone/5)**

## GitHub project

**[Public Launch](https://github.com/users/Vinosaamaa/projects/5)**

**Board order = implementation order:** #86 → #87 → #88–#93 (UI) → #94–#100 (AI) → #101–#104 (launch).

## Parent epic

| # | Title |
|---|-------|
| [82](https://github.com/Vinosaamaa/chanter/issues/82) | Epic: Public Launch |

## Child epics

| # | Title | Phase |
|---|-------|-------|
| [83](https://github.com/Vinosaamaa/chanter/issues/83) | Epic: MVP UI Polish & Mockup Fidelity | **1 — UI first** |
| [84](https://github.com/Vinosaamaa/chanter/issues/84) | Epic: Real AI Study Assistant & Agent Runtime | **2 — LLM / RAG / MCP** |
| [85](https://github.com/Vinosaamaa/chanter/issues/85) | Epic: Launch Readiness & Reliability | **3 — ship** |

## Phase 0 — Reliability (start immediately)

| # | Title | Type | Blocked by |
|---|---|---|---|
| [86](https://github.com/Vinosaamaa/chanter/issues/86) | Slice: Product stack reliability hotfixes | AFK | — |

## Phase 1 — UI polish (mockup fidelity)

| # | Title | Type | Blocked by | Mockup(s) |
|---|---|---|---|---|
| [87](https://github.com/Vinosaamaa/chanter/issues/87) | Slice: Mockup gap audit and UI polish backlog | HITL | — | all 19 |
| [88](https://github.com/Vinosaamaa/chanter/issues/88) | Slice: App shell, sidebar, and channel navigation polish | AFK | #87 | `app-shell.png` |
| [89](https://github.com/Vinosaamaa/chanter/issues/89) | Slice: Study Server home, create server, and enrollment polish | AFK | #87 | `create-study-server.png`, `study-server-home.png`, `cohort-enrollment.png` |
| [90](https://github.com/Vinosaamaa/chanter/issues/90) | Slice: Friend requests inbox and Friends Hub polish | AFK | #87 | `friend-requests.png`, `friends-hub-dm.png` |
| [91](https://github.com/Vinosaamaa/chanter/issues/91) | Slice: AI Study Assistant install flow (production UI) | AFK | #87 | `ai-assistant-install.png` |
| [92](https://github.com/Vinosaamaa/chanter/issues/92) | Slice: Support operations and instructor panels polish | AFK | #87 | `ta-queue.png`, `office-hours-voice.png`, `faq-approval.png`, `channel-summary.png`, `instructor-dashboard.png` |
| [93](https://github.com/Vinosaamaa/chanter/issues/93) | Slice: Study Server management (list, delete, empty states) | AFK | #87 | `study-server-home.png` |

## Phase 2 — Real AI (LLM orchestration, embeddings, MCP)

| # | Title | Type | Blocked by |
|---|---|---|---|
| [94](https://github.com/Vinosaamaa/chanter/issues/94) | Slice: Resource ingestion and chunking for AI-approved materials | AFK | — |
| [95](https://github.com/Vinosaamaa/chanter/issues/95) | Slice: Embedding pipeline and vector retrieval store | AFK | #94 |
| [96](https://github.com/Vinosaamaa/chanter/issues/96) | Slice: RAG grounding engine (replace keyword-only) | AFK | #95 |
| [97](https://github.com/Vinosaamaa/chanter/issues/97) | Slice: LLM provider adapters (Ollama local + OpenAI-compatible) | AFK | — (parallel) |
| [98](https://github.com/Vinosaamaa/chanter/issues/98) | Slice: Agent runtime orchestration (prompt, streaming, quotas) | AFK | #96, #97 |
| [99](https://github.com/Vinosaamaa/chanter/issues/99) | Slice: MCP tool bridge for course-grounded assistant tools | AFK | #98 |
| [100](https://github.com/Vinosaamaa/chanter/issues/100) | Slice: Streaming AI answer UX, citations, and audit trail | AFK | #98 |

## Phase 3 — Launch readiness

| # | Title | Type | Blocked by |
|---|---|---|---|
| [101](https://github.com/Vinosaamaa/chanter/issues/101) | Slice: Staging deployment with HTTPS | AFK | #86 |
| [102](https://github.com/Vinosaamaa/chanter/issues/102) | Slice: Production auth (email verification, password reset) | AFK | #101 |
| [103](https://github.com/Vinosaamaa/chanter/issues/103) | Slice: Playwright E2E critical paths | AFK | #90, #91 |
| [104](https://github.com/Vinosaamaa/chanter/issues/104) | Slice: Public beta launch checklist and docs | AFK | #101, #103 |

## Recommended order

```
#86 reliability (start now — uncommitted local fixes map here)
#87 UI audit (HITL sign-off)
#88 → #93 UI slices (parallel after audit)
#94 → #100 AI stack
#101 → #104 launch
```

## Resume / portfolio angle (Phase 2)

Issues **#94–#99** are the “LLM orchestration, embeddings, MCP” story: chunking → vectors → RAG → provider adapters → runtime → MCP tools → streaming UI (**#100**).

## Related docs

- [`docs/product-design/mockups/README.md`](../product-design/mockups/README.md)
- [`docs/operations/public-launch-ui-gap-audit.md`](../operations/public-launch-ui-gap-audit.md) — **#87** gap audit output
- [`docs/operations/ai-study-assistant.md`](../operations/ai-study-assistant.md)
- [`plan.md`](../../plan.md)
- [`CONTEXT.md`](../../CONTEXT.md)
