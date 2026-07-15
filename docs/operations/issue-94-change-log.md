# Issue #94 — Resource ingestion and chunking

## Summary

When a course resource is AI-approved, media-service notifies agent-service to extract text from `.txt` / `.md` / `.markdown`, chunk with stable character offsets, and persist chunks in `chanter_agent.resource_chunks`. Re-ingest for the same `resourceId` replaces prior chunks idempotently.

## Changes

### agent-service
- Flyway `V3__create_resource_chunk_tables.sql`
- `TextResourceChunker`, `ResourceTextExtractor`, `ResourceIngestionService`
- Internal API (service-token gated):
  - `POST /api/v1/internal/resource-chunks/ingest`
  - `GET /api/v1/internal/resource-chunks/{resourceId}`
  - `DELETE /api/v1/internal/resource-chunks/{resourceId}`
- Logs resource/course IDs and content SHA-256 only (no plaintext body)

### media-service
- After AI-approved upload, best-effort call to agent ingest (`HttpResourceIngestionClient`)
- Test double `TestResourceIngestionClient`

## Out of scope
- Embeddings / vector store (#95)
- Replacing keyword grounding (#96)
- PDF extraction

## Verify

```bash
cd backend && mvn -pl agent-service,media-service -am test
```
