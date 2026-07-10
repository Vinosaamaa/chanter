#!/usr/bin/env bash
# Enrich #88–#93 with gap-audit checklists from #87. Idempotent section append.
set -euo pipefail

REPO="Vinosaamaa/chanter"
AUDIT="docs/operations/public-launch-ui-gap-audit.md"
MARKER="## Gap audit (#87)"

append_if_missing() {
  local num="$1"
  local body_file="$2"
  local current
  current="$(gh issue view "$num" --repo "$REPO" --json body -q .body)"
  if echo "$current" | grep -qF "$MARKER"; then
    echo "Issue #$num already has gap audit section; updating full body."
  fi
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

## Acceptance criteria

- [ ] Delete Study Server API + UI (owner-only, cascade documented).
- [ ] Server list empty state and create CTA per mockups.
- [ ] Integration test for delete authorization.
- [ ] Browser check documented in change log.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
append_if_missing 93 /tmp/issue-93-body.md

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

## Acceptance criteria

- [ ] User can accept or decline friend requests from UI.
- [ ] Friends Hub matches mockup for list + DM panel layout.
- [ ] Live DM still works over social realtime (#31).
- [ ] Tests for inbox actions and hub rendering.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
append_if_missing 90 /tmp/issue-90-body.md

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

## Acceptance criteria

- [ ] Instructor completes install from UI on a fresh Study Server.
- [ ] Grants match preview selections; Ask AI works for enrolled learner in #questions.
- [ ] Re-install / already-installed states handled gracefully.
- [ ] Documented in change log.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
append_if_missing 91 /tmp/issue-91-body.md

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
- [ ] `#questions` context rail density vs `ai-support-question.png`
- [ ] Top bar iconography and shell spacing vs `app-shell.png`

## Acceptance criteria

- [ ] Side-by-side review with `app-shell.png` — owner approves layout.
- [ ] Channel selection, breadcrumbs, and Friends entry match mockup hierarchy.
- [ ] No regression on live channel chat or voice channel entry.
- [ ] Frontend tests for navigation helpers where applicable.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
append_if_missing 88 /tmp/issue-88-body.md

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

## Acceptance criteria

- [ ] Owner can create a server and land on home with mockup-aligned cards and CTAs.
- [ ] Enrollment flow clear for instructor adding learners.
- [ ] Empty states and validation copy match product tone.

## Blocked by

#87, #93 (server list/delete foundation)
EOF
append_if_missing 89 /tmp/issue-89-body.md

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
| `saas-billing.png` | Dashboard embed | Partial | No full billing page (defer OK) |

### Gap checklist

- [ ] Each ops panel side-by-side with its mockup
- [ ] Instructor dashboard charts and deep links into ops
- [ ] Display names where mockup shows names (#31 deferral)

## Acceptance criteria

- [ ] Each panel reviewed against its mockup.
- [ ] Instructor can complete one full support loop in browser.
- [ ] No API contract breaks.

## Blocked by

#87 (gap audit sign-off — ready when #87 merges)
EOF
append_if_missing 92 /tmp/issue-92-body.md

# #87 — mark sign-off on parent issue
gh issue comment 87 --repo "$REPO" --body "$(cat <<'EOF'
**Owner sign-off (2026-07-09):** Gap audit approved. P0 implementation order: **#93 → #90 → #91 → #88**, then P1 **#89 → #92**.

Child issues #88–#93 updated with gap-audit checklists from `docs/operations/public-launch-ui-gap-audit.md`.
EOF
)"

echo "Done. See $AUDIT for master table."
