# Issue #95 — Embedding pipeline and vector retrieval store

## Summary

Embed `#94` resource chunks on ingest (and via on-demand backfill), store vectors in `resource_chunk_embeddings`, and retrieve top-k ranked passages scoped to granted resource IDs.

## Changes

### agent-service
- Flyway `V4__create_resource_chunk_embedding_tables.sql`
- `HashingEmbeddingClient` (default) — deterministic local vectors for CI / product-without-Ollama
- `OllamaEmbeddingClient` — optional (`chanter.embeddings.provider=ollama`)
- `EmbeddingPipelineService` — embed on ingest + `POST .../embed` backfill
- `VectorRetrievalService` — cosine top-k over granted resources only
- Internal APIs:
  - `POST /api/v1/internal/resource-chunks/{resourceId}/embed`
  - `POST /api/v1/internal/resource-chunks/retrieve`

### Local Ollama (optional)

```bash
# Install Ollama, then:
ollama pull nomic-embed-text

# In .env / process env:
export CHANTER_EMBEDDINGS_PROVIDER=ollama
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_EMBED_MODEL=nomic-embed-text
export CHANTER_EMBEDDINGS_DIMENSIONS=768
```

Default remains `hashing` so `make product-up` works without Ollama.

## Out of scope
- Replacing `KeywordGroundingEngine` (#96)
- LLM generation (#97+)

## Verify

```bash
cd backend && mvn -pl agent-service -am test
```
