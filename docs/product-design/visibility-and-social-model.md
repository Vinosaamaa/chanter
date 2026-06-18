# Visibility And Social Model

Date: 2026-06-17  
Status: product decision (Education MVP)  
Canonical showcase index: [README.md](README.md)

This document explains how **global friends** coexist with **enrollment-scoped course UI** so learners see a coherent sidebar without every course on the server, while DMs remain platform-wide.

## Two layers (do not merge them)

| Layer | Question it answers | Scope | Examples |
|---|---|---|---|
| **Social** | Who can I message privately? | **Global** (platform account) | Friends list, DMs, friend voice (#31–#32) |
| **Learning** | What class spaces can I use? | **Enrollment + role** | `#questions`, `#resources`, TA queue, course resources |

- **Support Questions, TA Queue, and Office Hours** stay on the learning layer — never in DMs.
- **Friends Hub** stays on the social layer — separate from course channels.

Global friends are viable for education **because** course work is tightly scoped. Social opt-in (friend request → accept) is separate from accidentally browsing another cohort's `#questions`.

## User flow (global account)

```
Sign in (global account)
  → Friends Hub (optional) — all accepted friends, DMs, presence
  → Study Server picker
  → Enter one Study Server
  → Sidebar shows server channels + MY courses only
  → Open a course → that course's channels only
```

**Enrollment (MVP):** learners join a cohort when an instructor enrolls them (or via invite / purchase later). They do not see or enter courses they are not enrolled in.

## Personalized sidebar (per user, same Study Server)

Two learners in the **same Study Server** but **different courses** see **different course sections**. That is intentional.

| Role | Study Server channels | Course section in sidebar |
|---|---|---|
| **Learner** | Yes (if Study Server member) | **Only courses** where they have Cohort Enrollment |
| **Instructor** | Yes | **Courses they instruct** (+ courses they learn in, if any) |
| **TA** | Yes | **Cohorts they support** (grouped under parent course) |
| **Owner** | Yes | All courses + create/manage |

Example learner sidebar:

```text
Bootcamp Hub
├── STUDY SERVER
│   #announcements
│   #general
│   > study-room
├── MY COURSES
│   Spring Boot (March 2026 cohort)
│     #announcements
│     #questions
│     #resources
│   (Data Science — not listed; not enrolled)
```

Example instructor sidebar adds admin entries and every course they teach; owners see the full catalog.

## Where users can still “see” each other

| Situation | Same course channels? | Server-wide channels? | Friends / DMs? |
|---|---|---|---|
| Same server, **same course/cohort** | Yes | Yes | Yes (if friends) |
| Same server, **different courses** | **No** | **Often yes** (`#general`, voice) | Yes (if friends) |
| **Different Study Servers** | No | No (unless member of both) | Yes (if friends) |

Course isolation is **not** the same as total invisibility: classmates in different courses may still meet in `#general` or `> study-room`. Learning surfaces stay isolated.

## Global friends — rules

**Decision:** keep a **single global friend graph** per user pair (as implemented in #15).

**Guardrail (recommended for #31):** allow friend requests only between users who **share at least one Study Server membership** (co-membership). This prevents messaging arbitrary strangers on the platform while keeping one Friends list across servers.

| Action | Rule |
|---|---|
| Send friend request | Co-membership required (recommended); recipient accepts or declines |
| Direct message | Accepted friends only; block/report applies (#15) |
| Friend list UI | **Global** — all friends; optional badge for shared server/course context |
| Course member list | Enrollment-scoped — only people in that course/cohort |

**UI hint (optional):** in Friends Hub, show context such as `Bootcamp Hub · Spring Boot` under a friend's name when you share membership — for clarity, not for permissions.

## Search and discovery

- **Global search** (#17+): results filtered by **enrollment and role** — no leaking other cohorts' private messages or resources.
- **Course catalog** on server home: learners see **my courses**; owners/instructors see management views; a public storefront is a later phase.

## Implementation notes

| Area | Service | Today |
|---|---|---|
| Enrollment, course/channel access | `community-service` | #13 — API enforces boundaries |
| Friend requests, DMs, blocks | `message-service` | #15 — global graph (no `study_server_id` yet) |
| Friends Hub UX, presence | `realtime-service` + frontend | #31 — should apply co-membership check |
| Sidebar “my courses” | frontend + `community-service` | Target UI — list courses/cohorts for current user only |

Backend permission checks remain authoritative; the sidebar is a reflection of enrollment, not the source of truth.

## Related docs

- [Product vision](vision.md) — screens and journeys
- [Education MVP PRD](../product/education-mvp-prd.md) — user stories and implementation decisions
- [Social hub architecture](../architecture/social-hub-and-dm-voice.md) — Friends Hub and DM voice
- [CONTEXT.md](../../CONTEXT.md) — glossary (Enrollment, Friends Hub, Study Server Member)
