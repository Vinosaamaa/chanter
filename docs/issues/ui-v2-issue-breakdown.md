# Chanter UI v2 Issue Breakdown

> **Goal:** Rebuild the **production frontend** to match the **course-first shell** mockups and [`DESIGN-DECISIONS.md`](../product-design/DESIGN-DECISIONS.md). Design work ([#114](https://github.com/Vinosaamaa/chanter/issues/114)) is complete; this phase is **implementation**.
>
> **Supersedes for layout:** [Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3) **#48–#59** (legacy Discord-style channel tree). Backend APIs and behaviors from those slices **remain** — reskin, re-route, and restructure the UI.
>
> **Public Launch UI polish** ([#88](https://github.com/Vinosaamaa/chanter/issues/88)–[#93](https://github.com/Vinosaamaa/chanter/issues/93)) is **paused** until **#116** (v2 shell foundation) merges — otherwise polish lands on UI that will be torn down.

## Agent read order (mandatory for every UI slice)

1. [`DESIGN-DECISIONS.md`](../product-design/DESIGN-DECISIONS.md) — canonical decisions
2. [`specs/layout-rules.md`](../product-design/specs/layout-rules.md) — chrome, active states, browser framing
3. **Issue-linked PNG(s)** in [`mockups/learner-flow/`](../product-design/mockups/learner-flow/) or [`mockups/owner-flow/`](../product-design/mockups/owner-flow/)
4. [`visibility-and-social-model.md`](../product-design/visibility-and-social-model.md) — enrollment-scoped sidebar vs global Friends
5. Relevant component specs in [`specs/`](../product-design/specs/)

**Visual parity checklist (every slice):**

- [ ] Layout matches linked mockup(s) — sidebar, top bar (right pane only), tabs, main content
- [ ] Passes `layout-rules.md` (complete top bar, profile in sidebar bottom, ⌘F search hint, browser chrome)
- [ ] Route-scoped search placeholder per `DESIGN-DECISIONS.md` §2.3
- [ ] No new UI from retired flat PNGs in `mockups/` root — use `learner-flow/` and `owner-flow/` only
- [ ] Existing merged behaviors (#51 chat, #31 friends, #55 instructor tools, etc.) still work after reskin

## GitHub milestone

**[UI v2 — Course-first shell](https://github.com/Vinosaamaa/chanter/milestone/7)**

## GitHub project

**[Public Launch](https://github.com/users/Vinosaamaa/projects/5)** — UI v2 block is **P0 above** legacy Public Launch UI polish (#88–#93).

**Board order = implementation order** (serial — one issue per branch). Full table: [`agent-workflow.md`](../operations/agent-workflow.md) § Phase 2b.

## Epic

| # | Title |
|---|-------|
| [115](https://github.com/Vinosaamaa/chanter/issues/115) | Epic: Implement course-first shell (UI v2) |

## Vertical slices

| Order | # | Title | Type | Blocked by | Mockup(s) |
|------:|---|-------|------|------------|-----------|
| 1 | [116](https://github.com/Vinosaamaa/chanter/issues/116) | Slice: v2 app shell foundation | AFK | — | `journey-3-home.png`, [`specs/sidebar-spec.png`](../product-design/specs/sidebar-spec.png), [`specs/topbar-tabs-spec.png`](../product-design/specs/topbar-tabs-spec.png) |
| 2 | [117](https://github.com/Vinosaamaa/chanter/issues/117) | Slice: Auth and onboarding v2 | AFK | #116 | `journey-1-signup-invite.png`, `journey-2-welcome-joined.png`, `owner-o1-create-study-server-wizard.png` |
| 3 | [118](https://github.com/Vinosaamaa/chanter/issues/118) | Slice: Home, Inbox, and Calendar v2 | AFK | #116 | `journey-3-home.png`, `journey-6-inbox.png`, `journey-8-calendar.png` |
| 4 | [119](https://github.com/Vinosaamaa/chanter/issues/119) | Slice: Course workspace — Overview and Chat | AFK | #116 | `journey-4a-course-overview.png`, `journey-4b-course-chat.png` |
| 5 | [120](https://github.com/Vinosaamaa/chanter/issues/120) | Slice: Course workspace — Questions and AI panel | AFK | #119 | `journey-4c-course-questions.png`, `owner-o5-questions-faq-ta-queue.png` |
| 6 | [121](https://github.com/Vinosaamaa/chanter/issues/121) | Slice: Course workspace — Resources | AFK | #119 | `journey-4d-course-resources.png`, `owner-o4-resources-upload-ai-install.png` |
| 7 | [122](https://github.com/Vinosaamaa/chanter/issues/122) | Slice: Course workspace — Office Hours | AFK | #119 | `journey-4e-course-office-hours.png`, `owner-o6-office-hours-schedule-host.png`, `owner-o6-office-hours-schedule-edit.png` |
| 8 | [123](https://github.com/Vinosaamaa/chanter/issues/123) | Slice: Course workspace — People | AFK | #119 | `journey-4f-course-people.png`, `owner-o7a-people-roster-default.png`, `owner-o7b-people-add-ta-popover.png`, `owner-o7c-people-bulk-assign-ta.png` |
| 9 | [124](https://github.com/Vinosaamaa/chanter/issues/124) | Slice: Community hub — five tabs | AFK | #116 | `journey-5a`–`5e` (+ detail/apply modals) — see [`learner-flow/README.md`](../product-design/mockups/learner-flow/README.md) |
| 10 | [125](https://github.com/Vinosaamaa/chanter/issues/125) | Slice: Teaching dashboard and Settings billing | AFK | #116 | `owner-o8-teaching-dashboard.png`, `owner-o9-settings-plan-billing.png` |
| 11 | [126](https://github.com/Vinosaamaa/chanter/issues/126) | Slice: Friends and DM v2 chrome | AFK | #116 | `journey-7-friends-dm.png` |
| 12 | [127](https://github.com/Vinosaamaa/chanter/issues/127) | Slice: Owner create course/cohort and community events | AFK | #124 | `owner-o2-create-course-cohort.png`, `owner-o10a-community-events-owner.png`, `owner-o10b-create-community-event.png` |
| 13 | [128](https://github.com/Vinosaamaa/chanter/issues/128) | Slice: Landing and marketing v2 | AFK | #117 | Legacy `landing-page.png` or refresh to match v2 brand |

## Recommended implementation order

```
#116 → #117 → #118 → #119 → #120 → #121 → #122 → #123 → #124 → #125 → #126 → #127 → #128
```

**One issue → one branch → one PR.** Do not parallelize on this repo.

After **#116**, resume deferred Public Launch UI polish only where it still applies (or fold into the relevant v2 slice).

## Mockup coverage map

| Mockup | Slice |
|--------|-------|
| `journey-1-signup-invite.png` | #117 |
| `journey-2-welcome-joined.png` | #117 |
| `journey-3-home.png` | #116, #118 |
| `journey-4a`–`4f` | #119–#123 |
| `journey-5a`–`5e` (+ modals) | #124, #127 |
| `journey-6-inbox.png` | #118 |
| `journey-7-friends-dm.png` | #126 |
| `journey-8-calendar.png` | #118 |
| `owner-o1` | #117 |
| `owner-o2` | #127 |
| `owner-o4` | #121 |
| `owner-o5` | #120 |
| `owner-o6a/b` | #122 |
| `owner-o7a/b/c` | #123 |
| `owner-o8` | #125 |
| `owner-o9` | #125 |
| `owner-o10a/b` | #127 |

## What legacy Production Frontend delivered (keep behavior, replace chrome)

| Capability | Legacy slice | UI v2 slice |
|------------|--------------|-------------|
| App shell + nav | #50 | **#116** |
| Auth + protected routes | #49 | **#117** |
| Live course chat | #51 | **#119** |
| Questions + AI panel | #52 | **#120** |
| Resources panel | #53 | **#121** |
| Office hours / TA queue | #54 | **#122**, **#120** |
| Instructor dashboard + billing | #55 | **#125** |
| Onboarding / enrollment | #56 | **#117**, **#124**, **#127** |
| Route-scoped search | #57 | **#116** + per-route in each slice |
| Friends + DM | #31 | **#126** |
| Landing page | #59 | **#128** |

## Labels

`epic`, `story`, `ready-for-agent`, `frontend`, `education`

## Related docs

- [`agent-workflow.md`](../operations/agent-workflow.md) — **mandatory** issue order (Phase 2b)
- [`production-frontend-issue-breakdown.md`](production-frontend-issue-breakdown.md) — historical (#47–#59)
- [`public-launch-issue-breakdown.md`](public-launch-issue-breakdown.md) — AI + launch after UI v2 shell
- [`DESIGN-DECISIONS.md`](../product-design/DESIGN-DECISIONS.md) — design parent of #114
