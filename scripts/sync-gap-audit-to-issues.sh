#!/usr/bin/env bash
# Sync #87 gap audit onto GitHub issues.
# Agent rule: implement **What to build** + **Acceptance criteria** only.
# **Non-goals** = explicitly out of scope (deferred to another issue).
set -euo pipefail

REPO="Vinosaamaa/chanter"

update_issue() {
  gh issue edit "$1" --repo "$REPO" --body-file "$2"
  echo "Updated #$1"
}

# #93
cat > /tmp/issue-93-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (1 of 4)** — owner sign-off 2026-07-09.

## What to build

Study Server list shows only servers the user owns or can access; owner can delete a server they own (with confirm dialog). Multi-server picker cards per `study-server-home.png` (not initials-only rail). Fixes duplicate-server clutter from repeated local seeds without requiring psql.

Gap audit source: [`public-launch-ui-gap-audit.md`](https://github.com/Vinosaamaa/chanter/blob/main/docs/operations/public-launch-ui-gap-audit.md) — `study-server-home.png`.

## Acceptance criteria

- [ ] Multi-server landing or picker aligned with `study-server-home.png` (cards, not initials-only rail).
- [ ] Owner delete with confirm dialog (wire DELETE API if missing); integration test for delete authorization.
- [ ] Empty state + create CTA when user has zero servers.
- [ ] `product-cleanup-demo-servers.sh` remains for dev; UI delete works for normal users.
- [ ] Browser check documented in change log.

## Non-goals

- [ ] Per-server **course card** polish on home — **#89** (P1).

## Blocked by

#87
EOF
update_issue 93 /tmp/issue-93-body.md

# #90
cat > /tmp/issue-90-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (2 of 4)** — owner sign-off 2026-07-09.

## What to build

Production friend-request inbox per `friend-requests.png`: pending incoming/outgoing, accept/decline/cancel/block without REST-only workarounds. Polish Friends Hub per `friends-hub-dm.png` (presence, thread list, connection status, requests entry point, Online/All tabs if feasible).

Gap audit source: `friend-requests.png`, `friends-hub-dm.png`.

## Acceptance criteria

- [ ] `/app/friends/requests` or inbox panel: accept, decline, cancel outgoing, block where API supports.
- [ ] Nav badge for pending requests.
- [ ] Friends Hub: requests entry from hub; list + DM panel layout vs `friends-hub-dm.png`.
- [ ] Live DM still works over social realtime (#31).
- [ ] No reliance on `/dev/demo` for friend-request flows.
- [ ] Tests for inbox actions and hub rendering.
- [ ] *(Stretch)* Friend display names via profile lookup — only if time remains after P0 inbox; truncated IDs acceptable for merge.

## Non-goals

- [ ] None — inbox is the P0 deliverable.

## Blocked by

#87
EOF
update_issue 90 /tmp/issue-90-body.md

# #91
cat > /tmp/issue-91-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (3 of 4)** — owner sign-off 2026-07-09.

## What to build

Instructor-facing install flow per `ai-assistant-install.png`: install preview, grant checkboxes (channels, courses, cohorts, AI-approved resources), confirm install (HITL), and installed state — using existing #18 APIs (no seed script required). Replace `/dev/demo` install steer in `QuestionsContextPanel`.

## Acceptance criteria

- [ ] Instructor completes install from production UI on a fresh Study Server.
- [ ] Grant tree + checkboxes match preview; confirm install persists grants.
- [ ] Ask AI works for enrolled learner in `#questions` after install.
- [ ] Already-installed / re-install states handled gracefully.
- [ ] `QuestionsContextPanel` no longer points users to `/dev/demo` for install.
- [ ] `workable-product-demo.md` updated with UI install path; change log complete.

## Non-goals

- [ ] LLM streaming answer UX — **#100**.

## Blocked by

#87
EOF
update_issue 91 /tmp/issue-91-body.md

# #88
cat > /tmp/issue-88-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (4 of 4)** — owner sign-off 2026-07-09.

## What to build

Align signed-in Study Server shell with `app-shell.png`: server switcher, My courses sidebar, channel grouping, context column widgets (not only on `#questions`), global search overlay polish (scope/type filters where feasible), `#questions` right-rail **static** layout density. Preserve live realtime from #51.

Gap audit source: `app-shell.png`, `global-search.png`, `ai-support-question.png` (layout only), `course-resources.png` (shell surfacing only).

## Acceptance criteria

- [ ] Side-by-side review with `app-shell.png` — owner approves P0 layout.
- [ ] Context column shows useful widgets on non-`#questions` channels (not bare `ContextPlaceholder`).
- [ ] Global search: resource + FAQ results with mockup-aligned filters/scope UI.
- [ ] `#questions` right rail static layout vs `ai-support-question.png` (citations panel density).
- [ ] Channel selection, breadcrumbs, Friends entry match mockup hierarchy.
- [ ] No regression on live channel chat or voice channel entry.
- [ ] Frontend tests for navigation helpers where applicable.

## Non-goals

Gap audit #87 — **do not implement in #88** (owned elsewhere):

- [ ] `course-resources.png` **folder hierarchy** — flat list from #53 is launch scope.
- [ ] `global-search.png` **message / support-question** result types — resources + FAQs only.
- [ ] `ai-support-question.png` **streaming tokens**, **Mark helpful**, LLM answer quality — **#100** (after #94–#99).

## Blocked by

#87
EOF
update_issue 88 /tmp/issue-88-body.md

# #89
cat > /tmp/issue-89-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P1** — after P0 (#93, #90, #91, #88).

## What to build

Polish create Study Server, server home course cards, and cohort enrollment per `create-study-server.png`, `study-server-home.png` (course grid), `cohort-enrollment.png`: multi-step create wizard, enrollment admin table, invite link, TA assignment.

## Acceptance criteria

- [ ] Create-server flow vs `create-study-server.png` (description, icon, invite, review sidebar — step up from single field).
- [ ] Server home course cards and CTAs vs mockup (**#93** owns server picker/delete).
- [ ] Enrollment: learner table or list, invite link, TA assignment, search/pagination vs `cohort-enrollment.png`.
- [ ] Empty states and validation copy match product tone.
- [ ] Browser demo documented in change log.

## Non-goals

- [ ] `sign-in-onboarding.png` invite/cohort-discovery **right pane** on `/sign-in` — **#102** (optional there).
- [ ] Multi-server picker / delete — **#93**.

## Blocked by

#87, #93
EOF
update_issue 89 /tmp/issue-89-body.md

# #92
cat > /tmp/issue-92-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P1** — after P0 slices.

## What to build

Polish TA queue, office hours, FAQ approval, channel summary, and instructor dashboard per mockups. Improve density, tables, filters, empty states. Instructor dashboard **embed** for plan tier + AI usage vs `saas-billing.png` subsection (not a full billing app).

## Acceptance criteria

- [ ] `ta-queue.png` — layout/density; pick up/resolve unchanged functionally.
- [ ] `office-hours-voice.png` — schedule/waitlist UI vs mockup where feasible.
- [ ] `faq-approval.png` — approve flow + richer preview/editor vs mockup.
- [ ] `channel-summary.png` — metrics/digest/timeline vs mockup (AI text quality may follow **#94–#100**).
- [ ] `instructor-dashboard.png` — charts/tables/deep links into ops panels.
- [ ] SaaS plan + AI quota **subsection** on dashboard matches `saas-billing.png` embed (tier, meters).
- [ ] Instructor completes one full support loop in browser.
- [ ] *(Stretch)* Display names in TA queue/dashboard — only if time remains; IDs acceptable for merge.

## Non-goals

- [ ] `saas-billing.png` **dedicated** Plan & Billing settings page (storage, invoices, upgrade CTA) — dashboard embed is public-beta scope.

## Blocked by

#87
EOF
update_issue 92 /tmp/issue-92-body.md

# #100 — receives deferred AI UX from #88
cat > /tmp/issue-100-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/84

## What to build

Production `#questions` UI per `ai-support-question.png` after real LLM stack (**#94–#99**): **streaming** answer card, live citation chips, model/source metadata, instructor-visible audit snippet, **Mark helpful** on answers, full right-rail density vs mockup.

Gap audit #87: items deferred **from #88** into this issue.

## Acceptance criteria

- [ ] Learner sees tokens stream then final citations (SSE or documented stream path).
- [ ] **Mark helpful** control on AI answers (wire API or document backend follow-up).
- [ ] Right-rail layout matches `ai-support-question.png` with streaming affordance.
- [ ] Low-confidence handoff to TA queue unchanged.
- [ ] Instructor can see AI was used (dashboard or question metadata).
- [ ] Frontend tests for streaming state machine.

## Non-goals

- [ ] App shell / global search polish — **#88**.
- [ ] Replacing keyword-only backend before **#96** — this slice is UX on top of orchestration **#98**.

## Blocked by

#98
EOF
update_issue 100 /tmp/issue-100-body.md

# #102 — receives sign-in deferrals
cat > /tmp/issue-102-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/85

## What to build

Production auth for public/staging users: email verification on register, password reset via email link, secure session refresh, and **`sign-in-onboarding.png` alignment** for flows this slice owns — forgot-password link and UI on `/sign-in`, OAuth/SSO provider buttons (Google, Microsoft, GitHub) when configured for staging. Dev demo passwords remain for local seed only.

Gap audit #87: sign-in gaps deferred **from Phase 1 UI (#88–#93)** into this issue.

## Acceptance criteria

- [ ] Register → verify email → sign in on staging.
- [ ] **Forgot password:** link on `/sign-in` → email reset → sign in; UI matches `sign-in-onboarding.png` reset affordances.
- [ ] **SSO:** at least one OAuth provider works on staging when env vars set (document which providers in `.env.example` + ops doc).
- [ ] Rate limits on auth endpoints.
- [ ] Documented email provider config for staging.
- [ ] Browser/staging walkthrough in change log.

## Non-goals

- [ ] `sign-in-onboarding.png` **invite-link / cohort-discovery marketing right pane** — in-app enrollment is **#89**; optional on sign-in only if time remains after SSO + reset.

## Blocked by

#101
EOF
update_issue 102 /tmp/issue-102-body.md

# #104 — receives landing deferrals
cat > /tmp/issue-104-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/85

## What to build

Public beta launch checklist **and** landing page polish per gap audit #87 (`landing-page.png`): security review, backup, monitoring, support email, terms/privacy placeholders, getting-started updates, known limitations — plus richer marketing `/` preview (TA queue widget, course stats, Join Queue CTA) before beta.

## Acceptance criteria

- [ ] `docs/operations/public-beta-launch-checklist.md` complete with sign-off sections.
- [ ] README points to staging URL and beta scope.
- [ ] HANDOFF updated for post-launch phase.
- [ ] Landing `/` side-by-side with `landing-page.png` — owner approves marketing layout.
- [ ] App preview on landing shows mockup-aligned product chrome (TA queue / stats / CTA).
- [ ] Known limitations list mentions `course-storefront.png` is post-MVP commerce.

## Non-goals

- [ ] Notification / friends badges in marketing header — post-launch polish.
- [ ] `course-storefront.png` commerce storefront — later phase.

## Blocked by

#101, #103
EOF
update_issue 104 /tmp/issue-104-body.md

echo "All issues updated with What to build / Acceptance criteria / Non-goals structure."
