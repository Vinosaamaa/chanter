# Issue #254 change log

Repository: `Vinosaamaa/chanter`. Owner: issue #254. Lane: frontend presentation, responsive interaction and design evidence. Branch: `codex/254-ui-reconstruction`, created from `origin/main` at `2073de3`. One final issue-linked PR; internal design and first screenshots reviewed before publication.

## Acceptance and scope

Complete reconstruction of the learning/community product UI with the pinned frontend-design skill, a reusable design system, all viewport and accessibility checks, and preserved real API/session behavior. Full end-to-end no-dead-controls acceptance depends on #242, #244, #248, #249, #250 and #251. Visual work is authorized now; those gates remain open.

## Work

- Vendored unmodified upstream frontend-design guidance and Apache-2.0 license at commit `34040c9c568585f6929bedeaad110ad08f079624`, with invocation and update provenance.
- Authored and critiqued the [learning desk v3 system](../product-design/learning-desk-v3.md) before implementation.

## Verification

Pending implementation. No screenshot, mocked API or component test is recorded as full-stack proof.


## Workspace and public route reconstruction

- Replaced dark surface colors with the learning-desk semantic palette; removed the legacy saved-light-theme override.
- Rebuilt Course context with a native cohort selector, continuous conversation canvas, phone channel chooser, and responsive reading panels.
- Added phone list/conversation navigation for Friends and Inbox. Removed unsupported attachment, emoji and video buttons. Billing is a regular page with a direct Home route.
- Rebuilt marketing, authentication and legal reading surfaces. Removed fabricated invitation Course details and misleading illustrative queue actions from public entry.
- Focused tests: 21 V2 files / 85 tests pass. Billing deep-link navigation was observed failing before the direct Home link and passing afterward. Earlier cohort-selection and phone-chat regressions were also observed red/green.
- The first workspace production check passed TypeScript and bundling, then rejected CSS size (223,316 bytes against 220,000). Replacing old public-page styles addresses duplicate CSS; the limit is unchanged.
- The hosted Home run at 86fb2f7 produced twelve Home images, with eleven of twelve checks passing. The remaining failure is drawer focus during its visibility transition. The transition is removed; the browser focus assertion remains.
- Hosted visual fixtures now cover nineteen routes at phone and desktop widths plus landscape chat. Fixture responses are explicit test-server data and fail on missing API responses. These images do not prove backend functionality.


## Browser findings and second candidate

- Hosted run 34674558271: 49/51 checks pass; all requested screenshots captured. Drawer focus/Escape/return is now green. Failing checks identified a missing notification-read fixture and an offscreen landscape composer; portrait inspection found the same composer problem.
- Removed the old chat minimum height, corrected shared question avatar dimensions, added phone question list/detail/back navigation, and added portrait composer assertions. The fixture socket acknowledges subscriptions only; reconnecting-state coverage remains separate and no live-delivery claim is made.
- Removed unconfigured Google controls/setup-variable text, fabricated welcome enrollment details, unavailable Study room action, and outdated unused design constants. Community breadcrumbs use the current Study Server (observed red/green).
- Local verification: 24 component files / 97 tests pass; lint and production build pass. New import-graph budget tests observed red/green. Asset budgets now measure route delivery as described in the design document.
- Added axe WCAG checks for primary routes, tablet screenshots, Inbox completion/back behavior, question list/back behavior, and a reduced-motion/200%-equivalent reflow check. Hosted outcomes remain pending.
