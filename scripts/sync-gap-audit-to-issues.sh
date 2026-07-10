#!/usr/bin/env bash
# Sync #87 gap audit: priorities, checklists, and deferrals onto GitHub issues.
set -euo pipefail

REPO="Vinosaamaa/chanter"
AUDIT="docs/operations/public-launch-ui-gap-audit.md"

update_issue() {
  local num="$1"
  local body_file="$2"
  gh issue edit "$num" --repo "$REPO" --body-file "$body_file"
  echo "Updated #$num"
}

# #93 — P0 (1)
cat > /tmp/issue-93-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (1 of 4)** — owner sign-off 2026-07-09. First P0 slice after #87.

## What to build

Study Server list shows only servers the user owns or can access; owner can delete a server they own (with confirm dialog). Multi-server picker cards per `study-server-home.png` (not initials-only rail). Fixes duplicate-server clutter from repeated local seeds without requiring psql.

## Gap audit (#87)

Source: [`public-launch-ui-gap-audit.md`](https://github.com/Vinosaamaa/chanter/blob/main/docs/operations/public-launch-ui-gap-audit.md)

| Mockup | Route | Status | Gaps |
|--------|-------|--------|------|
| `study-server-home.png` | `/app`, `/app/servers/:serverId/home` | Partial | No multi-server picker cards; server rail initials-only; no create/delete/manage; no empty state |

### Gap checklist

- [ ] Multi-server landing or picker aligned with `study-server-home.png`
- [ ] Owner delete with confirm dialog (wire DELETE API if missing)
- [ ] Empty state + create CTA when user has zero servers
- [ ] `product-cleanup-demo-servers.sh` remains for dev; UI delete for normal users

## Deferred from gap audit (#87)

_None — this slice owns P0 server list/delete/home picker gaps. Course-card polish on server home remains in **#89**._

## Acceptance criteria

- [ ] Delete Study Server API + UI (owner-only, cascade documented).
- [ ] Server list empty state and create CTA per mockups.
- [ ] Integration test for delete authorization.
- [ ] Browser check documented in change log.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
update_issue 93 /tmp/issue-93-body.md

# #90 — P0 (2)
cat > /tmp/issue-90-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (2 of 4)** — owner sign-off 2026-07-09.

## What to build

Production friend-request inbox per `friend-requests.png`: pending incoming/outgoing, accept/decline/cancel without REST-only workarounds. Polish Friends Hub layout per `friends-hub-dm.png` (presence, thread list, connection status, requests entry point).

## Gap audit (#87)

| Mockup | Route | Status | Gaps |
|--------|-------|--------|------|
| `friend-requests.png` | **None** (`/dev/demo` only) | **Missing** | No inbox, accept/decline/block UI, nav badge, `/app/friends/requests` |
| `friends-hub-dm.png` | `/app/friends` | Partial | Truncated user IDs; no Online/All tabs; no pending-requests entry |

### Gap checklist

- [ ] `/app/friends/requests` or inbox panel with accept/decline/cancel
- [ ] Nav badge for pending requests
- [ ] Friends Hub: requests entry, presence grouping polish
- [ ] Remove reliance on `/dev/demo` for friend-request flows

## Deferred from gap audit (#87)

Owner deferral 2026-07-09 — **do not block P0 inbox on these:**

- [ ] **Friend display names** (profile lookup) — deferred since #31; truncated user IDs acceptable for P0; polish if time remains in this slice

## Acceptance criteria

- [ ] User can accept or decline friend requests from UI.
- [ ] Friends Hub matches mockup for list + DM panel layout.
- [ ] Live DM still works over social realtime (#31).
- [ ] Tests for inbox actions and hub rendering.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
update_issue 90 /tmp/issue-90-body.md

# #91 — P0 (3)
cat > /tmp/issue-91-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (3 of 4)** — owner sign-off 2026-07-09.

## What to build

Instructor-facing install flow per `ai-assistant-install.png`: install preview, grant checkboxes (channels, courses, cohorts, AI-approved resources), confirm install, and installed state — using existing #18 APIs (no seed script required).

## Gap audit (#87)

| Mockup | Route | Status | Gaps |
|--------|-------|--------|------|
| `ai-assistant-install.png` | **None** (read-only in `#questions` panel) | **Missing** | No HITL install modal; copy points to `/dev/demo` + seed |

### Gap checklist

- [ ] Production install preview + grant tree + confirm modal
- [ ] Wire existing install APIs from instructor context
- [ ] Update `QuestionsContextPanel` — remove `/dev/demo` steer
- [ ] Update `workable-product-demo.md` with UI install path

## Deferred from gap audit (#87)

_None — this slice fully owns the deferred-out `/dev/demo` install path._

## Acceptance criteria

- [ ] Instructor completes install from UI on a fresh Study Server.
- [ ] Grants match preview selections; Ask AI works for enrolled learner in #questions.
- [ ] Re-install / already-installed states handled gracefully.
- [ ] Documented in change log.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
update_issue 91 /tmp/issue-91-body.md

# #88 — P0 (4)
cat > /tmp/issue-88-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P0 (4 of 4)** — owner sign-off 2026-07-09.

## What to build

Align the signed-in Study Server shell with `app-shell.png` and related overlays: server switcher, My courses sidebar, channel grouping, context column widgets, global search polish, `#questions` right rail. Preserve live realtime from #51.

## Gap audit (#87)

| Mockup | Route | Status | Gaps |
|--------|-------|--------|------|
| `app-shell.png` | `/app/servers/:serverId/...` | Partial | Context placeholder outside `#questions`; no TA/resources widgets; top bar density |
| `global-search.png` | Overlay (⌘K) | Partial | Resources + FAQs only; no messages/filters; disabled on Friends without server |
| `ai-support-question.png` | `#questions` channel | Partial | Thinner right rail; Mark helpful stub; mobile hides panel |
| `course-resources.png` | `#resources` channel | Partial | Flat list OK; no shell widget surfacing resources |

### Gap checklist

- [ ] Context column widgets (not only `ContextPlaceholder` on non-questions channels)
- [ ] Global search: scope/type filters per mockup where feasible
- [ ] `#questions` context rail density vs `ai-support-question.png` (static layout; streaming → **#100**)
- [ ] Top bar iconography and shell spacing vs `app-shell.png`

## Deferred from gap audit (#87)

Owner deferral 2026-07-09 — **explicitly out of #88 scope:**

- [ ] `course-resources.png` **folder hierarchy** — keep flat list from #53 for launch
- [ ] `global-search.png` **message / support-question results** — resources + FAQs only
- [ ] `ai-support-question.png` **streaming tokens**, **Mark helpful** action, and LLM-quality answers → **#100** (blocked by #94–#99 / #98)

## Acceptance criteria

- [ ] Side-by-side review with `app-shell.png` — owner approves layout.
- [ ] Channel selection, breadcrumbs, and Friends entry match mockup hierarchy.
- [ ] No regression on live channel chat or voice channel entry.
- [ ] Frontend tests for navigation helpers where applicable.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
update_issue 88 /tmp/issue-88-body.md

# #89 — P1
cat > /tmp/issue-89-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P1** — after P0 slices (#93, #90, #91, #88). Overlaps #93 on server home cards.

## What to build

Polish create Study Server, server home, and cohort enrollment flows per `create-study-server.png`, `study-server-home.png`, `cohort-enrollment.png`.

## Gap audit (#87)

| Mockup | Route | Status | Gaps |
|--------|-------|--------|------|
| `create-study-server.png` | `/app/onboarding/create-study-server` | Partial | Single field vs 3-step wizard |
| `study-server-home.png` | `/app/servers/:serverId/home` | Partial | Course grid only; P0 picker/delete in **#93** |
| `cohort-enrollment.png` | `.../enrollment` | Partial | UUID enroll only; no table, invite, TA assign |

### Gap checklist

- [ ] Create-server wizard (description, icon, invite, review sidebar)
- [ ] Server home course cards and CTAs vs mockup
- [ ] Enrollment admin table, invite link, TA assignment

## Deferred from gap audit (#87)

- [ ] `sign-in-onboarding.png` **invite-link / cohort-discovery right pane** — deferred to **#102** (auth/onboarding at staging launch); in-app enrollment stays in this slice

## Acceptance criteria

- [ ] Owner can create a server and land on home with mockup-aligned cards and CTAs.
- [ ] Enrollment flow clear for instructor adding learners.
- [ ] Empty states and validation copy match product tone.

## Blocked by

#87, #93 (server list/delete foundation)
EOF
update_issue 89 /tmp/issue-89-body.md

# #92 — P1
cat > /tmp/issue-92-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/83

## Priority

**P1** — after P0 slices.

## What to build

Polish TA queue, office hours, FAQ approval, channel summary, and instructor dashboard panels per mockups. Improve density, tables, filters, and empty states.

## Gap audit (#87)

| Mockup | Route | Status | Gaps |
|--------|-------|--------|------|
| `ta-queue.png` | `.../support/ta-queue` | Partial | Standalone page; IDs not names |
| `office-hours-voice.png` | `.../support/office-hours` | Partial | No participant grid / calendar |
| `faq-approval.png` | `.../support/faq-approval` | Partial | No split editor / category badges |
| `channel-summary.png` | `.../summary` | Partial | AI digest quality → #94–#100 |
| `instructor-dashboard.png` | `/app/instructor-dashboard` | Partial | Missing charts, tables, deep links |
| `saas-billing.png` | Dashboard embed | Partial | No full billing page |

### Gap checklist

- [ ] Each ops panel side-by-side with its mockup
- [ ] Instructor dashboard charts and deep links into ops
- [ ] SaaS plan + AI usage embed matches `saas-billing.png` **subsection** (not full page)

## Deferred from gap audit (#87)

Owner deferral 2026-07-09 — **out of #92 scope:**

- [ ] `saas-billing.png` **dedicated Plan & Billing settings page** (storage meter, invoices, upgrade CTA) — instructor dashboard embed is sufficient for public beta
- [ ] **Display names** in TA queue / instructor dashboard — profile lookup deferred since **#31**; IDs acceptable unless time remains

## Acceptance criteria

- [ ] Each panel reviewed against its mockup.
- [ ] Instructor can complete one full support loop in browser.
- [ ] No API contract breaks.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
update_issue 92 /tmp/issue-92-body.md

# #100 — deferred items from #88 / ai-support-question
cat > /tmp/issue-100-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/84

## What to build

Production #questions UI: streaming answer card, live citation chips, model/source metadata, instructor-visible audit snippet. Match `ai-support-question.png` with streaming affordance.

## Deferred from gap audit (#87)

Owner deferral 2026-07-09 — carried **from #88** (`ai-support-question.png`):

- [ ] Streaming answer tokens (vs static answer card)
- [ ] **Mark helpful** control on AI answers
- [ ] Full right-rail density and citation UX after real LLM (**#94–#99**)

## Acceptance criteria

- [ ] Learner sees tokens stream then final citations.
- [ ] Low-confidence handoff unchanged.
- [ ] Instructor can see that AI was used (dashboard or question metadata).
- [ ] Frontend tests for streaming state machine.

## Blocked by

#98
EOF
update_issue 100 /tmp/issue-100-body.md

# #102 — sign-in / auth deferrals
cat > /tmp/issue-102-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/85

## What to build

Auth flows for public users: email verification on register, password reset via email link, secure session refresh. Dev demo passwords remain for local seed only.

## Deferred from gap audit (#87)

Owner deferral 2026-07-09 — `sign-in-onboarding.png` gaps **out of Phase 1 UI (#88–#93)**:

| Mockup gap | Notes |
|------------|-------|
| SSO providers (Google, Microsoft, GitHub) | Production SSO when staging auth lands |
| Forgot-password link + reset flow UI | Pairs with password-reset acceptance criteria below |
| Invite-link and cohort-discovery right pane | In-app enrollment in **#89**; sign-in marketing pane defer OK |

## Acceptance criteria

- [ ] Register → verify email → sign in on staging.
- [ ] Forgot password → reset → sign in.
- [ ] Rate limits on auth endpoints.
- [ ] Documented email provider config for staging.

## Blocked by

#101
EOF
update_issue 102 /tmp/issue-102-body.md

# #104 — landing / launch deferrals
cat > /tmp/issue-104-body.md <<'EOF'
## Parent

https://github.com/Vinosaamaa/chanter/issues/85

## What to build

Launch checklist: security review, backup, monitoring, support email, terms/privacy placeholders, updated getting-started for public beta, known limitations list.

## Deferred from gap audit (#87)

Owner deferral 2026-07-09 — `landing-page.png` gaps **out of Phase 1 UI (#88–#93)**:

| Mockup gap | Notes |
|------------|-------|
| Rich app preview (TA queue widget, course stats, Join Queue) | Marketing polish at public beta prep |
| Notification / friends badges in marketing chrome | Post-launch marketing |

Also track post-MVP: `course-storefront.png` (commerce) — not Public Launch scope.

## Acceptance criteria

- [ ] `docs/operations/public-beta-launch-checklist.md` complete with sign-off sections.
- [ ] README points to staging URL and beta scope.
- [ ] HANDOFF updated for post-launch phase.

## Blocked by

#101, #103
EOF
update_issue 104 /tmp/issue-104-body.md

gh issue comment 87 --repo "$REPO" --body "Deferrals synced to #88 #90 #92 #100 #102 #104. See gap audit doc section Deferrals → issues."

echo "Done. See $AUDIT"
