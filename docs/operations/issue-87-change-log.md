# Issue #87 — change log

## Scope

Mockup gap audit for Public Launch UI polish — compare 19 MVP mockups to production routes and produce a prioritized backlog for #88–#93 with owner HITL sign-off on P0.

## Deliverables

| Artifact | Path |
|----------|------|
| Gap audit (master table + P0/P1 + sign-off checklist) | `docs/operations/public-launch-ui-gap-audit.md` |

## Method

- Mapped mockups → routes via `frontend/src/app/router.tsx` and feature modules under `frontend/src/features/`.
- Confirmed dev-only gaps: friend requests and AI install live in `/dev/demo` only.
- Confirmed no production study-server delete UI (backend DELETE may exist — to be wired in #93).

## Findings summary

- **15** screens partial vs mockups
- **2** screens missing in production (`friend-requests`, `ai-assistant-install`)
- **2** non-route surfaces (`global-search` overlay, `saas-billing` dashboard embed)

## Verification

- [x] All 19 mockup filenames listed in audit table
- [x] P0 covers friend requests, AI install, study server management, app shell density
- [x] Follow-up slices #88–#93 linked (replaces stale #69–#75 reference in issue body)
- [ ] Owner P0 sign-off on PR (HITL)
- [x] Owner P0 sign-off 2026-07-09 — P0 order #93 → #90 → #91 → #88
- [x] GitHub issues #88–#93 enriched via `scripts/sync-gap-audit-to-issues.sh`
- [x] Deferrals written to **#88, #90, #92, #100, #102, #104** — revised into **What to build / Acceptance criteria / Non-goals** (no orphan Deferred sections)

## cubic

Pending first PR review.
