# Local embeddings for Chanter (#95)

Agent-service stores chunk vectors for retrieval. Two providers are supported:

| Provider | When to use | Config |
|----------|-------------|--------|
| `hashing` (default) | CI, product demo without models | `CHANTER_EMBEDDINGS_PROVIDER=hashing` |
| `ollama` | Real local embeddings | see below |

## Default hashing embedder

No external process required. Vectors are a deterministic bag-of-words hash into `CHANTER_EMBEDDINGS_DIMENSIONS` (default **384**). Good enough for grant-scoped ranking smoke tests; not a neural model.

## Ollama

1. Install [Ollama](https://ollama.com/) and start the daemon.
2. Pull an embedding model:

```bash
ollama pull nomic-embed-text
```

3. Point agent-service at it (restart after changing env):

```bash
export CHANTER_EMBEDDINGS_PROVIDER=ollama
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_EMBED_MODEL=nomic-embed-text
export CHANTER_EMBEDDINGS_DIMENSIONS=768
```

4. Re-ingest or backfill:

```bash
curl -X POST "http://localhost:8085/api/v1/internal/resource-chunks/<resourceId>/embed" \
  -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN"
```

Uploading a new AI-approved `.txt`/`.md` resource already embeds automatically via media → agent ingest (#94/#95).

## Retrieve (internal)

```bash
curl -X POST "http://localhost:8085/api/v1/internal/resource-chunks/retrieve" \
  -H "Content-Type: application/json" \
  -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN" \
  -d '{"query":"How do I submit homework?","grantedResourceIds":["<resource-uuid>"],"topK":5}'
```

Grant filtering is mandatory: only chunks whose `resourceId` is in `grantedResourceIds` are considered.
