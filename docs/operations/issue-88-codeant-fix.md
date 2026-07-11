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
| cubic | Skipping (trial expired) |
