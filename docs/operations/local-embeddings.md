# Local semantic embeddings

Agent-service uses the pinned all-MiniLM-L6-v2 ONNX model by default. `make backend-agent` and `make product-up` prepare its checksum-verified assets. Direct source runs must download them and set an absolute model directory as described in [embedding operations](embedding-versions.md).

Production supports the local `onnx` provider and explicit `api` models. Hashing is restricted to test fixtures; the former `ollama` provider setting is obsolete. Model identity, dimensions, active/candidate versions and durable rebuilding are documented in the same runbook. Current chunks are rebuilt after V13 replaces their former BYTEA coordinates. Never reset or reverse migrations to change models.

## Retrieve (internal)

```bash
curl -X POST "http://localhost:8085/api/v1/internal/resource-chunks/retrieve" \
  -H "Content-Type: application/json" \
  -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN" \
  -d '{"query":"How do I submit homework?","courseId":"<course-uuid>","viewerUserId":"<viewer-uuid>","grantedResourceIds":["<resource-uuid>"],"topK":5}'
```

Grant filtering is mandatory. The actual course and viewer are required; requests omitting them return HTTP 400. The agent intersects supplied resource grants with the live media catalog: the resource must be AVAILABLE, AI-approved, in that course and readable by that viewer. Missing metadata, revoked access and media failures never fall back to stale chunks. Update internal callers together with the epoch 4 release; the old request shape cannot safely authorize retrieval.

Terminal resource deletion permanently retires its index identity. Re-ingestion and backfill return HTTP 409 for that ID. Legacy migration uses the authenticated nonterminal purge route described in [private resource operations](private-course-resources.md); purge cannot revive a terminally deleted resource.
