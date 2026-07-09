#!/usr/bin/env bash
# One-shot publisher for Public Launch epics and stories. Safe to re-run only on empty milestone.
set -euo pipefail

REPO="Vinosaamaa/chanter"
MILESTONE_TITLE="Public Launch"

create_issue() {
  local title="$1"
  local body="$2"
  shift 2
  gh issue create --repo "$REPO" --title "$title" --body "$body" "$@"
}

MILESTONE_NUM=$(gh api repos/$REPO/milestones --jq ".[] | select(.title==\"$MILESTONE_TITLE\") | .number" | head -1)
if [[ -z "$MILESTONE_NUM" ]]; then
  MILESTONE_NUM=$(gh api repos/$REPO/milestones -f title="$MILESTONE_TITLE" -f description="UI polish, real AI, and launch readiness after Workable Product" --jq .number)
  echo "Created milestone #$MILESTONE_NUM"
fi

MILESTONE_FLAG=(--milestone "$MILESTONE_TITLE")

EXISTING_ISSUE_COUNT=$(gh issue list --repo "$REPO" --milestone "$MILESTONE_TITLE" --state all --limit 1 --json number --jq 'length')
if [[ "$EXISTING_ISSUE_COUNT" != "0" ]]; then
  echo "Public Launch milestone already has $EXISTING_ISSUE_COUNT issue(s); refusing to create duplicates." >&2
  echo "See docs/issues/public-launch-issue-breakdown.md" >&2
  exit 1
fi

echo "Creating parent epic..."
EPIC_PUBLIC=$(create_issue \
  "Epic: Public Launch" \
  "$(cat <<'EOF'
## Problem

Workable Product (#60–#63) proves the full stack locally, but the product is not ready for strangers on the internet: UI still diverges from MVP mockups, AI answers use keyword matching only (no LLM/RAG), and there is no staging deployment or production auth.

## Goals

- **Phase 1:** Mockup-faithful UI polish (friend requests, AI install, study server management, instructor/support panels).
- **Phase 2:** Real AI Study Assistant — embeddings, vector retrieval, LLM orchestration, optional MCP tools — with citations, quotas, and audit.
- **Phase 3:** Launch readiness — reliable local stack, staging + HTTPS, production auth, E2E tests, public beta checklist.

## Non-goals

- Course commerce / storefront (`course-storefront.png`) — later phase.
- Marketplace agents and billing beyond existing SaaS quota UI.
- Mobile apps.

## Vertical Slices

Child epics and stories are tracked in `docs/issues/public-launch-issue-breakdown.md`.

## Architecture Impact

- UI: production frontend only; no new backend services in Phase 1.
- AI: extend `agent-service` and/or bootstrap `agent-runtime-service` per `plan.md`; vector store; provider adapters; MCP as optional tool surface.
- Launch: infra/staging, gateway TLS, auth-service email flows, Playwright in CI.

## References

- `docs/issues/public-launch-issue-breakdown.md`
- `docs/product-design/mockups/`
- `docs/operations/ai-study-assistant.md`
EOF
)" \
  --label epic --label ops "${MILESTONE_FLAG[@]}")
echo "Epic Public Launch: $EPIC_PUBLIC"

EPIC_UI=$(create_issue \
  "Epic: MVP UI Polish & Mockup Fidelity" \
  "$(cat <<EOF
## Parent

$EPIC_PUBLIC

## Problem

Production Frontend (#48–#59) and Workable Product (#31–#32) delivered functional screens, but local demo feedback shows visible gaps vs the 19 MVP mockups: layout polish, missing friend-request inbox, no production AI install UI, no study server delete, and uneven support/instructor panels.

## Goals

- Close the highest-impact mockup gaps for onboarding, app shell, social, AI install, and instructor workflows.
- Produce a prioritized gap audit so agents implement polish in mockup order.

## Non-goals

- Rebuilding the design system from scratch (#48 stands).
- Course storefront.

## Vertical Slices

See child stories in \`docs/issues/public-launch-issue-breakdown.md\` (Phase 1 UI slices).

## Architecture Impact

Frontend-only slices touching \`frontend/src/features/\`. No schema changes unless study server delete requires new API (prefer existing DELETE if present).
EOF
)" \
  --label epic --label frontend "${MILESTONE_FLAG[@]}")
echo "Epic UI: $EPIC_UI"

EPIC_AI=$(create_issue \
  "Epic: Real AI Study Assistant & Agent Runtime" \
  "$(cat <<EOF
## Parent

$EPIC_PUBLIC

## Problem

Ask AI today uses \`KeywordGroundingEngine\` — no LLM, no embeddings. This blocks resume-grade learning (orchestration, RAG, MCP) and real instructor value.

## Goals

- Ingest and chunk AI-approved course resources.
- Embed and retrieve relevant passages (vector store).
- Orchestrate LLM calls with provider adapters (Ollama local, OpenAI-compatible hosted).
- Optional MCP tool bridge for grounded tools.
- Stream answers in UI with citations, quotas, and audit trail.

## Non-goals

- General-purpose chatbot outside Study Server grants.
- Training custom models.

## Vertical Slices

See child stories in \`docs/issues/public-launch-issue-breakdown.md\` Phase 2.

## Architecture Impact

\`agent-service\`, new or extended runtime module per \`plan.md\`, vector store (pgvector/Redis), provider config in \`.env\`, gateway routes unchanged where possible.
EOF
)" \
  --label epic --label ai-agent --label backend "${MILESTONE_FLAG[@]}")
echo "Epic AI: $EPIC_AI"

EPIC_LAUNCH=$(create_issue \
  "Epic: Launch Readiness & Reliability" \
  "$(cat <<EOF
## Parent

$EPIC_PUBLIC

## Problem

Local demo is fragile (realtime/docker split, seed edge cases) and there is no path for public users: no staging URL, no email verify/reset, no automated E2E.

## Goals

- Stabilize \`make product-up\` / demo seed for repeatable local QA.
- Deploy staging with HTTPS.
- Production auth flows.
- Playwright E2E for critical paths.
- Public beta launch checklist.

## Non-goals

- Multi-region production.
- SOC2 / enterprise compliance.

## Vertical Slices

See Phase 3 in \`docs/issues/public-launch-issue-breakdown.md\`.

## Architecture Impact

\`scripts/product/\`, infra/staging, auth-service email, CI Playwright, ops docs.
EOF
)" \
  --label epic --label infra --label ops "${MILESTONE_FLAG[@]}")
echo "Epic Launch: $EPIC_LAUNCH"

story() {
  local title="$1"
  local body="$2"
  shift 2
  local url
  url=$(create_issue "$title" "$body" --label story --label ready-for-agent "$@")
  echo "  -> $url" >&2
  echo "$url" | sed -n 's|.*/\([0-9][0-9]*\)$|\1|p'
}

echo "Creating Phase 0 reliability..."
ISSUE_83=$(story \
  "Slice: Product stack reliability hotfixes" \
  "$(cat <<EOF
## Parent

$EPIC_LAUNCH

## What to build

Land local demo reliability fixes: realtime-service on host (not Docker) so channel subscribe and DM fan-out reach community/message on localhost; \`java -jar\` process supervision in \`make product-up\`; seed script idempotency and health gate; agent-service \`X-User-Id\` headers; LiveKit key format; duplicate study server cleanup script.

## Acceptance criteria

- [ ] \`make product-down && make product-up && make product-health && make product-demo-seed\` succeeds twice in a row.
- [ ] #announcements subscribe works (no \"Realtime request failed\" banner).
- [ ] Friends Hub DM send/receive works with both users on /app/friends.
- [ ] \`make product-cleanup-demo-servers\` removes duplicate demo Study Servers.
- [ ] Change log in \`docs/operations/issue-83-change-log.md\`.

## Blocked by

None — can start immediately.
EOF
)" --label backend --label infra --label realtime "${MILESTONE_FLAG[@]}")

echo "Creating Phase 1 UI stories..."
ISSUE_68=$(story \
  "Slice: Mockup gap audit and UI polish backlog" \
  "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Walk all 19 screens in \`docs/product-design/mockups/\` against the production app at \`localhost:5173\`. Produce a prioritized gap list (layout, typography, missing controls, empty states) mapped to implementation tickets. Owner signs off on P0 vs P1 before agents polish.

## Acceptance criteria

- [ ] Gap document in \`docs/operations/public-launch-ui-gap-audit.md\` with mockup → route → priority.
- [ ] P0 list covers friend requests, AI install, study server list/delete, app shell density.
- [ ] Linked follow-up issues or explicit scope for #69–#75.

## Blocked by

None — can start immediately (HITL: requires human review of priorities).
EOF
)" --label frontend --label docs "${MILESTONE_FLAG[@]}")

ISSUE_69=$(story \
  "Slice: App shell, sidebar, and channel navigation polish" \
    "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Align the signed-in Study Server shell with \`app-shell.png\`: server switcher, My courses sidebar, channel grouping, active states, mobile collapse, and connection badges. Preserve live realtime from #51.

## Acceptance criteria

- [ ] Side-by-side review with \`app-shell.png\` — owner approves P0 layout.
- [ ] Channel selection, breadcrumbs, and Friends entry match mockup hierarchy.
- [ ] No regression on live channel chat or voice channel entry.
- [ ] Frontend tests for navigation helpers where applicable.

## Blocked by

#$ISSUE_68
EOF
)" --label frontend "${MILESTONE_FLAG[@]}")

ISSUE_70=$(story \
  "Slice: Study Server home, create server, and enrollment polish" \
  "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Polish create Study Server, server home, and cohort enrollment flows per \`create-study-server.png\`, \`study-server-home.png\`, \`cohort-enrollment.png\`.

## Acceptance criteria

- [ ] Owner can create a server and land on home with mockup-aligned cards and CTAs.
- [ ] Enrollment flow clear for instructor adding learners.
- [ ] Empty states and validation copy match product tone.
- [ ] Browser demo documented in change log.

## Blocked by

#$ISSUE_68
EOF
)" --label frontend "${MILESTONE_FLAG[@]}")

ISSUE_71=$(story \
  "Slice: Friend requests inbox and Friends Hub polish" \
  "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Production friend-request inbox per \`friend-requests.png\`: pending incoming/outgoing, accept/decline/cancel without REST-only workarounds. Polish Friends Hub layout per \`friends-hub-dm.png\` (presence, thread list, connection status).

## Acceptance criteria

- [ ] User can accept or decline friend requests from UI.
- [ ] Friends Hub matches mockup for list + DM panel layout.
- [ ] Live DM still works over social realtime (#31).
- [ ] Tests for inbox actions and hub rendering.

## Blocked by

#$ISSUE_68
EOF
)" --label frontend --label realtime "${MILESTONE_FLAG[@]}")

ISSUE_72=$(story \
  "Slice: AI Study Assistant install flow (production UI)" \
  "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Instructor-facing install flow per \`ai-assistant-install.png\`: install preview, grant checkboxes (channels, courses, cohorts, AI-approved resources), confirm install, and installed state — using existing #18 APIs (no seed script required).

## Acceptance criteria

- [ ] Instructor completes install from UI on a fresh Study Server.
- [ ] Grants match preview selections; Ask AI works for enrolled learner in #questions.
- [ ] Re-install / already-installed states handled gracefully.
- [ ] Documented in change log; demo path updated in \`workable-product-demo.md\`.

## Blocked by

#$ISSUE_68
EOF
)" --label frontend --label ai-agent "${MILESTONE_FLAG[@]}")

ISSUE_73=$(story \
  "Slice: Support operations and instructor panels polish" \
  "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Polish TA queue, office hours, FAQ approval, channel summary, and instructor dashboard panels per mockups. Improve density, tables, filters, and empty states.

## Acceptance criteria

- [ ] Each panel reviewed against its mockup (\`ta-queue.png\`, \`office-hours-voice.png\`, \`faq-approval.png\`, \`channel-summary.png\`, \`instructor-dashboard.png\`).
- [ ] Instructor can complete one full support loop in browser (queue → office hours → FAQ).
- [ ] No API contract breaks.

## Blocked by

#$ISSUE_68
EOF
)" --label frontend "${MILESTONE_FLAG[@]}")

ISSUE_74=$(story \
  "Slice: Study Server management (list, delete, empty states)" \
  "$(cat <<EOF
## Parent

$EPIC_UI

## What to build

Study Server list shows only servers the user owns or can access; owner can delete a server they own (with confirm dialog). Fixes duplicate-server clutter from repeated local seeds without requiring psql.

## Acceptance criteria

- [ ] Delete Study Server API + UI (owner-only, cascade documented).
- [ ] Server list empty state and create CTA per mockups.
- [ ] \`product-cleanup-demo-servers.sh\` remains for dev; UI delete works for normal users.
- [ ] Integration test for delete authorization.

## Blocked by

#$ISSUE_68
EOF
)" --label frontend --label backend "${MILESTONE_FLAG[@]}")

echo "Creating Phase 2 AI stories..."
ISSUE_76=$(story \
  "Slice: Resource ingestion and chunking for AI-approved materials" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

When a course resource is AI-approved, extract text from \`.txt\` / \`.md\` (and PDF if in scope), chunk with stable offsets, and store chunks for retrieval. Re-index on resource update/delete.

## Acceptance criteria

- [ ] Chunks persisted with resource id, course id, and text offsets.
- [ ] Re-upload replaces prior chunks idempotently.
- [ ] Unit tests for chunking edge cases (short files, unicode).
- [ ] No plaintext secrets in logs.

## Blocked by

None — can start immediately.
EOF
)" --label ai-agent --label backend "${MILESTONE_FLAG[@]}")

ISSUE_77=$(story \
  "Slice: Embedding pipeline and vector retrieval store" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

Embed resource chunks (local model or API), store vectors, and retrieve top-k passages for a support question scoped to install grants.

## Acceptance criteria

- [ ] Embedding job runs on ingest and on demand backfill.
- [ ] Retrieval returns ranked chunks with scores; respects course/resource grants.
- [ ] Local dev documented (e.g. Ollama embeddings or small local model).
- [ ] Integration test: ingest → embed → retrieve.

## Blocked by

#$ISSUE_76
EOF
)" --label ai-agent --label backend "${MILESTONE_FLAG[@]}")

ISSUE_78=$(story \
  "Slice: RAG grounding engine (replace keyword-only)" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

Implement \`GroundingEngine\` (or successor) that uses vector retrieval + approved FAQs, replacing \`KeywordGroundingEngine\` for Ask AI. Preserve HIGH/LOW confidence, citations, and TA handoff.

## Acceptance criteria

- [ ] Ask AI uses retrieved chunks; citations point to resource titles/offsets.
- [ ] Low confidence still routes to TA queue when retrieval score is weak.
- [ ] Feature flag or config to fall back to keyword engine for dev.
- [ ] Tests with fixture corpus.

## Blocked by

#$ISSUE_77
EOF
)" --label ai-agent --label backend "${MILESTONE_FLAG[@]}")

ISSUE_79=$(story \
  "Slice: LLM provider adapters (Ollama local + OpenAI-compatible)" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

Provider abstraction for chat completions: Ollama for local dev, OpenAI-compatible HTTP for staging/production. Config via \`.env\` (model, base URL, API key). No provider calls without explicit enable flag.

## Acceptance criteria

- [ ] \`OLLAMA_BASE_URL\` / \`OPENAI_API_KEY\` documented in \`.env.example\`.
- [ ] Health check or smoke script verifies provider reachability.
- [ ] Errors map to user-safe messages; quotas still enforced (#24).
- [ ] Unit tests with mocked HTTP.

## Blocked by

None — can start in parallel with ingestion slice (merge before agent runtime orchestration).
EOF
)" --label ai-agent --label backend "${MILESTONE_FLAG[@]}")

ISSUE_80=$(story \
  "Slice: Agent runtime orchestration (prompt, streaming, quotas)" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

Orchestration layer: assemble system prompt from retrieved chunks + policies, call LLM adapter, stream tokens to client, record usage for SaaS quota and audit. Wire into existing assistant-answer endpoint.

## Acceptance criteria

- [ ] End-to-end Ask AI uses LLM when enabled; citations appended from retrieval.
- [ ] Streaming response path (SSE or WebSocket) documented.
- [ ] Quota exhaustion returns 429 with UI copy unchanged.
- [ ] Audit log entry per answer (user, question, sources, model).

## Blocked by

#$ISSUE_78, #$ISSUE_79
EOF
)" --label ai-agent --label backend "${MILESTONE_FLAG[@]}")

ISSUE_81=$(story \
  "Slice: MCP tool bridge for course-grounded assistant tools" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

Expose a small MCP-compatible tool surface for the Study Assistant (e.g. list granted resources, fetch chunk, search course FAQ) so orchestration can call tools safely within install grants.

## Acceptance criteria

- [ ] MCP server or embedded tool registry with grant checks.
- [ ] At least two tools demonstrated in local dev doc.
- [ ] Tools cannot access out-of-grant course data.
- [ ] README section: \"LLM orchestration + MCP\" for resume/portfolio.

## Blocked by

#$ISSUE_80
EOF
)" --label ai-agent --label backend --label architecture "${MILESTONE_FLAG[@]}")

ISSUE_82=$(story \
  "Slice: Streaming AI answer UX, citations, and audit trail" \
  "$(cat <<EOF
## Parent

$EPIC_AI

## What to build

Production #questions UI: streaming answer card, live citation chips, model/source metadata, instructor-visible audit snippet. Match \`ai-support-question.png\` with streaming affordance.

## Acceptance criteria

- [ ] Learner sees tokens stream then final citations.
- [ ] Low-confidence handoff unchanged.
- [ ] Instructor can see that AI was used (dashboard or question metadata).
- [ ] Frontend tests for streaming state machine.

## Blocked by

#$ISSUE_80
EOF
)" --label frontend --label ai-agent "${MILESTONE_FLAG[@]}")

echo "Creating Phase 3 launch stories..."
ISSUE_84=$(story \
  "Slice: Staging deployment with HTTPS" \
  "$(cat <<EOF
## Parent

$EPIC_LAUNCH

## What to build

Documented staging environment (e.g. single VM or PaaS): Docker infra, Java services or containers, frontend static build, TLS termination, env secrets, smoke URL for demos.

## Acceptance criteria

- [ ] Public staging URL serves sign-in over HTTPS.
- [ ] WebSocket and LiveKit paths work through TLS proxy.
- [ ] Deploy runbook in \`docs/operations/staging-deploy.md\`.
- [ ] No secrets committed.

## Blocked by

#$ISSUE_83
EOF
)" --label infra --label ops "${MILESTONE_FLAG[@]}")

ISSUE_85=$(story \
  "Slice: Production auth (email verification, password reset)" \
  "$(cat <<EOF
## Parent

$EPIC_LAUNCH

## What to build

Auth flows for public users: email verification on register, password reset via email link, secure session refresh. Dev demo passwords remain for local seed only.

## Acceptance criteria

- [ ] Register → verify email → sign in on staging.
- [ ] Forgot password → reset → sign in.
- [ ] Rate limits on auth endpoints.
- [ ] Documented email provider config for staging.

## Blocked by

#$ISSUE_84
EOF
)" --label backend --label security "${MILESTONE_FLAG[@]}")

ISSUE_86=$(story \
  "Slice: Playwright E2E critical paths" \
  "$(cat <<EOF
## Parent

$EPIC_LAUNCH

## What to build

Playwright suite in CI: sign-in, channel message, friend DM, Ask AI (keyword or LLM), optional voice smoke stub. Runs against local product stack or staging.

## Acceptance criteria

- [ ] \`make e2e\` or npm script documented.
- [ ] CI job on PR (may use docker-compose profile).
- [ ] Flake budget < 5% on main over a week (owner spot-check).

## Blocked by

#$ISSUE_71, #$ISSUE_72 (core UI flows)
EOF
)" --label frontend --label infra "${MILESTONE_FLAG[@]}")

ISSUE_87=$(story \
  "Slice: Public beta launch checklist and docs" \
  "$(cat <<EOF
## Parent

$EPIC_LAUNCH

## What to build

Launch checklist: security review, backup, monitoring, support email, terms/privacy placeholders, updated getting-started for public beta, known limitations list.

## Acceptance criteria

- [ ] \`docs/operations/public-beta-launch-checklist.md\` complete with sign-off sections.
- [ ] README points to staging URL and beta scope.
- [ ] HANDOFF updated for post-launch phase.

## Blocked by

#$ISSUE_84, #$ISSUE_86
EOF
)" --label ops --label docs "${MILESTONE_FLAG[@]}")

echo ""
echo "=== Created issues ==="
echo "Public Launch epic: $EPIC_PUBLIC"
echo "UI epic: $EPIC_UI"
echo "AI epic: $EPIC_AI"
echo "Launch epic: $EPIC_LAUNCH"
echo "Stories: 83=$ISSUE_83 68-74 76-82 84-87"
