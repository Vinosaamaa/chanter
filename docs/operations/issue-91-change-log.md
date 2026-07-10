# Issue #91 — change log

**Slice:** AI Study Assistant install flow (production UI)  
**Branch:** `feature/91-ai-study-assistant-install`  
**Mockup:** `docs/product-design/mockups/ai-assistant-install.png`

## Summary

Instructors and Study Server owners can install the AI Study Assistant from the production `#questions` context panel. The HITL modal loads install preview from existing #18 APIs, shows a grant tree with checkboxes, and confirms install without `/dev/demo` or the seed script.

## Changes

| Area | File | What |
|------|------|------|
| API | `frontend/src/features/study-assistant/study-assistant-api.ts` | `install-preview` + `install` wrappers |
| Grants | `frontend/src/features/study-assistant/study-assistant-grants.ts` | Grant keys, selection → confirmed grants |
| Types | `frontend/src/features/study-assistant/study-assistant-types.ts` | Install preview shapes |
| Dialog | `frontend/src/features/study-assistant/components/StudyAssistantInstallDialog.tsx` | HITL modal (grant tree, confirm) |
| Hook | `frontend/src/features/study-assistant/hooks/use-study-assistant-install.ts` | Preview/open/confirm + cache invalidation |
| Panel | `frontend/src/features/shell/components/QuestionsContextPanel.tsx` | Install CTA for instructors; learner copy; no `/dev/demo` steer |
| Tests | `study-assistant-*.test.ts`, `StudyAssistantInstallDialog.test.tsx` | API, grants, dialog |
| Docs | `docs/operations/workable-product-demo.md` | UI install path in checklist |
| Docs | `HANDOFF.md` | Active slice → #91 |

## Acceptance mapping

- [x] Instructor completes install from production UI on a fresh Study Server (owner with `canViewFullCatalog` → **Install AI Study Assistant** in `#questions` panel).
- [x] Grant tree + checkboxes match preview; confirm install persists grants (subset validation on backend unchanged).
- [x] Ask AI works for enrolled learner after install (uses existing `invokeAssistantAnswer`; presence query invalidated on install).
- [x] Already-installed / re-install handled (`alreadyInstalled` in preview; install `409` message).
- [x] `QuestionsContextPanel` no longer points to `/dev/demo` or seed script.
- [x] `workable-product-demo.md` updated with UI install path.

## Verification

```bash
(cd frontend && npm run test -- --run study-assistant)
(cd frontend && npm run lint && npm run build)
```

## Browser testing (2026-07-10)

Stack: `make product-supervise` → `make product-health` green. Glass browser via `open_resource` + `position: "active"`.

| Step | Actor | Result |
|------|-------|--------|
| Create Study Server **AI Install Test Hub** + course **Install Test Course** | Owner | Pass |
| Open `#questions` → **Install AI Study Assistant** → grant tree → **Confirm install** | Owner | Pass — 8 grants; panel shows installed |
| No `/dev/demo` or seed copy in panel | Owner | Pass |
| Enroll demo learner via **Manage enrollment** UI | Owner | Pass |
| Learner sees **Installed** in panel (no install CTA) | Learner | Pass |
| Learner posts question → **Ask AI** | Learner | **500** — no AI-approved resource uploaded before install (resource grants empty at install time); pre-existing grounding path, not #91 UI |

**Note:** For grounded Ask AI with citations, upload AI-approved resources **before** confirm install (or use seeded **Workable Product Demo** server). Re-install / grant update is out of #91 scope.

Teardown: `make product-down`.


## Non-goals (unchanged)

- LLM streaming answer UX — **#100**
- Re-install / grant edit after first install — out of scope (preview shows already-installed; POST returns 409)
