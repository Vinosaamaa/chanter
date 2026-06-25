# Chanter Product Design Showcase

Start here if you want the **product story** — what Chanter is, what it looks like, and how users move through it — without reading the whole codebase.

**Referenced from:** [`README.md`](../../README.md), [`HANDOFF.md`](../../HANDOFF.md), [`plan.md`](../../plan.md), [`System Design.md`](../../System Design.md), [`CONTEXT.md`](../../CONTEXT.md), [`frontend/README.md`](../../frontend/README.md).

**Positioning:**

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

## Quick tour

| Asset | What it shows |
|---|---|
| [Product vision](vision.md) | Narrative walkthrough with user stories |
| [Visibility and social model](visibility-and-social-model.md) | Global friends + enrollment-scoped course sidebar |
| [**Mockup gallery**](mockups/README.md) | **19 UI concept screens** — full visual catalog |
| [User journey diagram](diagrams/user-journey.drawio.png) | Click-flow map for MVP screens and later phases |
| [Interactive screen tour](interactive/) | Cursor canvas — click through screens in the IDE |

## Mockups at a glance (19 screens)

| Category | Screens |
|---|---|
| **Onboarding** | [landing](mockups/landing-page.png) · [sign-in](mockups/sign-in-onboarding.png) · [create server](mockups/create-study-server.png) · [server home](mockups/study-server-home.png) |
| **Core app** | [app shell](mockups/app-shell.png) · [global search](mockups/global-search.png) |
| **Course channels** | [#questions + AI](mockups/ai-support-question.png) · [#resources](mockups/course-resources.png) |
| **Support ops** | [TA queue](mockups/ta-queue.png) · [office hours](mockups/office-hours-voice.png) · [FAQ approval](mockups/faq-approval.png) · [channel summary](mockups/channel-summary.png) |
| **Instructor / admin** | [dashboard](mockups/instructor-dashboard.png) · [enrollment](mockups/cohort-enrollment.png) · [AI install](mockups/ai-assistant-install.png) · [billing](mockups/saas-billing.png) |
| **Social** | [friends + DM](mockups/friends-hub-dm.png) · [friend requests](mockups/friend-requests.png) |
| **Later** | [course storefront](mockups/course-storefront.png) |

## Folder layout

```
docs/product-design/
├── README.md                 ← you are here
├── vision.md                 ← screens, user stories, built vs planned
├── visibility-and-social-model.md  ← global friends vs my-courses sidebar
├── mockups/                  ← 19 UI concept PNGs + gallery README
├── diagrams/                 ← editable draw.io + PNG exports
└── interactive/              ← Cursor canvas source (optional live tour)
```

## Related product docs

| Document | Purpose |
|---|---|
| [Education MVP PRD](../product/education-mvp-prd.md) | Problem, solution, user stories, implementation decisions |
| [Issue breakdown](../issues/education-mvp-issue-breakdown.md) | Backend epics and slices (#11–#24) — **done** |
| [Production frontend breakdown](../issues/production-frontend-issue-breakdown.md) | UI phase (#47–#59) — **active** |
| [Workable product breakdown](../issues/workable-product-issue-breakdown.md) | Full-stack local app (#60–#63, #31–#32) |
| [**Agent roadmap**](../issues/agent-roadmap.md) | **Mandatory issue order** for agents (project boards #3, #4) |
| [Product glossary](../../CONTEXT.md) | Canonical domain language (Study Server, Cohort, etc.) |
| [Product strategy session](../sessions/2026-06-16-product-strategy-grill-session.md) | How the education wedge was chosen |
| [Social hub architecture](../architecture/social-hub-and-dm-voice.md) | Friends hub and DM voice (#31–#32) on project #4 |

## Related architecture (engineering)

These live under `docs/diagrams/` because `plan.md` and `System Design.md` reference them:

| Diagram | Topic |
|---|---|
| [Target architecture](../diagrams/plan-target-architecture.drawio.png) | Services and platform overview |
| [Backend architecture](../diagrams/system-backend-architecture.drawio.png) | Service boundaries and data flow |
| [Agent invocation path](../diagrams/system-agent-invocation-path.drawio.png) | AI Study Assistant request path |
| [Voice agent path](../diagrams/system-voice-agent-path.drawio.png) | Voice channel and agent transport |
| [Large-scale architecture](../diagrams/plan-large-scale-architecture.drawio.png) | Scale-out direction |

## Implementation status (snapshot)

**Backend MVP merged on `main`:** #11–#24 (through SaaS plan limits).

**Active:** [Production Frontend](https://github.com/users/Vinosaamaa/projects/3) — start at **[#48](https://github.com/Vinosaamaa/chanter/issues/48)**. Agent order: [`docs/issues/agent-roadmap.md`](../issues/agent-roadmap.md).

**Next:** [Workable Product](https://github.com/users/Vinosaamaa/projects/4) (#60–#63, #31–#32) after **#51** (realtime text chat) merges.

The current `frontend/` app is an **API demo shell** for vertical slices. The mockups in `mockups/` show the **target product UI** — implemented in Production Frontend slices #48–#59.

## Editing assets

- **Mockups:** replace PNGs in `mockups/`; update [mockups/README.md](mockups/README.md) if filenames change.
- **Journey diagram:** edit `diagrams/user-journey.drawio` in [draw.io](https://app.diagrams.net), re-export PNG with embedded XML (`drawio -x -f png -o diagrams/user-journey.drawio.png diagrams/user-journey.drawio`).
- **Interactive tour:** edit `interactive/chanter-product-vision.canvas.tsx`; open in Cursor beside chat for a clickable walkthrough (see [interactive/README.md](interactive/README.md)).
