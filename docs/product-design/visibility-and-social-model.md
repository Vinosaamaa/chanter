# Visibility And Social Model

Date: 2026-07-12 (v2 UI update)  
Status: product decision (Education MVP)  
Canonical showcase index: [README.md](README.md)  
**v2 UI shell:** [`DESIGN-DECISIONS.md`](DESIGN-DECISIONS.md)

This document explains how **global friends** coexist with **enrollment-scoped course UI** so learners see a coherent sidebar without every course on the server, while DMs remain platform-wide.

## Two layers (do not merge them)

| Layer | Question it answers | Scope | Examples |
|---|---|---|---|
| **Social** | Who can I message privately? | **Global** (platform account) | Friends list, DMs, friend voice (#31–#32) |
| **Learning** | What class spaces can I use? | **Enrollment + role** | Course workspace tabs (Questions, Resources, People), TA queue, Office Hours |

- **Support Questions, TA Queue, and Office Hours** stay on the learning layer — never in DMs.
- **Friends Hub** stays on the social layer — separate from course channels.

Global friends are viable for education **because** course work is tightly scoped. Social opt-in (friend request → accept) is separate from accidentally browsing another cohort's Questions tab.

## User flow (global account)

```
Sign in (global account)
  → Friends Hub (optional) — all accepted friends, DMs, presence
  → Home dashboard (cross-server)
  → Sidebar: server groups + MY courses only
  → Click course → course workspace (tabs, default cohort)
  → Click server group → community hub (Announcements, Lounge, …)
```

**Enrollment (MVP):** learners join a cohort when an instructor enrolls them (or via invite / purchase later). They do not see or enter courses they are not enrolled in.

## Personalized sidebar (v2 — per user, same Study Server)

Two learners in the **same Study Server** but **different courses** see **different course rows**. That is intentional.

**v2 sidebar structure:**

```text
Chanter
├── Home
├── [Teaching]              ← instructor/owner only
├── Inbox
├── Calendar
├── Friends
├── SPRING BOOTCAMP HUB ▾
│   ├── CS 101 — Intro to CS
│   ├── MATH 201 — …
│   └── …
├── + Join or create
└── [Profile row]
```

| Role | Server group visible? | Course rows in sidebar |
|---|---|---|
| **Learner** | Yes (if Study Server member) | **Only courses** where they have Cohort Enrollment |
| **Instructor** | Yes | **Courses they instruct** (+ courses they learn in, if any) |
| **TA** | Yes | **Cohorts they support** (shown under parent course) |
| **Owner** | Yes | All courses + create/manage via Discover courses |

**Course workspace** (not sidebar channels): `Overview · Chat · Questions · Resources · Office Hours · People` — cohort-scoped except Resources (course-scoped by default).

**Community hub** (server-wide, separate from course workspace): `Announcements · Lounge · Events · Discover courses · Members`.

Example learner sidebar:

```text
Spring Bootcamp Hub ▾
├── CS 101 — Intro to CS        ← click → course workspace
├── MATH 201 — Calculus II
(Data Science — not listed; not enrolled)
```

Example instructor sidebar adds **Teaching** nav and every course they teach; owners see the full catalog and **Settings** (billing, members).

## Where users can still “see” each other

| Situation | Same course workspace? | Community hub (Lounge, etc.)? | Friends / DMs? |
|---|---|---|---|
| Same server, **same course/cohort** | Yes | Yes | Yes (if friends) |
| Same server, **different courses** | **No** | **Often yes** (Lounge, Events) | Yes (if friends) |
| **Different Study Servers** | No | No (unless member of both) | Yes (if friends) |

Course isolation is **not** the same as total invisibility: classmates in different courses may still meet in **Lounge** or hub Events. Learning surfaces stay isolated.

## Global friends — rules

**Decision:** keep a **single global friend graph** per user pair (as implemented in #15).

**Guardrail (recommended for #31):** allow friend requests only between users who **share at least one Study Server membership** (co-membership). This prevents messaging arbitrary strangers on the platform while keeping one Friends list across servers.

| Action | Rule |
|---|---|
| Send friend request | Co-membership required (recommended); recipient accepts or declines |
| Direct message | Accepted friends only; block/report applies (#15) |
| Friend list UI | **Global** — all friends; optional badge for shared server/course context |
| Course member list | Enrollment-scoped — only people in that course/cohort (People tab) |

**UI hint (optional):** in Friends Hub, show context such as `Bootcamp Hub · Spring Boot` under a friend's name when you share membership — for clarity, not for permissions.

## Search and discovery

- **Search (v2 MVP):** **Route-scoped** top-bar field — scope follows navigation (Home → my courses, Inbox → inbox, Friends → friends/DMs, course tab → that course/tab). **⌘F** focuses only; does not change scope (unlike Discord server-wide + ⌘F channel lock). Optional **scope preview hints** in course workspace (e.g. `in:#general`). **No** global ⌘K overlay or cross-server search in MVP.
- **Discover courses:** hub tab (`5e`) — 2×2 grid of courses **in this study server only**; filters All / Enrolled / Open / Opening soon. Not a global marketplace route.
- **Course catalog on home:** learners see **my courses**; owners/instructors see management views via Teaching dashboard + Discover courses; a public storefront is a later phase.

## Implementation notes

| Area | Service | Today |
|---|---|---|
| Enrollment, course/channel access | `community-service` | #13 — API enforces boundaries |
| Friend requests, DMs, blocks | `message-service` | #15 — global graph (no `study_server_id` yet) |
| Friends Hub UX, presence | `realtime-service` + frontend | #31 — should apply co-membership check |
| Sidebar “my courses” | frontend + `community-service` | v2 UI — list courses/cohorts for current user only |

Backend permission checks remain authoritative; the sidebar is a reflection of enrollment, not the source of truth.

## Related docs

- [Product vision](vision.md) — screens and journeys
- [**DESIGN-DECISIONS.md**](DESIGN-DECISIONS.md) — v2 shell, tabs, roles
- [Education MVP PRD](../product/education-mvp-prd.md) — user stories and implementation decisions
- [Social hub architecture](../architecture/social-hub-and-dm-voice.md) — Friends Hub and DM voice
- [CONTEXT.md](../../CONTEXT.md) — glossary (Enrollment, Friends Hub, Study Server Member)
