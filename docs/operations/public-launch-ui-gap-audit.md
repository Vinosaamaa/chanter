# Public Launch — UI gap audit

> **Issue:** [#87](https://github.com/Vinosaamaa/chanter/issues/87)  
> **Date:** 2026-07-09  
> **Method:** Compared all 19 MVP mockups in `[docs/product-design/mockups/](../product-design/mockups/)` against production routes in `frontend/src/app/router.tsx` and feature components. Post-#86 stack was not re-run in this session (local Java gate); gaps are confirmed from code + prior demo verification.

## Executive summary

Production frontend **covers most flows functionally** (#48–#59, #31–#32) but **none of the 19 screens are mockup-faithful**. The largest product gaps for launch are:


| Theme                                 | Mockup(s)                            | Production today                                                                 | Follow-up        |
| ------------------------------------- | ------------------------------------ | -------------------------------------------------------------------------------- | ---------------- |
| **Friend requests inbox**             | `friend-requests.png`                | Dev-only (`/dev/demo`)                                                           | **#90**          |
| **AI Study Assistant install (HITL)** | `ai-assistant-install.png`           | Dev-only + read-only status in `#questions`                                      | **#91**          |
| **Study Server management**           | `study-server-home.png`              | Per-server home only; no multi-server landing, delete, or empty states           | **#89**, **#93** |
| **App shell density & context rail**  | `app-shell.png`, `global-search.png` | 4-column shell exists; context column mostly placeholder; search overlay limited | **#88**          |


**Owner sign-off:** **Approved 2026-07-09** (owner review of this document).

## Implementation order (post–#87)

**P0 (do first):** [#93](https://github.com/Vinosaamaa/chanter/issues/93) → [#90](https://github.com/Vinosaamaa/chanter/issues/90) → [#91](https://github.com/Vinosaamaa/chanter/issues/91) → [#88](https://github.com/Vinosaamaa/chanter/issues/88)

**P1 (after P0):** [#89](https://github.com/Vinosaamaa/chanter/issues/89) → [#92](https://github.com/Vinosaamaa/chanter/issues/92)

---

## Master gap table

Status key: **OK** = close enough for beta; **Partial** = works but visibly diverges; **Missing** = no production screen/route.


| #   | Mockup                     | Production route                                                         | Status      | Priority | Top gaps                                                                                                                           | Slice             |
| --- | -------------------------- | ------------------------------------------------------------------------ | ----------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------- | ----------------- |
| 1   | `landing-page.png`         | `/`                                                                      | Partial     | P2       | Static preview vs rich mockup widgets; no social/notification chrome                                                               | Defer → #104      |
| 2   | `sign-in-onboarding.png`   | `/sign-in`                                                               | Partial     | P2       | Email/password only; no SSO, forgot-password, invite/cohort discovery panes                                                        | #102 + defer      |
| 3   | `create-study-server.png`  | `/app/onboarding/create-study-server`                                    | Partial     | P1       | Single name field vs 3-step wizard (description, icon, invite, review sidebar)                                                     | **#89**           |
| 4   | `study-server-home.png`    | `/app` → redirect; `/app/servers/:serverId/home`                         | Partial     | **P0**   | No multi-server picker cards; server rail is initials-only; no create/delete/manage; no empty state                                | **#89**, **#93**  |
| 5   | `app-shell.png`            | `/app/servers/:serverId/study-channels/:channelId` (and course channels) | Partial     | **P0**   | Context column placeholder outside `#questions`; no TA queue / resources widgets in right rail; top bar text links vs icon density | **#88**           |
| 6   | `global-search.png`        | Overlay (⌘K / top bar); **no route**                                     | Partial     | P1       | Resources + FAQs only; no messages; no scope/type filters; disabled on `/app/friends` without active server                        | **#88**           |
| 7   | `ai-support-question.png`  | `.../course-channels/:channelId` (`#questions`)                          | Partial     | P1       | Core Ask AI + citations work; right rail thinner than mockup; “Mark helpful” stub; mobile hides context panel                      | **#88**, **#100** |
| 8   | `course-resources.png`     | `.../course-channels/:channelId` (`#resources`)                          | Partial     | P1       | Upload/search/filters shipped; **flat list** (no folders); no shell widget surfacing resources                                     | **#88**           |
| 9   | `ta-queue.png`             | `.../courses/:courseId/support/ta-queue`                                 | Partial     | P1       | Pick up/resolve works; standalone page not embedded in shell widget; learner IDs not display names                                 | **#92**           |
| 10  | `office-hours-voice.png`   | `.../support/office-hours`                                               | Partial     | P1       | Schedule/waitlist/admit exist; no participant grid / calendar layout from mockup                                                   | **#92**           |
| 11  | `faq-approval.png`         | `.../support/faq-approval`                                               | Partial     | P1       | Approve flow exists; lacks split editor, category badges, richer preview                                                           | **#92**           |
| 12  | `channel-summary.png`      | `.../course-channels/:channelId/summary`                                 | Partial     | P1       | Metrics/digest/timeline present; AI digest quality depends on #94–#100 backend                                                     | **#92**           |
| 13  | `instructor-dashboard.png` | `/app/instructor-dashboard`                                              | Partial     | P1       | Metric cards + billing subsection; missing charts, FAQ/TA tables, date filters, deep links                                         | **#92**           |
| 14  | `cohort-enrollment.png`    | `/app/servers/:serverId/courses/:courseId/enrollment`                    | Partial     | P1       | Manual UUID enroll only; no learner table, invite link, TA assignment, search/pagination                                           | **#89**           |
| 15  | `ai-assistant-install.png` | **None** (status in `#questions` panel only)                             | **Missing** | **P0**   | No production HITL install modal (grant tree, confirm); copy points to `/dev/demo` + seed script                                   | **#91**           |
| 16  | `saas-billing.png`         | **None** (subsection on instructor dashboard)                            | Partial     | P2       | Plan tier + AI meter only; no dedicated billing settings, storage meter, invoices, upgrade CTA                                     | **#92** or defer  |
| 17  | `friends-hub-dm.png`       | `/app/friends`                                                           | Partial     | P1       | DM + presence + voice call work; truncated user IDs; no Online/All tabs; no requests entry point                                   | **#90**           |
| 18  | `friend-requests.png`      | **None** (`/dev/demo` only)                                              | **Missing** | **P0**   | No inbox, accept/decline/block UI, nav badge, or `/app/friends/requests` route                                                     | **#90**           |
| 19  | `course-storefront.png`    | —                                                                        | N/A         | Defer    | Post-MVP commerce; out of Public Launch UI scope                                                                                   | Later phase       |


---



## Proposed P0 backlog (owner sign-off)

These four themes match issue #87 acceptance criteria. **Confirm or reprioritize before #88–#93 implementation.**


| P0 item                        | Mockup evidence                      | Current gap                                                                                         | Proposed slice                  | Est. scope                                                                     |
| ------------------------------ | ------------------------------------ | --------------------------------------------------------------------------------------------------- | ------------------------------- | ------------------------------------------------------------------------------ |
| **Friend requests**            | `friend-requests.png`                | No production inbox; APIs exist (`/api/v1/friend-requests`) but UI is dev-demo only                 | **#90**                         | New `/app/friends/requests` or inbox panel + nav badge                         |
| **AI Study Assistant install** | `ai-assistant-install.png`           | Install preview/confirm only in `/dev/demo`; production panel is read-only                          | **#91**                         | HITL modal from instructor context; wire existing install APIs                 |
| **Study Server list & delete** | `study-server-home.png`              | Multi-server picker, delete, and empty states missing; duplicate servers from re-seed               | **#93** (+ **#89** home layout) | Server cards rail + DELETE API if missing + empty/zero-state copy              |
| **App shell density**          | `app-shell.png`, `global-search.png` | Context rail mostly placeholder; search overlay thin; channel shell spacing/typography below mockup | **#88**                         | Context widgets, top-bar iconography, search filters, `#questions` rail polish |




### Owner sign-off checklist

- [x] **P0 list approved** as written (owner review 2026-07-09).
- [x] **P1 order** confirmed: **P0** #93 → #90 → #91 → #88, then **P1** #89 → #92.
- [x] **Deferrals accepted:** landing/sign-in polish → #102/#104; folder hierarchy on resources; full SaaS billing page.

---



## P1 backlog (polish after P0 sign-off)


| Slice                                                  | Mockups                                                                   | Main work                                                                            |
| ------------------------------------------------------ | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| [#88](https://github.com/Vinosaamaa/chanter/issues/88) | `app-shell`, `global-search`, `ai-support-question`, `course-resources`   | Shell density, context column widgets, search scope/filters, `#questions` right rail |
| [#89](https://github.com/Vinosaamaa/chanter/issues/89) | `create-study-server`, `study-server-home`, `cohort-enrollment`           | Onboarding wizard, server home cards, enrollment admin table                         |
| [#90](https://github.com/Vinosaamaa/chanter/issues/90) | `friend-requests`, `friends-hub-dm`                                       | Requests inbox + Friends Hub polish (names, tabs, badges)                            |
| [#91](https://github.com/Vinosaamaa/chanter/issues/91) | `ai-assistant-install`                                                    | Production install flow (overlaps P0)                                                |
| [#92](https://github.com/Vinosaamaa/chanter/issues/92) | TA queue, office hours, FAQ, summary, instructor dashboard, billing embed | Ops panels + dashboard charts/deep links                                             |
| [#93](https://github.com/Vinosaamaa/chanter/issues/93) | `study-server-home`                                                       | List/delete/empty states (overlaps P0)                                               |


---

## Deferrals → issues

Owner accepted deferrals **2026-07-09**. “Deferred” means **moved from Phase 1 UI (#88–#93) to the owning issue** — not optional forever.

**Agent rule (issue bodies):**

| Section | Agent behavior |
|---------|----------------|
| **What to build** + **Acceptance criteria** | **Implement** — merge gate |
| **Non-goals** | **Do not implement** on this issue; work lives on the linked issue |
| *(Stretch)* in acceptance | Implement only if P0/P1 criteria are already done |

| Gap | Mockup | Owning issue | Formerly in |
|-----|--------|--------------|-------------|
| Landing marketing polish | `landing-page.png` | **#104** — in What to build + AC | Phase 1 UI |
| SSO, forgot-password, sign-in flows | `sign-in-onboarding.png` | **#102** — in What to build + AC | Phase 1 UI |
| Streaming AI, Mark helpful | `ai-support-question.png` | **#100** — in What to build + AC | **#88** Non-goals |
| Resource folders | `course-resources.png` | Post-launch | **#88** Non-goals |
| Message search | `global-search.png` | Post-launch | **#88** Non-goals |
| Full SaaS billing page | `saas-billing.png` | Post-launch | **#92** Non-goals |
| Display names (friends / TA) | various | Stretch on **#90** / **#92** | Optional if time |
| Course storefront | `course-storefront.png` | Post-MVP | **#104** Non-goals |

---

## Cross-cutting notes

1. `/dev/demo` **leakage:** `QuestionsContextPanel`, `AppShellPlaceholderPage`, and marketing demo link still steer users to `/dev/demo` for install and API harness behaviors. P0/P1 slices should replace these with production routes.
2. **Display names:** Friends Hub and TA queue show truncated user IDs; profile lookup deferred in #31 — revisit in #90/#92.
3. **AI answer UX:** Mockup fidelity for streaming/citations may need **#100** after real LLM stack (**#94–#99**).
4. **Verification:** After owner sign-off, each polish slice should include browser check against the matching PNG at `localhost:5173` (see `[workable-product-demo.md](workable-product-demo.md)`).

---



## Related

- [Public Launch issue breakdown](../issues/public-launch-issue-breakdown.md)
- [Mockup gallery](../product-design/mockups/README.md)
- [Issue #87 change log](issue-87-change-log.md)

