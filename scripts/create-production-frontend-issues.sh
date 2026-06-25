#!/usr/bin/env bash
set -euo pipefail
MILESTONE="Production Frontend"

create_issue() {
  local title="$1"
  local labels="$2"
  local body="$3"
  gh issue create --title "$title" --label "$labels" --milestone "$MILESTONE" --body "$body"
}

EPIC_BODY="$(cat <<'EOF'
## Problem

Education MVP backend slices **#11–#24** shipped APIs and a single-file demo UI. Educators and learners still cannot use Chanter as a product — there is no auth screen, Discord-like app shell, realtime chat, or mockup-aligned flows.

## Goals

- Deliver the **production React shell** described in `docs/product-design/vision.md` and `docs/product-design/mockups/`.
- Replace caller-supplied user IDs in `frontend/src/App.tsx` with real session-based UX (pairs with **#30**).
- Bootstrap **`realtime-service`** for live Course Channel messaging (**#37**).
- Make the product demoable to buyers without exposing the API harness.

## Non-goals

- Course storefront commerce UI (`course-storefront.png`) — future PRD.
- Rebuilding backend APIs from #11–#24.
- Native mobile apps or Organization SSO.

## Vertical Slices

- [ ] **#34** Bootstrap Production Frontend Foundation
- [ ] **#35** Auth UI And Protected App Routes (with **#30**)
- [ ] **#36** Study Server App Shell And Navigation
- [ ] **#37** Bootstrap Realtime Service And Live Course Channel Chat
- [ ] **#38** Production `#questions` UX With AI Context Panel
- [ ] **#39** Production Course Resources Panel
- [ ] **#40** Production Support Operations UI
- [ ] **#41** Production Instructor Dashboard And SaaS Plan UI
- [ ] **#42** Production Onboarding And Enrollment Flows
- [ ] **#43** Global Search UI And Search Service Bootstrap
- [ ] **#44** Channel Summary UI For Course Channels
- [ ] **#45** Public Marketing Landing Page (optional polish)

Post-MVP social mockups: **#31**, **#32**.

## Architecture Impact

- Restructure `frontend/` into feature routes (React Router, TanStack Query, Zustand, Tailwind/shadcn-style components).
- Implement `backend/realtime-service` (WebSocket gateway integration, channel subscriptions).
- Bootstrap `backend/search-service` in **#43**.
- Auth: gateway + auth-service JWT/session; remove `TODO(#auth)` body params from UI flows when **#30** lands.
- Reference: `docs/issues/production-frontend-issue-breakdown.md`, `plan.md` Milestones 1–3.
EOF
)"

echo "Creating epic..."
EPIC_URL=$(create_issue "Epic: Product Shell And Production Frontend" "epic,education,frontend,architecture,ready-for-agent" "$EPIC_BODY")
echo "Epic: $EPIC_URL"

slice() {
  local title="$1"
  local labels="$2"
  local blocked="$3"
  local build="$4"
  local criteria="$5"
  local body
  body="$(cat <<EOF
## Parent

Epic: Product Shell And Production Frontend (#33)

## What to build

$build

## Acceptance criteria

$criteria

## Blocked by

$blocked

## References

- \`docs/issues/production-frontend-issue-breakdown.md\`
- \`docs/product-design/mockups/\`
- \`docs/product-design/visibility-and-social-model.md\`
EOF
)"
  create_issue "$title" "$labels" "$body"
}

echo "Creating slices..."
slice "Slice: Bootstrap Production Frontend Foundation" "story,ready-for-agent,frontend,education" "None — can start immediately." \
"Establish the production frontend architecture: React Router, TanStack Query, Zustand, Tailwind (shadcn-style) design tokens matching mockups (dark theme, indigo accents), feature-based folder layout, shared layout primitives, and API client wiring to the gateway. Move the current \`App.tsx\` demo harness behind a dev-only route (e.g. \`/dev/demo\`) so new routes can grow without blocking slice work." \
"- [ ] \`npm run lint\` and \`npm run build\` pass with the new structure.
- [ ] Routes exist for public, auth, and authenticated shell placeholders.
- [ ] Design tokens and base components match \`docs/product-design/mockups/app-shell.png\` tone.
- [ ] Existing demo flows remain reachable at a dev route for regression until retired."

slice "Slice: Auth UI And Protected App Routes" "story,ready-for-agent,frontend,education,security" "#34 — Bootstrap Production Frontend Foundation. Implement together with **#30** (backend auth principal)." \
"Sign-in and session UX per \`sign-in-onboarding.png\`: register/login (or login-only for MVP), token refresh, logout, and protected route guards. Replace demo forms that accept raw user UUIDs with the authenticated principal from auth-service. Unauthenticated users redirect to sign-in; authenticated users land on Study Server home." \
"- [ ] Sign-in screen matches mockup layout and passes basic accessibility checks.
- [ ] Protected routes block unauthenticated access.
- [ ] Session refresh and logout work through the gateway.
- [ ] No production route requires manually typing \`ownerUserId\` / \`learnerUserId\`.
- [ ] Frontend E2E or smoke test covers sign-in → protected shell."

slice "Slice: Study Server App Shell And Navigation" "story,ready-for-agent,frontend,education" "#35 — Auth UI And Protected App Routes." \
"Four-column Study Server shell per \`app-shell.png\`: server switcher, channel list with **My courses** filtered by enrollment/role (\`visibility-and-social-model.md\`), conversation column placeholder, and context column placeholder. Top bar links for Friends (routes to stub until **#31**) and Instructor Dashboard." \
"- [ ] Enrolled learners see only their courses in the sidebar, not the full server catalog.
- [ ] Server switcher and channel selection update the main route.
- [ ] Layout is responsive for desktop-first web (mockup target).
- [ ] Browser demo: owner and learner accounts see different sidebars."

slice "Slice: Bootstrap Realtime Service And Live Course Channel Chat" "story,ready-for-agent,frontend,education,realtime,backend" "#36 — Study Server App Shell And Navigation." \
"Implement \`realtime-service\`: authenticated WebSocket connect, channel subscribe/unsubscribe, message fan-out, reconnect/resubscribe. Render a live message timeline in Study Server and Course text channels with optimistic send, loading states, and error recovery. Replaces form-based message demo for channel chat." \
"- [ ] \`realtime-service\` boots in Docker Compose and passes smoke tests.
- [ ] Learner can post a text message in \`#general\` or \`#questions\` and see it without refresh.
- [ ] Reconnect restores subscriptions and reconciles missed events (best-effort MVP).
- [ ] Unauthorized channel subscription is rejected.
- [ ] Gateway routes WebSocket traffic correctly."

slice "Slice: Production #questions UX With AI Context Panel" "story,ready-for-agent,frontend,education,ai-agent" "#37 — Bootstrap Realtime Service And Live Course Channel Chat." \
"\`#questions\` experience per \`ai-support-question.png\`: Support Question as channel messages, **Ask AI** action, grounded answer with citation cards in the context panel, low-confidence handoff affordance to TA Queue. AI Study Assistant install/grant summary visible in context panel (uses #18 API)." \
"- [ ] Learner posts a Support Question in the live timeline.
- [ ] AI answer renders with resource citation cards in the right panel.
- [ ] HTTP 429 quota exhaustion shows clear UI copy (from #24).
- [ ] Low-confidence path surfaces **Add to TA Queue** when applicable.
- [ ] Browser demo matches mockup flow end-to-end."

slice "Slice: Production Course Resources Panel" "story,ready-for-agent,frontend,education" "#36 — Study Server App Shell And Navigation." \
"\`#resources\` panel per \`course-resources.png\`: list/upload/download Course Resources, AI-approved badges, enrollment-scoped visibility. Instructor upload flow uses media-service; learners see permitted resources only." \
"- [ ] Instructor uploads a resource from the resources panel.
- [ ] AI-approved badge displays for grounded resources.
- [ ] Learner without enrollment cannot access private resources in UI.
- [ ] Layout matches mockup information hierarchy."

slice "Slice: Production Support Operations UI" "story,ready-for-agent,frontend,education" "#36 — Study Server App Shell And Navigation." \
"Dedicated screens for TA Queue (\`ta-queue.png\`), Office Hours (\`office-hours-voice.png\`), and FAQ approval (\`faq-approval.png\`). TAs pick up queue items; instructors schedule Office Hours and manage waitlist; instructors approve FAQ candidates from repeated Support Questions." \
"- [ ] TA can view and pick up Cohort-scoped queue items.
- [ ] Instructor can schedule Office Hours and admit learners during the window.
- [ ] Instructor can approve or edit an Approved FAQ from the FAQ approval screen.
- [ ] Browser demo covers queue → resolve and FAQ approve paths."

slice "Slice: Production Instructor Dashboard And SaaS Plan UI" "story,ready-for-agent,frontend,education,analytics,billing" "#36 — Study Server App Shell And Navigation." \
"Instructor Dashboard per \`instructor-dashboard.png\` and SaaS Plan panel per \`saas-billing.png\`: unanswered questions, queue load, AI usage vs plan limit, plan tier upgrade for Study Server Owner. Uses analytics-service and community-service saas-plan APIs from #23/#24." \
"- [ ] Dashboard widgets render live data from backend aggregates.
- [ ] Owner can change SaaS Plan tier with updated quota display.
- [ ] Unauthorized users cannot open the dashboard.
- [ ] Browser demo: instructor sees ops metrics; owner upgrades plan."

slice "Slice: Production Onboarding And Enrollment Flows" "story,ready-for-agent,frontend,education" "#35 — Auth UI And Protected App Routes." \
"Flows per \`create-study-server.png\`, \`study-server-home.png\`, and \`cohort-enrollment.png\`: owner creates Study Server, server home with course cards, instructor enrolls learners and assigns TAs with channel access preview." \
"- [ ] Owner completes create Study Server wizard and lands in server home.
- [ ] Server home lists courses for permitted roles.
- [ ] Instructor enrolls a learner in a Cohort from the enrollment UI.
- [ ] Browser demo: new server → course → enroll learner → learner sees My courses."

slice "Slice: Global Search UI And Search Service Bootstrap" "story,ready-for-agent,frontend,education,backend" "#36 — Study Server App Shell And Navigation." \
"Global search overlay per \`global-search.png\`. Bootstrap \`search-service\` to index Course Resources and Approved FAQs (messages optional MVP). Results respect enrollment and role scope." \
"- [ ] \`search-service\` boots and indexes at least resources + FAQs.
- [ ] Search UI opens from shell shortcut; results are enrollment-scoped.
- [ ] Unauthorized content does not appear in results.
- [ ] Smoke test covers query → permitted result."

slice "Slice: Channel Summary UI For Course Channels" "story,ready-for-agent,frontend,education,ai-agent" "#38 — Production #questions UX With AI Context Panel." \
"Channel Summary view per \`channel-summary.png\`: instructor generates or views a weekly digest of \`#questions\` activity — top topics, follow-ups, export. Backend summary generation can start as analytics/agent read model MVP." \
"- [ ] Instructor can generate a summary for a Course Channel time window.
- [ ] Summary displays top topics and suggested follow-ups.
- [ ] Unauthorized users cannot view summaries.
- [ ] UI matches mockup layout."

slice "Slice: Public Marketing Landing Page" "story,ready-for-agent,frontend,education" "#35 — Auth UI And Protected App Routes." \
"Public marketing page per \`landing-page.png\`: hero, feature bands, pricing teaser, CTA to sign in or create Study Server. Static or lightly dynamic; no auth required." \
"- [ ] \`/\` renders landing page for unauthenticated visitors.
- [ ] CTAs route to sign-in and onboarding.
- [ ] Page is responsive and passes basic accessibility checks.
- [ ] \`npm run build\` includes static assets."

echo "Done."
