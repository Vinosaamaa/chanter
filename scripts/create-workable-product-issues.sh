#!/usr/bin/env bash
set -euo pipefail
MILESTONE="Workable Product"

create_issue() {
  local title="$1"
  local labels="$2"
  local body="$3"
  gh issue create --title "$title" --label "$labels" --milestone "$MILESTONE" --body "$body"
}

EPIC_BODY="$(cat <<'EOF'
## Problem

After **Production Frontend** (#48–#59), users will have mockup-aligned screens and live **text** chat — but not a **workable local product**: no real voice in channels, no live Friends/DM UX, and no reliable one-command dev stack for click-through demos.

## Goals

- **Join a Voice Channel and speak/listen** (WebRTC/LiveKit) — not just presence flags (#14).
- **Talk to friends** via live DM (**#31**) and optional DM voice (**#32**).
- **One command** starts gateway, services, realtime, media/LiveKit, and frontend for local demos.
- **End-to-end checklist** proves sign-in → channel chat → friends → voice works.

## Non-goals

- Production cloud deployment or multi-region scale.
- Live Class video teaching rooms.
- Course commerce storefront.

## Vertical Slices

- [ ] **#30** Wire Auth Service Principal (from Education MVP — pair with #49)
- [ ] Voice Channel WebRTC + LiveKit local stack (new)
- [ ] One-command local product stack (new)
- [ ] **#31** Friends Hub + live DM
- [ ] **#32** DM voice call
- [ ] Workable product E2E demo path (new)

## Architecture Impact

- LiveKit or equivalent in `infra/docker-compose.yml`; media/signaling through gateway + `realtime-service`.
- Reuse Office Hours (#22) and Voice Channel (#14) permission models for media tokens.
- Reference: `docs/issues/workable-product-issue-breakdown.md`, `docs/architecture/social-hub-and-dm-voice.md`.
EOF
)"

echo "Creating workable product epic..."
EPIC_URL=$(create_issue "Epic: Workable Local Product (Full Stack)" "epic,education,frontend,backend,realtime,infra,ready-for-agent" "$EPIC_BODY")
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

Epic: Workable Local Product (Full Stack)

## What to build

$build

## Acceptance criteria

$criteria

## Blocked by

$blocked

## References

- \`docs/issues/workable-product-issue-breakdown.md\`
- \`docs/architecture/social-hub-and-dm-voice.md\`
- Production Frontend milestone: **#51** (realtime text chat) and **#50** (app shell)
EOF
)"
  create_issue "$title" "$labels" "$body"
}

slice "Slice: Voice Channel WebRTC And LiveKit Local Stack" "story,ready-for-agent,frontend,backend,realtime,infra" "#51 — Bootstrap Realtime Service And Live Course Channel Chat." \
"Add WebRTC audio for Study Server Voice Channels and Office Hours using LiveKit (or equivalent) in local Docker Compose. Users who join \`> study-room\` or an active Office Hours session receive a short-lived media token, connect to the SFU, and can speak/hear. Signaling flows through \`realtime-service\`; permission checks reuse community-service voice/office-hours rules (#14, #22)." \
"- [ ] LiveKit (or chosen SFU) runs in local Compose with documented ports.
- [ ] Member can join a Voice Channel and exchange audio with another member in the browser.
- [ ] Non-members and users outside Office Hours window are denied media tokens.
- [ ] Office Hours voice reuses the same media plane where applicable.
- [ ] Smoke or integration test covers token issue + join (headless or manual checklist)."

slice "Slice: One-Command Local Product Stack" "story,ready-for-agent,infra,ops,frontend,backend" "#48 — Bootstrap Production Frontend Foundation (can overlap)." \
"Document and automate a **single entrypoint** (e.g. \`make product-up\` or \`docker compose --profile product up\`) that starts PostgreSQL, Redis, broker, MinIO, gateway, all required microservices, \`realtime-service\`, LiveKit, and the production frontend dev server with correct service URL env vars. No manual per-service ports or forgotten \`COMMUNITY_SERVICE_URL\` fixes." \
"- [ ] One documented command brings up a workable local stack.
- [ ] Frontend opens at a stable URL and reaches the gateway.
- [ ] README / HANDOFF lists the happy-path ports and env defaults.
- [ ] CI or script verifies core health endpoints respond after startup."

slice "Slice: Workable Product End-To-End Demo Path" "story,ready-for-agent,frontend,education,ops" "#31, Voice Channel WebRTC slice, and One-Command Local Product Stack." \
"Capstone verification: scripted or documented flow where two local users sign in, chat live in a Course Channel, become friends and DM in real time, join a Voice Channel with audio, and (if #32 merged) place a DM voice call. Capture as \`docs/operations/workable-product-demo.md\` with screenshots or checklist." \
"- [ ] Demo doc lists exact steps from cold start to voice join.
- [ ] All steps pass on a clean clone with the one-command stack.
- [ ] Known gaps are explicitly listed if any step remains manual.
- [ ] Linked from README as the definition of \"workable local product\"."

echo "Done."
