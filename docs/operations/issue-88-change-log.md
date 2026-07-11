# Issue #88 — change log

**Slice:** App shell, sidebar, and channel navigation polish  
**Branch:** `feature/88-app-shell-polish`  
**Mockups:** `app-shell.png`, `global-search.png`, `ai-support-question.png` (layout only)

## Summary

Polish the signed-in Study Server shell toward mockup density: context-column widgets on all major channel types, grouped sidebar channels, icon top bar, shared channel headers, and a richer global search overlay with client-side filters.

## Changes

| Area | File | What |
|------|------|------|
| Routes | `shell-routes.ts` + `shell-routes.test.ts` | Context panel kind, channel groups, breadcrumbs, descriptions, icons |
| Context rail | `context/*`, `QuestionsContextPanel.tsx` | TA queue, resources, FAQs widgets; route by channel type |
| Header | `ChannelHeader.tsx`, `ChannelConversation.tsx` | Breadcrumb + description + search affordance |
| Sidebar | `ChannelSidebarColumn.tsx` | Information / text / voice channel grouping |
| Top bar | `AppTopBar.tsx` | Chanter wordmark, icon nav, avatar chip, help button |
| Search | `GlobalSearchOverlay.tsx`, `last-active-study-server.ts` | Grouped results, course/content filters, Friends-route server recall |
| Layout | `AppShellLayout.tsx` | Remember last active Study Server for search |

## Acceptance mapping

- [x] Context column shows useful widgets on non-`#questions` channels (general, resources, voice).
- [x] `#questions` right rail adds course resources + approved FAQs sections (static layout density).
- [x] Global search: resource + FAQ results with filter UI (client-side; messages out of scope).
- [x] Channel selection hierarchy: grouped sidebar + breadcrumb headers.
- [x] Friends entry via icon top bar (badge preserved).
- [ ] Owner side-by-side sign-off vs `app-shell.png` (pending browser review).
- [x] Frontend tests for navigation helpers (`shell-routes.test.ts`).
- [x] No intentional regression to live chat path (`ChannelConversation` unchanged behavior).

## PR review (CodeAnt AI)

When opening the PR for this slice, use **CodeAnt AI** (not cubic — trial expired). Log review passes in `docs/operations/issue-88-codeant-fix.md`.

## Verification

```bash
(cd frontend && npm run test -- --run shell-routes)
(cd frontend && npm run lint && npm run build)
```

## Non-goals (unchanged)

- Resource folder hierarchy, message search, streaming AI / Mark helpful (#100), shared modal primitive (stretch for follow-up commit).
