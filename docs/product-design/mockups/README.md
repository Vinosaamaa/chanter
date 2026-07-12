# UI mockups — learner & owner flows

Canonical PNG gallery for the **course-first** redesign (2026-07).

**Design:** [#114](https://github.com/Vinosaamaa/chanter/issues/114) (closed). **Implementation:** [`ui-v2-issue-breakdown.md`](../../issues/ui-v2-issue-breakdown.md) — epic [#115](https://github.com/Vinosaamaa/chanter/issues/115), start [#116](https://github.com/Vinosaamaa/chanter/issues/116).

## Start here (agents)

| Doc | Purpose |
|-----|---------|
| **[`../DESIGN-DECISIONS.md`](../DESIGN-DECISIONS.md)** | **Canonical decision record** — shell, tabs, roles, onboarding |
| [`../specs/layout-rules.md`](../specs/layout-rules.md) | Mockup generation rules |
| [`learner-flow/`](learner-flow/) | Learner journey PNGs + index |
| [`owner-flow/`](owner-flow/) | Owner / instructor PNGs (O1–O10) + index |

## Folders

| Folder | Audience |
|--------|----------|
| [`learner-flow/`](learner-flow/) | Students / enrolled learners — journeys 1–7, community 5a–5e |
| [`owner-flow/`](owner-flow/) | Study server owners & instructors — O1–O10 |

Component spec sheets: [`../specs/`](../specs/).

## Design direction (locked)

### Shell (learner + owner)

- **Sidebar:** Home · **[Teaching]** (instructor/owner) · Inbox · Calendar · Friends + server-grouped course list + `+ Join or create`
- **Top bar:** breadcrumb · search (**⌘F**, context-scoped) · one bell — **no avatar**
- **Profile:** flush bottom of sidebar; Settings = Discord-style modal

### Course workspace

`Overview · Chat · Questions · Resources · Office Hours · People`

### Community hub

`Announcements · Lounge · Events · Discover courses · Members`

### Search

Route-scoped top-bar search; **⌘F** focuses. **No** global ⌘K overlay.

## Legacy note

The flat 2026-06 PNG gallery in this folder root was retired July 2026. All screens now live in `learner-flow/` and `owner-flow/`.
