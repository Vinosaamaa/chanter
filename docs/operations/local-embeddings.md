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
  -d '{"query":"How do I submit homework?","courseId":"<course-uuid>","viewerUserId":"<viewer-uuid>","grantedResourceIds":["<resource-uuid>"],"topK":5}'
```

Grant filtering is mandatory. The actual course and viewer are required; requests omitting them return HTTP 400. The agent intersects supplied resource grants with the live media catalog: the resource must be AVAILABLE, AI-approved, in that course and readable by that viewer. Missing metadata, revoked access and media failures never fall back to stale chunks. Update internal callers together with the epoch 4 release; the old request shape cannot safely authorize retrieval.

Terminal resource deletion permanently retires its index identity. Re-ingestion and backfill return HTTP 409 for that ID. Legacy migration uses the authenticated nonterminal purge route described in [private resource operations](private-course-resources.md); purge cannot revive a terminally deleted resource.
