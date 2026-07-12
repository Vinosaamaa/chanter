# Chanter Product Design Showcase

Start here if you want the **product story** — what Chanter is, what it looks like, and how users move through it — without reading the whole codebase.

**Referenced from:** [`README.md`](../../README.md), [`HANDOFF.md`](../../HANDOFF.md), [`plan.md`](../../plan.md), [`System Design.md`](../../System Design.md), [`CONTEXT.md`](../../CONTEXT.md), [`frontend/README.md`](../../frontend/README.md).

**Positioning:**

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

## Quick tour

| Asset | What it shows |
|---|---|
| [**DESIGN-DECISIONS.md**](DESIGN-DECISIONS.md) | **Canonical UI decisions** — agents implement from this |
| [**Mockup gallery**](mockups/README.md) | Course-first PNGs — [`learner-flow/`](mockups/learner-flow/) · [`owner-flow/`](mockups/owner-flow/) |
| [Component specs](specs/) | Sidebar, top bar, chat, cards — + [`layout-rules.md`](specs/layout-rules.md) |
| [Product vision](vision.md) | Narrative walkthrough with user stories |
| [Visibility and social model](visibility-and-social-model.md) | Global friends + enrollment-scoped course sidebar |
| [User journey diagram](diagrams/user-journey.drawio.png) | Click-flow map for MVP screens and later phases |
| [Interactive screen tour](interactive/) | Cursor canvas — click through screens in the IDE |

## Mockups at a glance

**Design:** [#114](https://github.com/Vinosaamaa/chanter/issues/114) — **closed** (mockups complete).  
**Implementation:** epic [#115](https://github.com/Vinosaamaa/chanter/issues/115), slices **#116–#128** — [`ui-v2-issue-breakdown.md`](../issues/ui-v2-issue-breakdown.md).

| Category | Screens |
|---|---|
| **Onboarding** | signup invite · welcome · home |
| **Course workspace** | Overview · Chat · Questions · Resources · Office Hours · People |
| **Community hub** | Announcements · Lounge · Events · Discover courses · Members |
| **Social** | Inbox · Calendar · Friends + DM |
| **Owner / instructor** | O1–O10 — see [`mockups/owner-flow/`](mockups/owner-flow/) |

Full index: [`mockups/README.md`](mockups/README.md) · learner: [`mockups/learner-flow/`](mockups/learner-flow/) · owner: [`mockups/owner-flow/`](mockups/owner-flow/)

## Folder layout

```
docs/product-design/
├── README.md                 ← you are here
├── DESIGN-DECISIONS.md       ← canonical UI reference for agents
├── vision.md
├── visibility-and-social-model.md
├── specs/                    ← component spec sheets + layout-rules.md
├── mockups/
│   ├── learner-flow/         ← learner journey PNGs
│   └── owner-flow/           ← owner / instructor PNGs
├── diagrams/
└── interactive/
```

Legacy 2026-06 flat PNGs were retired; all screens live under `mockups/learner-flow/` and `mockups/owner-flow/`. Owner screen mapping from old concepts: [`DESIGN-DECISIONS.md` §8](DESIGN-DECISIONS.md).

## Related product docs

| Document | Purpose |
|---|---|
| [Education MVP PRD](../product/education-mvp-prd.md) | Problem, solution, user stories, implementation decisions |
| [Issue breakdown](../issues/education-mvp-issue-breakdown.md) | Backend epics and slices (#11–#24) — **done** |
| [Production frontend breakdown](../issues/production-frontend-issue-breakdown.md) | Legacy UI phase (#47–#59) — superseded for layout |
| [**UI v2 breakdown**](../issues/ui-v2-issue-breakdown.md) | **Active** — course-first shell (#115–#128) |
| [Workable product breakdown](../issues/workable-product-issue-breakdown.md) | Full-stack local app (#60–#63, #31–#32) |
| [**Agent workflow**](../operations/agent-workflow.md) | **Mandatory agent workflow** (project boards #3, #4) |
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

**Workable Product merged:** #60–#63, #31–#32.

**Active — UI v2:** epic [#115](https://github.com/Vinosaamaa/chanter/issues/115), **start [#116](https://github.com/Vinosaamaa/chanter/issues/116)**. Agent workflow: [`docs/operations/agent-workflow.md`](../operations/agent-workflow.md) § Phase 2b.

**Agents implementing UI must:**

1. Read [`DESIGN-DECISIONS.md`](DESIGN-DECISIONS.md) and [`specs/layout-rules.md`](specs/layout-rules.md)
2. Match the issue-linked PNG in `mockups/learner-flow/` or `mockups/owner-flow/`
3. Follow [`visibility-and-social-model.md`](visibility-and-social-model.md) for sidebar vs Friends scope

The current `frontend/` app uses the **legacy channel-tree shell**. **Target product UI** = v2 mockups above — not the retired flat PNG gallery.

## Editing assets

- **Mockups:** PNGs in `mockups/learner-flow/` and `mockups/owner-flow/`; update each folder’s `README.md` when adding screens.
- **Journey diagram:** edit `diagrams/user-journey.drawio` in [draw.io](https://app.diagrams.net), re-export PNG with embedded XML (`drawio -x -f png -o diagrams/user-journey.drawio.png diagrams/user-journey.drawio`).
- **Interactive tour:** edit `interactive/chanter-product-vision.canvas.tsx`; open in Cursor beside chat for a clickable walkthrough (see [interactive/README.md](interactive/README.md)).
