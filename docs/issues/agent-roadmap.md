# Chanter Agent Roadmap — Issue Order

**Last updated:** 2026-06-24  
**Rule:** One GitHub issue → one branch → one PR → CodeRabbit loop → merge → next issue **in order below**. Do not skip ahead on the active project board.

## Phase status

| Phase | GitHub milestone | Project board | Status |
|-------|------------------|---------------|--------|
| Backend MVP | [Education MVP](https://github.com/Vinosaamaa/chanter/milestone/1) | [#1](https://github.com/users/Vinosaamaa/projects/1) | **Done** (#11–#24 merged) |
| Production Frontend | [Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3) | [#3](https://github.com/users/Vinosaamaa/projects/3) | **Active — start here** |
| Workable Product | [Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4) | [#4](https://github.com/users/Vinosaamaa/projects/4) | After #51 on project #3 |

Legacy **Social Hub project #2** is **closed**. #31–#32 are tracked only on **project #4**.

---

## Phase 2: Production Frontend (project #3)

**Goal:** Mockup-aligned UI, auth screens, app shell, live **text** chat.  
**Breakdown:** [`production-frontend-issue-breakdown.md`](production-frontend-issue-breakdown.md)  
**PRD:** [`education-mvp-prd.md`](../product/education-mvp-prd.md) § Phase 2

**Board order (top = do first):**

| Order | Issue | Title |
|------:|-------|-------|
| 1 | [#47](https://github.com/Vinosaamaa/chanter/issues/47) | Epic: Product Shell And Production Frontend |
| 2 | [**#48**](https://github.com/Vinosaamaa/chanter/issues/48) | **← START HERE** Bootstrap Production Frontend Foundation |
| 3 | [#49](https://github.com/Vinosaamaa/chanter/issues/49) | Auth UI And Protected App Routes |
| 4 | [#30](https://github.com/Vinosaamaa/chanter/issues/30) | Wire Auth Service Principal (implement with #49) |
| 5 | [#50](https://github.com/Vinosaamaa/chanter/issues/50) | Study Server App Shell And Navigation |
| 6 | [#51](https://github.com/Vinosaamaa/chanter/issues/51) | Bootstrap Realtime Service And Live Course Channel Chat |
| 7 | [#52](https://github.com/Vinosaamaa/chanter/issues/52) | Production `#questions` UX With AI Context Panel |
| 8 | [#53](https://github.com/Vinosaamaa/chanter/issues/53) | Production Course Resources Panel |
| 9 | [#54](https://github.com/Vinosaamaa/chanter/issues/54) | Production Support Operations UI |
| 10 | [#55](https://github.com/Vinosaamaa/chanter/issues/55) | Production Instructor Dashboard And SaaS Plan UI |
| 11 | [#56](https://github.com/Vinosaamaa/chanter/issues/56) | Production Onboarding And Enrollment Flows |
| 12 | [#57](https://github.com/Vinosaamaa/chanter/issues/57) | Global Search UI And Search Service Bootstrap |
| 13 | [#58](https://github.com/Vinosaamaa/chanter/issues/58) | Channel Summary UI For Course Channels |
| 14 | [#59](https://github.com/Vinosaamaa/chanter/issues/59) | Public Marketing Landing Page (optional polish) |

After **#50**, issues **#53–#56** may run in parallel if coordinated; default is still top-to-bottom on the board.

---

## Phase 3: Workable Product (project #4)

**Goal:** Clickable **full-stack local app** — voice in channels, live friends/DM, one-command dev stack.  
**Breakdown:** [`workable-product-issue-breakdown.md`](workable-product-issue-breakdown.md)  
**PRD:** [`education-mvp-prd.md`](../product/education-mvp-prd.md) § Phase 3

**Do not start project #4 until #51 (realtime text chat) is merged.**

**Board order (top = do first):**

| Order | Issue | Title |
|------:|-------|-------|
| 1 | [#60](https://github.com/Vinosaamaa/chanter/issues/60) | Epic: Workable Local Product (Full Stack) |
| 2 | [#30](https://github.com/Vinosaamaa/chanter/issues/30) | Auth (if not done in phase 2) |
| 3 | [#62](https://github.com/Vinosaamaa/chanter/issues/62) | One-Command Local Product Stack (may start early) |
| 4 | [#61](https://github.com/Vinosaamaa/chanter/issues/61) | Voice Channel WebRTC And LiveKit Local Stack |
| 5 | [#31](https://github.com/Vinosaamaa/chanter/issues/31) | Friends Hub And Live DM Conversation |
| 6 | [#32](https://github.com/Vinosaamaa/chanter/issues/32) | Direct Message Voice Call Between Friends |
| 7 | [#63](https://github.com/Vinosaamaa/chanter/issues/63) | Workable Product End-To-End Demo Path |

---

## Workable app checklist (definition of done for phase 3)

1. One command starts the full local stack (#62).
2. Sign in as two users (#30 + #49).
3. Live text in a shared Course Channel (#51).
4. Friend request → accept → live DM (#31).
5. Join Voice Channel with real audio (#61).
6. Optional: DM voice call (#32).

---

## Agent startup (copy-paste)

```text
Read HANDOFF.md, CONTEXT.md, and docs/issues/agent-roadmap.md.

Backend MVP #11–#24 is merged. Active work: Production Frontend project #3.
Start at issue #48 unless a higher-priority in-order issue is already in progress.

Product UI: docs/product-design/README.md
Workflow: docs/operations/coderabbit-review-workflow.md
```
