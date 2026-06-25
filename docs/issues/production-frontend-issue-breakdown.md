# Chanter Production Frontend Issue Breakdown

> **Context:** Education MVP backend vertical slices **#11–#24** are merged. APIs exist; `frontend/src/App.tsx` is still a vertical-slice demo. This breakdown tracks the **production UI phase** that matches [`docs/product-design/mockups/`](../product-design/mockups/README.md).
>
> **PRD addendum:** [`docs/product/education-mvp-prd.md`](../product/education-mvp-prd.md) — *Phase 2: Production Frontend* section.

## GitHub milestone

**[Production Frontend](https://github.com/Vinosaamaa/chanter/milestone/3)** — browser product shell, realtime chat UX, and mockup-aligned screens.

## GitHub project

**[Production Frontend](https://github.com/users/Vinosaamaa/projects/3)** — issues **#30**, **#47–#59**.

**Board order = implementation order** (top row first). Full table: [`agent-roadmap.md`](agent-roadmap.md) § Phase 2.

## Epic

| # | Title |
|---|---|
| [47](https://github.com/Vinosaamaa/chanter/issues/47) | Epic: Product Shell And Production Frontend |

## Vertical slices

| # | Title | Type | Start when unblocked | Mockup(s) |
|---|---|---|---|---|
| [48](https://github.com/Vinosaamaa/chanter/issues/48) | Slice: Bootstrap Production Frontend Foundation | AFK | Immediately | — |
| [49](https://github.com/Vinosaamaa/chanter/issues/49) | Slice: Auth UI And Protected App Routes | AFK | after #48; pair with [#30](https://github.com/Vinosaamaa/chanter/issues/30) | `sign-in-onboarding.png` |
| [50](https://github.com/Vinosaamaa/chanter/issues/50) | Slice: Study Server App Shell And Navigation | AFK | after #49 | `app-shell.png` |
| [51](https://github.com/Vinosaamaa/chanter/issues/51) | Slice: Bootstrap Realtime Service And Live Course Channel Chat | AFK | after #50 | `app-shell.png` |
| [52](https://github.com/Vinosaamaa/chanter/issues/52) | Slice: Production #questions UX With AI Context Panel | AFK | after #51 | `ai-support-question.png` |
| [53](https://github.com/Vinosaamaa/chanter/issues/53) | Slice: Production Course Resources Panel | AFK | after #50 | `course-resources.png` |
| [54](https://github.com/Vinosaamaa/chanter/issues/54) | Slice: Production Support Operations UI | AFK | after #50 | `ta-queue.png`, `office-hours-voice.png`, `faq-approval.png` |
| [55](https://github.com/Vinosaamaa/chanter/issues/55) | Slice: Production Instructor Dashboard And SaaS Plan UI | AFK | after #50 | `instructor-dashboard.png`, `saas-billing.png` |
| [56](https://github.com/Vinosaamaa/chanter/issues/56) | Slice: Production Onboarding And Enrollment Flows | AFK | after #49 | `create-study-server.png`, `study-server-home.png`, `cohort-enrollment.png` |
| [57](https://github.com/Vinosaamaa/chanter/issues/57) | Slice: Global Search UI And Search Service Bootstrap | AFK | after #50 | `global-search.png` |
| [58](https://github.com/Vinosaamaa/chanter/issues/58) | Slice: Channel Summary UI For Course Channels | AFK | after #52 | `channel-summary.png` |
| [59](https://github.com/Vinosaamaa/chanter/issues/59) | Slice: Public Marketing Landing Page | AFK | after #49 (optional polish) | `landing-page.png` |

## Related existing issues

| # | Title | Milestone | Notes |
|---|---|---|---|
| [30](https://github.com/Vinosaamaa/chanter/issues/30) | Wire Auth Service Principal Into Protected Endpoints | Education MVP | Backend auth; **#49** delivers sign-in UI. Implement together or back-to-back. |
| [31](https://github.com/Vinosaamaa/chanter/issues/31) | Build Discord-Like Friends Hub And Live DM Conversation | [Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4) | [project #4](https://github.com/users/Vinosaamaa/projects/4) — not on legacy project #2 |
| [32](https://github.com/Vinosaamaa/chanter/issues/32) | Direct Message Voice Call Between Friends | [Workable Product](https://github.com/Vinosaamaa/chanter/milestone/4) | [project #4](https://github.com/users/Vinosaamaa/projects/4) |

## Recommended implementation order

```
#48 → #49 + #30 → #50 → #51 → #52
                  ↘ #53, #54, #55, #56 (parallel after #50)
                  → #57, #58
#59 anytime after #49 (marketing polish)
```

**#31–#32** (Friends Hub, DM voice) are **not** part of this milestone — they ship on [Workable Product project #4](https://github.com/users/Vinosaamaa/projects/4) after **#51** merges. See [`agent-roadmap.md`](agent-roadmap.md).

## Mock coverage map (19 screens)

| Mockup | Slice |
|---|---|
| `landing-page.png` | #59 |
| `sign-in-onboarding.png` | #49 + #30 |
| `create-study-server.png` | #56 |
| `study-server-home.png` | #56 |
| `app-shell.png` | #50, #51 |
| `global-search.png` | #57 |
| `ai-support-question.png` | #52 |
| `course-resources.png` | #53 |
| `ta-queue.png` | #54 |
| `office-hours-voice.png` | #54 |
| `faq-approval.png` | #54 |
| `channel-summary.png` | #58 |
| `instructor-dashboard.png` | #55 |
| `cohort-enrollment.png` | #56 |
| `ai-assistant-install.png` | Covered in #52 context panel / existing #18 API — polish in #52 or follow-up |
| `saas-billing.png` | #55 |
| `friends-hub-dm.png` | #31 |
| `friend-requests.png` | #31 |
| `course-storefront.png` | **Post-MVP commerce** — future PRD |

## Labels

Use existing labels: `epic`, `story`, `ready-for-agent`, `frontend`, `education`, `realtime` (for #51), `security` (for #30/#49), `analytics` (for #55).

## Related docs

- [`agent-roadmap.md`](agent-roadmap.md) — **mandatory issue order** (project #3)
- [`workable-product-issue-breakdown.md`](workable-product-issue-breakdown.md) — voice, friends, E2E (#60–#63, #31–#32) after #51
