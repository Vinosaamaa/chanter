# Issue #93 — change log

## Summary

Study Server management: multi-server card picker at `/app`, owner delete with confirm dialog, empty state with create CTA, and `DELETE /api/v1/study-servers/{id}` with authorization tests.

## Acceptance criteria

- [x] Multi-server landing/picker aligned with `study-server-home.png` (cards in main area; rail kept for quick switch + create).
- [x] Owner delete with confirm dialog; `DELETE` API wired; `StudyServerDeletionSmokeTest` for owner / learner / stranger.
- [x] Empty state + create CTA when user has zero servers (in-shell at `/app`).
- [x] `make product-cleanup-demo-servers` unchanged (dev psql script).
- [x] Browser check documented below.

## API

- `GET /api/v1/study-servers` — extended with `owner`, `courseCount`, `memberCount`.
- `DELETE /api/v1/study-servers/{id}` — owner only (204); enrolled learner/stranger → 403.

## Tests

```bash
cd backend && mvn -pl community-service test -Dtest=StudyServerDeletionSmokeTest,StudyServerNavigationSmokeTest
cd frontend && npm run test -- --run src/features/shell/shell-api.test.ts
cd frontend && npm run lint && npm run build
```

## Browser check (manual)

1. Sign in as demo owner (`dev-demo-owner@chanter.local` / `chanter-dev-demo`).
2. Open `/app` — card grid lists accessible servers with course/member counts.
3. Click **Open server** → per-server home; rail **⌂** returns to picker.
4. As owner, **Delete** → confirm → server removed from list (no psql).
5. Delete all servers or use fresh user → empty state with **Create Study Server** CTA.
6. Enrolled learner (non-owner) sees cards without Delete button.

## Non-goals (deferred)

- Per-server course card polish on home — **#89**.
