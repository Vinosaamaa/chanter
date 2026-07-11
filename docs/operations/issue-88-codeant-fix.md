# Issue #88 — CodeAnt fix log

## Pass 1 (PR #112 — initial review)

| Finding | File | Fix |
|---------|------|-----|
| Extract repeated icon-only top bar buttons | `AppTopBar.tsx` | Shared `HeaderIconButton`; reused in `ChannelHeader.tsx` |
| Duplicate course resource fetch logic | `ResourcesContextPanel.tsx`, `CourseResourcesWidget.tsx` | Added `useCourseResourcesList` hook |
| Recompute resource summary every render | `ResourcesContextPanel.tsx` | Memoized counts/sorts in hook |
| FAQ fetch not cancelled on unmount | `ApprovedFaqsWidget.tsx` | Guard state updates after `cancelled` |
| Double network round-trip for resources | `CourseResourcesWidget.tsx` | Single `listCourseResources` call; treat 403 as no access |
| Triple scan of search results | `GlobalSearchOverlay.tsx` | Single `useMemo` pass groups resources + FAQs |
| Linear course lookup per search hit | `GlobalSearchOverlay.tsx` | Precomputed lookup maps |
| Questions panel mounts widgets immediately | `QuestionsContextPanel.tsx` | `useDeferredValue` defers widget mount |
| Resources panel stuck loading without user | `ResourcesContextPanel.tsx` | Derived empty state when unsigned |
| Stale state after unmount | `use-course-resources-list.ts` | `cancelled` guard after await |
| Widgets stuck loading without user | FAQ/resources widgets | Derived loading via `requestKey` pattern |

Verification:

```bash
cd frontend && npm run lint && npm run build && npm run test -- --run shell-routes
```

## Pass 2 (PR #112 — post-fix push)

| Item | Status |
|------|--------|
| CI `backend` / `frontend` | Green after flaky `SocialRealtimeWebSocketSmokeTest` rerun |
| CodeAnt re-review on `abd3d0d` | Awaiting second bot pass (pass 1 findings addressed) |
| cubic | Still comments via installed GitHub app (trial expired — uninstall in repo settings) |

## Pass 3 (PR #112 — cubic + CodeAnt follow-up)

| Finding | Fix |
|---------|-----|
| P0 panel resize wrong baseline width | `usePanelResize` reads width from panel ref, not handle wrapper |
| P1 TA queue stale when cohort missing | Guard widget + empty hook state when `cohortId` absent |
| P2 collapse button invisible on keyboard focus | `focus-visible:opacity-100` on edge controls |
| P2 separator not keyboard-resizable | `tabIndex={0}` + arrow-key width adjust |
| P2 study server icon low contrast on bright colors | Luminance-based foreground color |
| P2 theme flash on load | Inline bootstrap in `index.html` + `useLayoutEffect` |
| P2 invalid persisted theme strings | `normalizeTheme` on rehydrate |
| P2 sessionStorage throws | try/catch in last-active-study-server helpers |
| P2 drag listeners leak on unmount | `dragCleanupRef` + effect teardown |
| Support routes show placeholder context | Route support pages to `GeneralContextPanel` |
| Study text channels show placeholder | Added `StudyServerContextPanel` |
| Help button no-op | Disabled with “coming soon” label |
| Search footer misleading key hints | Removed unimplemented ↑↓ / ↵ hints |
| HANDOFF / startup prompt stale | Point agents to active slice #88 |

## Pass 3b (PR #112 — latest cubic sweep)

| Finding | Fix |
|---------|-----|
| HANDOFF startup prompt still #91 | Updated to #88 / `feature/88-app-shell-polish` |
| pointercancel leaves resize active | Listen for `pointercancel` in `use-panel-resize` |
| Unknown study channel → general | Verify channel exists; placeholder when missing |

## Pass 4 (PR #112 — thread cleanup + cubic P2/P3)

| Finding | Fix |
|---------|-----|
| AppTopBar repeated icon markup | `TopNavIconLink` shared component |
| FAQ fetch not aborted on unmount | `AbortController` in `ApprovedFaqsWidget` |
| Multi-cohort TA queue wrong cohort | `resolveCourseCohortId` — only when single cohort |
| Learner “View all” on TA queue | Hide link unless `queue.canManage` |
| Context placeholder a11y | `aside` with `aria-label` |
| `courseChannelGroup` duplicate | Delegate to `studyChannelGroup` |
| Study `general` description wrong | Scope-aware `channelDescription` |
| Voice panel hardcoded office-hours path | `supportOperationPath` helper |
| Double `channelBreadcrumb` lookup | Thread breadcrumb from parent panel |

## Pass 5 (PR #112 — CodeAnt full review after quality gates)

| Finding | Fix |
|---------|-----|
| Duplicate channel unavailable UI | `ChannelUnavailable` helper; single breadcrumb guard |
| Unused `courseById` in search lookup | Removed from `courseLookup` + prop type |
| Repeated navigation scans in context panel | Precompute `courseContext` + `studyChannel` once |
| Deferred vs immediate course in questions panel | Derive `resourcesChannel` from `deferredCourseContext` |
| Stale course filter on server switch | Ignore stale `courseFilter` when course id absent from server |
| Resources panel ignores `canView` on 403 | Gate success/empty UI on permission flag |
| FAQ errors shown as empty list | Explicit `error` state in `ApprovedFaqsWidget` |

## Pass 6 (PR #112 — incremental)

| Finding | Fix |
|---------|-----|
| Hardcoded #questions link in general panel | `courseChannelPath` helper |
| plan.md CodeAnt vs Cursor skills | Clarified GitHub App vs Cursor skills |
