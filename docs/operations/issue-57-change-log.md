# Issue #57 — Global Search UI And Search Service Bootstrap

## Summary

Bootstrapped `search-service` with a denormalized Postgres index for course resources and approved FAQs, plus a production global search overlay in the app shell (⌘K / Ctrl+K).

## Backend

- New module `backend/search-service` (port `8088`, DB `chanter_search`)
- `POST .../search/reindex` — instructor-only (`canViewFullCatalog`); pulls catalogs from media + message services
- `GET .../search?q=` — filters hits to the viewer's enrolled courses and per-document visibility
- Gateway route + `make backend-search`
- Smoke test: `GlobalSearchSmokeTest` (reindex → query → unauthorized learner sees empty)

## Frontend

- `frontend/src/features/global-search/` — API client, overlay, keyboard shortcut hook
- App shell top bar **Search** button; overlay debounces queries and links hits to `#resources` or FAQ approval

## Manual test

1. Start gateway, auth, community, media, message, **search-service** (`make backend-search`; requires `chanter_search` DB)
2. Seed searchable content (optional): `./scripts/seed-issue-57-search-demo.sh` — posts support question, approves FAQ, uploads resource, reindexes, and asserts API hits
3. `/dev/demo` → **Open app shell as Owner** → **Browser Test Study Server 52**
4. **Search** (or ⌘K) opens overlay; **Refresh index** succeeds
5. After reindex, query `homework` or `lecture` shows FAQ/resource hits; Esc closes overlay

**Browser verified (2026-06-27):** overlay, reindex, and empty-state search on `http://127.0.0.1:5173`. Initial reindex returned 502 — `HttpMediaCatalogClient` was not sending `X-User-Id` to media-service (fixed). After fix, reindex returns 200 (`Indexed 0 documents` when no resources/FAQs on server).

**Positive hit path verified (2026-07-01):**

- `scripts/seed-issue-57-search-demo.sh` on **Browser Test Study Server 52** (`f34abe27-…`): indexed 4 documents (2 FAQs + 2 resources from two runs), owner search `homework` → 2 FAQ hits, learner search `lecture` → 2 resource hits, stranger → HTTP 403 (no study-server access).
- Browser (Owner): **Refresh index** → “Indexed 4 documents”; query `homework` → two FAQ results in overlay with course title and snippet.
- Note: first search before **Refresh index** can show empty results if the index was never built for that server in this environment.

## Deferred

- Message indexing
- Event-driven incremental indexing (Kafka)
- OpenSearch migration

## Verification

```bash
(cd backend && mvn -B -pl search-service -am test)
(cd frontend && npm test && npm run lint && npm run build)
```
