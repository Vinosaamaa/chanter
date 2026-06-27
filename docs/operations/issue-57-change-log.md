# Issue #57 — Global Search UI And Search Service Bootstrap

## Summary

Bootstrapped `search-service` with a denormalized Postgres index for course resources and approved FAQs, plus a production global search overlay in the app shell (⌘K / Ctrl+K).

## Backend

- New module `backend/search-service` (port `8088`, DB `chanter_search`)
- `POST .../search/reindex` pulls visible courses from community navigation, then catalogs from media + message services
- `GET .../search?q=` filters hits to the viewer's enrolled/visible courses
- Gateway route + `make backend-search`
- Smoke test: `GlobalSearchSmokeTest` (reindex → query → unauthorized learner sees empty)

## Frontend

- `frontend/src/features/global-search/` — API client, overlay, keyboard shortcut hook
- App shell top bar **Search** button; overlay debounces queries and links hits to `#resources` or FAQ approval

## Manual test

1. Start gateway, community, media, message, search services (+ Postgres with `chanter_search`)
2. Sign in as owner → open a Study Server → **Search** (or ⌘K)
3. **Refresh index** → search for a resource title or FAQ question
4. Sign in as a non-enrolled learner on another account → same query returns no unauthorized hits

## Deferred

- Message indexing
- Event-driven incremental indexing (Kafka)
- OpenSearch migration

## Verification

```bash
cd backend && mvn -B -pl search-service -am test
cd frontend && npm test && npm run lint && npm run build
```
