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

1. Start gateway, community, media, message, **search-service** (`make backend-search`; requires `chanter_search` DB)
2. `/dev/demo` → **Open app shell as Owner** → **Browser Test Study Server 52**
3. **Search** (or ⌘K) opens overlay; **Refresh index** succeeds
4. Query with no indexed content shows “No matching resources or FAQs” (expected until content exists + reindex)
5. Esc closes overlay

**Browser verified (2026-06-27):** overlay, reindex, and empty-state search on `http://127.0.0.1:5173`. Initial reindex returned 502 — `HttpMediaCatalogClient` was not sending `X-User-Id` to media-service (fixed). After fix, reindex returns 200 (`Indexed 0 documents` when no resources/FAQs on server).

**Positive hit path:** approve an FAQ (requires linked support question in FAQ approval UI) or upload a resource, then **Refresh index** and search again.

## Deferred

- Message indexing
- Event-driven incremental indexing (Kafka)
- OpenSearch migration

## Verification

```bash
cd backend && mvn -B -pl search-service -am test
cd frontend && npm test && npm run lint && npm run build
```
