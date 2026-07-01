# search-service

Global search read model for Chanter. Indexes course resources and approved FAQs per Study Server, then serves enrollment-scoped search results.

## Local run

```bash
make backend-search
```

Requires Postgres database `chanter_search` (see `infra/postgres/init/01-databases.sql`) and downstream community, media, and message services.

## API

- `GET /api/v1/study-servers/{studyServerId}/search?q=` — enrollment-scoped search
- `POST /api/v1/study-servers/{studyServerId}/search/reindex` — rebuild index from media + message catalogs

Gateway route: `/api/v1/study-servers/*/search/**` → port `8088`.
